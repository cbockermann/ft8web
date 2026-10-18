package de.bockermann.ft8monitor.overview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Counts decoded messages per Maidenhead cell over a rolling time window, for
 * the overview activity heatmap. Activity is stored at the finest useful level —
 * the 4-character square (e.g. {@code JO62}) — and can be queried at square or
 * field ({@code JO}) resolution. Keeps per-square message timestamps up to a
 * configurable cap ({@code ft8.overview.window-cap-minutes}); callers query the
 * count within the last N minutes (≤ cap). Wall-clock based, so "last N minutes"
 * means exactly that and stale activity fades out as it ages past the window.
 */
@Component
public class GridFieldAggregator {

    private final long capSeconds;
    private final Object lock = new Object();
    private final Map<String, Deque<Long>> bySquare = new HashMap<>();

    public GridFieldAggregator(@Value("${ft8.overview.window-cap-minutes:60}") long capMinutes) {
        this.capSeconds = Math.max(1, capMinutes) * 60;
    }

    /** Record a decode's message under its 4-char square (ignored if no grid). */
    public void add(long unix, String msg) {
        String square = Maidenhead.squareOf(msg);
        if (square == null) {
            return;
        }
        synchronized (lock) {
            bySquare.computeIfAbsent(square, k -> new ArrayDeque<>()).addLast(unix);
            evict(now());
        }
    }

    /**
     * Count per cell within the last {@code minutes} (clamped to the cap), keyed
     * either by 4-char square or by 2-char field (squares summed into fields).
     */
    public Map<String, Integer> countsSince(long minutes, boolean squareLevel) {
        long window = Math.min(Math.max(1, minutes) * 60, capSeconds);
        long horizon = now() - window;
        Map<String, Integer> out = new HashMap<>();
        synchronized (lock) {
            for (Map.Entry<String, Deque<Long>> e : bySquare.entrySet()) {
                int count = 0;
                for (long t : e.getValue()) {
                    if (t >= horizon) {
                        count++;
                    }
                }
                if (count > 0) {
                    String key = squareLevel ? e.getKey() : Maidenhead.fieldOfSquare(e.getKey());
                    out.merge(key, count, Integer::sum);
                }
            }
        }
        return out;
    }

    /** Periodic sweep so idle fields drop out even without new activity. */
    @Scheduled(fixedRateString = "${ft8.overview.sweep-interval-ms:60000}")
    public void sweep() {
        synchronized (lock) {
            evict(now());
        }
    }

    /** Drop timestamps older than the cap; remove emptied squares. Caller holds the lock. */
    private void evict(long nowSeconds) {
        long horizon = nowSeconds - capSeconds;
        Iterator<Map.Entry<String, Deque<Long>>> it = bySquare.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> times = it.next().getValue();
            while (!times.isEmpty() && times.peekFirst() < horizon) {
                times.pollFirst();
            }
            if (times.isEmpty()) {
                it.remove();
            }
        }
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
