package de.bockermann.ft8monitor.overview;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GridFieldAggregatorTest {

    private static final boolean SQUARE = true;
    private static final boolean FIELD = false;

    @Test
    void countsBySquareAndField() {
        GridFieldAggregator agg = new GridFieldAggregator(60);
        long now = Instant.now().getEpochSecond();
        agg.add(now, "CQ DX AB1HL FN42");
        agg.add(now, "AB1HL DL1ABC FN31");   // same field FN, different square
        agg.add(now, "CQ DL1ABC JO31");      // JO31
        agg.add(now, "AB1HL DL1ABC R-12");   // no grid → ignored

        Map<String, Integer> squares = agg.countsSince(15, SQUARE);
        assertEquals(1, squares.get("FN42"));
        assertEquals(1, squares.get("FN31"));
        assertEquals(1, squares.get("JO31"));

        Map<String, Integer> fields = agg.countsSince(15, FIELD);
        assertEquals(2, fields.get("FN")); // FN42 + FN31 summed
        assertEquals(1, fields.get("JO"));
    }

    @Test
    void olderThanWindowNotCounted() {
        GridFieldAggregator agg = new GridFieldAggregator(60);
        long now = Instant.now().getEpochSecond();
        agg.add(now - 20 * 60, "CQ AB1HL FN42"); // 20 min ago
        agg.add(now, "CQ DL1ABC JO31");

        Map<String, Integer> last5 = agg.countsSince(5, FIELD);
        assertNull(last5.get("FN"));           // outside 5-min window
        assertEquals(1, last5.get("JO"));

        assertEquals(1, agg.countsSince(30, FIELD).get("FN")); // inside 30-min window
    }

    @Test
    void evictsBeyondCap() {
        GridFieldAggregator agg = new GridFieldAggregator(10); // 10-min cap
        long now = Instant.now().getEpochSecond();
        agg.add(now - 20 * 60, "CQ AB1HL FN42"); // older than cap → evicted on add
        agg.add(now, "CQ DL1ABC JO31");

        Map<String, Integer> counts = agg.countsSince(60, SQUARE);
        assertNull(counts.get("FN42"));
        assertEquals(1, counts.get("JO31"));
    }
}
