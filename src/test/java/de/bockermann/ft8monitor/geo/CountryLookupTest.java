package de.bockermann.ft8monitor.geo;

import de.bockermann.ft8monitor.overview.Maidenhead;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CountryLookupTest {

    private static CountryLookup lookup;

    @BeforeAll
    static void load() {
        lookup = new CountryLookup();
        lookup.load();
    }

    private static String ccOf(String grid) {
        double[] c = Maidenhead.center(grid);
        assertNotNull(c, grid);
        CountryLookup.Match m = lookup.resolve(c[1], c[0]); // lon, lat
        assertNotNull(m, "no country for " + grid);
        return m.cc();
    }

    @Test
    void resolvesKnownGridsToCountries() {
        assertEquals("DE", ccOf("JO62")); // eastern Germany
        assertEquals("GB", ccOf("IO91")); // southern England
        assertEquals("US", ccOf("FN42")); // New England, USA
    }

    @Test
    void flagEmojiFromCountryCode() {
        assertEquals("🇩🇪", CountryLookup.flag("DE")); // 🇩🇪
        assertEquals("", CountryLookup.flag(""));
        assertEquals("", CountryLookup.flag("X"));
    }

    @Test
    void gridCenterIsCorrect() {
        double[] c = Maidenhead.center("JO62");
        assertEquals(52.5, c[0], 0.01);
        assertEquals(13.0, c[1], 0.01);
    }
}
