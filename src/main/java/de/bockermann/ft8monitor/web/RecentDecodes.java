package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.model.Decode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * A bounded, newest-first ring of the most recent decodes, so a browser that
 * (re)loads can fetch a backlog instead of starting with an empty feed. Fed by
 * the Kafka listener (including the startup backfill), served via
 * {@code GET /api/recent}.
 */
@Component
public class RecentDecodes {

    private final int capacity;
    private final Object lock = new Object();
    private final Deque<Decode> buffer = new ArrayDeque<>();

    public RecentDecodes(@Value("${ft8.recent-size:500}") int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Add a decode as the newest; drop the oldest beyond capacity. */
    public void add(Decode decode) {
        synchronized (lock) {
            buffer.addFirst(decode);
            while (buffer.size() > capacity) {
                buffer.removeLast();
            }
        }
    }

    /** The newest {@code limit} decodes, newest first. */
    public List<Decode> latest(int limit) {
        int n = Math.max(0, Math.min(limit, capacity));
        List<Decode> out = new ArrayList<>(Math.min(n, buffer.size()));
        synchronized (lock) {
            Iterator<Decode> it = buffer.iterator(); // newest first
            while (it.hasNext() && out.size() < n) {
                out.add(it.next());
            }
        }
        return out;
    }
}
