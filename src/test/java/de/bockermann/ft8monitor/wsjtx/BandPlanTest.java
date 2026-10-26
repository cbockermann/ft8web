package de.bockermann.ft8monitor.wsjtx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BandPlanTest {

    @Test
    void mapsFt8DialFrequenciesToBands() {
        assertEquals("80m", BandPlan.of(3_573_000L));
        assertEquals("40m", BandPlan.of(7_074_000L));
        assertEquals("20m", BandPlan.of(14_074_000L));
        assertEquals("10m", BandPlan.of(28_074_000L));
        assertEquals("6m", BandPlan.of(50_313_000L));
    }

    @Test
    void nullOutsideKnownBandsOrMissing() {
        assertNull(BandPlan.of(null));
        assertNull(BandPlan.of(5_000_000L)); // between 60m and 40m
    }
}
