package de.bockermann.ft8monitor.pskreporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects spots for the current reporting window, keeping at most one per
 * (sender, band) — the one with the best SNR (ties: the most recent). Thread
 * safe; {@link #drain()} atomically returns and clears the window.
 */
public class SpotAggregator {

    private final Object lock = new Object();
    private Map<String, Spot> window = new HashMap<>();

    public void add(Spot spot) {
        String key = spot.call().toUpperCase() + "|" + spot.band();
        synchronized (lock) {
            Spot existing = window.get(key);
            if (existing == null || isBetter(spot, existing)) {
                window.put(key, spot);
            }
        }
    }

    /** Return the window's spots and start a fresh window. */
    public List<Spot> drain() {
        synchronized (lock) {
            List<Spot> spots = new ArrayList<>(window.values());
            window = new HashMap<>();
            return spots;
        }
    }

    public int size() {
        synchronized (lock) {
            return window.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            window = new HashMap<>();
        }
    }

    private static boolean isBetter(Spot candidate, Spot existing) {
        if (candidate.snr() != existing.snr()) {
            return candidate.snr() > existing.snr();
        }
        return candidate.timeUnix() >= existing.timeUnix();
    }
}
