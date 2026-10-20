package de.bockermann.ft8monitor.geo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallsignFlagsTest {

    private static CallsignFlags flags;

    @BeforeAll
    static void load() {
        flags = new CallsignFlags();
        flags.load();
    }

    @Test
    void resolvesCommonPrefixes() {
        assertEquals("DE", flags.countryCode("DL1ABC"));
        assertEquals("US", flags.countryCode("W1AW"));
        assertEquals("GB", flags.countryCode("G3XYZ"));
        assertEquals("JP", flags.countryCode("JA1XYZ"));
        assertEquals("ES", flags.countryCode("EA4ABC"));
    }

    @Test
    void portablePrefixWins() {
        // F/DL1ABC → operating in France.
        assertEquals("FR", flags.countryCode("F/DL1ABC"));
    }

    @Test
    void unknownIsEmpty() {
        assertEquals("", flags.countryCode(""));
        assertEquals("", flags.countryCode("123"));
    }
}
