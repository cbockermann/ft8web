package de.bockermann.ft8monitor.qso;

import de.bockermann.ft8monitor.config.Ft8Properties;
import de.bockermann.ft8monitor.model.Decode;
import de.bockermann.ft8monitor.model.Qsl;
import de.bockermann.ft8monitor.qso.Callsigns.Stations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the QSO ("QSL") grouping incrementally over a rolling in-memory
 * window of recent decodes. Port of the batch algorithm in ft8mon's
 * {@code scripts/qsls.py}, adapted to run online as decodes arrive.
 *
 * <p>Directed (two-call) messages are grouped by the unordered pair of calls
 * and split into separate QSOs when consecutive messages are more than
 * {@code gapSeconds} apart. Each {@code CQ} is attached to the earliest
 * following QSO of its caller within the gap. Segmentation is recomputed on
 * demand in {@link #qslFor(String)}; per-pair message counts within the window
 * are small, so this stays cheap while keeping results faithful to the batch
 * semantics.
 *
 * <p>All mutating and reading operations are guarded by a single monitor.
 */
@Component
public class QsoGrouper {

    private final long gapSeconds;
    private final long windowSeconds;

    private final Object lock = new Object();

    /** All live decodes, by id. */
    private final Map<String, Decode> byId = new HashMap<>();
    /** Directed decodes grouped by unordered pair key. */
    private final Map<String, List<Decode>> byPair = new HashMap<>();
    /** Standalone {@code CQ} decodes, by caller. */
    private final Map<String, List<Decode>> cqsByCaller = new HashMap<>();

    /** Newest decode time seen; the eviction horizon is relative to this. */
    private long maxUnix = 0;

    public QsoGrouper(Ft8Properties props) {
        this.gapSeconds = props.gapSeconds();
        this.windowSeconds = props.windowSeconds();
    }

    /** Unordered, stable key for a pair of callsigns. */
    static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Ingest a decode into the rolling view. */
    public void add(Decode d) {
        synchronized (lock) {
            byId.put(d.id(), d);
            if (d.unix() > maxUnix) {
                maxUnix = d.unix();
            }
            Stations st = Callsigns.parse(d.msg());
            if (st instanceof Stations.Directed dir) {
                byPair.computeIfAbsent(pairKey(dir.a(), dir.b()), k -> new ArrayList<>()).add(d);
            } else if (st instanceof Stations.Cq cq) {
                cqsByCaller.computeIfAbsent(cq.caller(), k -> new ArrayList<>()).add(d);
            }
            evict();
        }
    }

    /**
     * The QSL a given decode belongs to, or {@code null} if the id is unknown
     * (e.g. already evicted).
     */
    public Qsl qslFor(String decodeId) {
        synchronized (lock) {
            Decode d = byId.get(decodeId);
            if (d == null) {
                return null;
            }
            Stations st = Callsigns.parse(d.msg());
            if (st instanceof Stations.Directed dir) {
                List<Decode> segment = segmentContaining(pairKey(dir.a(), dir.b()), decodeId);
                if (segment == null) {
                    return loneQsl(d);
                }
                return buildQsl(dir.a(), dir.b(), segment);
            }
            if (st instanceof Stations.Cq cq) {
                return qslForCq(cq.caller(), d);
            }
            return loneQsl(d);
        }
    }

    // --- QSO segmentation ---------------------------------------------------

    /** Split a pair's decodes into QSOs wherever a gap exceeds {@code gapSeconds}. */
    private List<List<Decode>> segments(String pairKey) {
        List<Decode> all = byPair.get(pairKey);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<Decode> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingLong(Decode::unix));

        List<List<Decode>> result = new ArrayList<>();
        List<Decode> current = new ArrayList<>();
        long prev = Long.MIN_VALUE;
        for (Decode d : sorted) {
            if (!current.isEmpty() && d.unix() - prev > gapSeconds) {
                result.add(current);
                current = new ArrayList<>();
            }
            current.add(d);
            prev = d.unix();
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    /** The segment of {@code pairKey} that contains the given decode id. */
    private List<Decode> segmentContaining(String pairKey, String decodeId) {
        for (List<Decode> seg : segments(pairKey)) {
            for (Decode d : seg) {
                if (d.id().equals(decodeId)) {
                    return seg;
                }
            }
        }
        return null;
    }

    /**
     * Build the QSL for one QSO segment, prepending the opening {@code CQ} (the
     * most recent CQ by either station within the gap before the segment
     * starts), if any.
     */
    private Qsl buildQsl(String a, String b, List<Decode> segment) {
        long start = segment.get(0).unix();

        Decode opener = null;
        for (String caller : new String[]{a, b}) {
            List<Decode> cqs = cqsByCaller.get(caller);
            if (cqs == null) {
                continue;
            }
            for (Decode cq : cqs) {
                if (cq.unix() <= start && start - cq.unix() <= gapSeconds) {
                    if (opener == null || cq.unix() > opener.unix()) {
                        opener = cq;
                    }
                }
            }
        }

        // De-duplicate by id and order by time.
        Map<String, Decode> ordered = new LinkedHashMap<>();
        if (opener != null) {
            ordered.put(opener.id(), opener);
        }
        for (Decode d : segment) {
            ordered.put(d.id(), d);
        }
        List<Decode> messages = new ArrayList<>(ordered.values());
        messages.sort(Comparator.comparingLong(Decode::unix));

        String lo = a.compareTo(b) <= 0 ? a : b;
        String hi = a.compareTo(b) <= 0 ? b : a;
        String id = lo + "|" + hi + "@" + messages.get(0).unix();
        return new Qsl(id, lo, hi, messages);
    }

    /** The QSL a standalone CQ belongs to: the earliest following QSO of its caller. */
    private Qsl qslForCq(String caller, Decode cqDecode) {
        List<Decode> best = null;
        String bestA = null;
        String bestB = null;
        long bestStart = Long.MAX_VALUE;

        for (Map.Entry<String, List<Decode>> e : byPair.entrySet()) {
            String[] pair = e.getKey().split("\\|", 2);
            if (!pair[0].equals(caller) && !pair[1].equals(caller)) {
                continue;
            }
            for (List<Decode> seg : segments(e.getKey())) {
                long start = seg.get(0).unix();
                if (start >= cqDecode.unix() && start - cqDecode.unix() <= gapSeconds && start < bestStart) {
                    best = seg;
                    bestStart = start;
                    bestA = pair[0];
                    bestB = pair[1];
                }
            }
        }
        if (best == null) {
            return loneQsl(cqDecode); // a CQ with no follow-up contact
        }
        return buildQsl(bestA, bestB, best);
    }

    /** A one-message QSL (lone CQ or an ungroupable message). */
    private Qsl loneQsl(Decode d) {
        Stations st = Callsigns.parse(d.msg());
        String a = st instanceof Stations.Cq cq ? cq.caller() : null;
        String id = (a != null ? a : "?") + "|@" + d.unix();
        return new Qsl(id, a, null, List.of(d));
    }

    // --- Eviction -----------------------------------------------------------

    /** Periodic sweep so idle periods still drop stale decodes. */
    @Scheduled(fixedRateString = "${ft8.eviction-interval-ms:60000}")
    public void sweep() {
        synchronized (lock) {
            evict();
        }
    }

    /** Drop decodes older than the window relative to the newest seen. Caller holds the lock. */
    private void evict() {
        if (maxUnix == 0) {
            return;
        }
        long horizon = maxUnix - windowSeconds;
        byId.values().removeIf(d -> d.unix() < horizon);
        pruneMap(byPair, horizon);
        pruneMap(cqsByCaller, horizon);
    }

    private static void pruneMap(Map<String, List<Decode>> map, long horizon) {
        Iterator<Map.Entry<String, List<Decode>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            List<Decode> list = it.next().getValue();
            list.removeIf(d -> d.unix() < horizon);
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }
}
