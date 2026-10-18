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
 * Counts decoded messages per Maidenhead field over a rolling time window, for
 * the overview activity heatmap. Keeps per-field message timestamps up to a
 * configurable cap ({@code ft8.overview.window-cap-minutes}); callers query the
 * count within the last N minutes (≤ cap). Wall-clock based, so "last N minutes"
 * means exactly that and stale activity fades out as it ages past the window.
 */
@Component
public class GridFieldAggregator {

    private final long capSeconds;
    private final Object lock = new Object();
    private final Map<String, Deque<Long>> byField = new HashMap<>();

    public GridFieldAggregator(@Value("${ft8.overview.window-cap-minutes:60}") long capMinutes) {
        this.capSeconds = Math.max(1, capMinutes) * 60;
    }

    /** Record a decode's message under its field (ignored if it carries no grid). */
    public void add(long unix, String msg) {
        String field = Maidenhead.fieldOf(msg);
        if (field == null) {
            return;
        }
        synchronized (lock) {
            byField.computeIfAbsent(field, k -> new ArrayDeque<>()).addLast(unix);
            evict(now());
        }
    }

    /** Count per field within the last {@code minutes} (clamped to the cap). */
    public Map<String, Integer> countsSince(long minutes) {
        long window = Math.min(Math.max(1, minutes) * 60, capSeconds);
        long horizon = now() - window;
        Map<String, Integer> out = new HashMap<>();
        synchronized (lock) {
            for (Map.Entry<String, Deque<Long>> e : byField.entrySet()) {
                int count = 0;
                for (long t : e.getValue()) {
                    if (t >= horizon) {
                        count++;
                    }
                }
                if (count > 0) {
                    out.put(e.getKey(), count);
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

    /** Drop timestamps older than the cap; remove emptied fields. Caller holds the lock. */
    private void evict(long nowSeconds) {
        long horizon = nowSeconds - capSeconds;
        Iterator<Map.Entry<String, Deque<Long>>> it = byField.entrySet().iterator();
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
