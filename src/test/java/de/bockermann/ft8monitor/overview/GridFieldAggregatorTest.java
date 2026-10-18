package de.bockermann.ft8monitor.overview;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GridFieldAggregatorTest {

    @Test
    void countsByFieldWithinWindow() {
        GridFieldAggregator agg = new GridFieldAggregator(60);
        long now = Instant.now().getEpochSecond();
        agg.add(now, "CQ DX AB1HL FN42");
        agg.add(now, "AB1HL DL1ABC FN31");   // FN again
        agg.add(now, "CQ DL1ABC JO31");      // JO
        agg.add(now, "AB1HL DL1ABC R-12");   // no grid → ignored

        Map<String, Integer> counts = agg.countsSince(15);
        assertEquals(2, counts.get("FN"));
        assertEquals(1, counts.get("JO"));
        assertFalse(counts.containsKey("--"));
    }

    @Test
    void olderThanWindowNotCounted() {
        GridFieldAggregator agg = new GridFieldAggregator(60);
        long now = Instant.now().getEpochSecond();
        agg.add(now - 20 * 60, "CQ AB1HL FN42"); // 20 min ago
        agg.add(now, "CQ DL1ABC JO31");

        Map<String, Integer> last5 = agg.countsSince(5);
        assertNull(last5.get("FN"));           // outside 5-min window
        assertEquals(1, last5.get("JO"));

        assertEquals(1, agg.countsSince(30).get("FN")); // inside 30-min window
    }

    @Test
    void evictsBeyondCap() {
        GridFieldAggregator agg = new GridFieldAggregator(10); // 10-min cap
        long now = Instant.now().getEpochSecond();
        agg.add(now - 20 * 60, "CQ AB1HL FN42"); // older than cap → evicted on add
        agg.add(now, "CQ DL1ABC JO31");

        // Even asking for a long window can't resurrect capped-out data.
        Map<String, Integer> counts = agg.countsSince(60);
        assertNull(counts.get("FN"));
        assertEquals(1, counts.get("JO"));
    }
}
