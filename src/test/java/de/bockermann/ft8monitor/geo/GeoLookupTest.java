package de.bockermann.ft8monitor.geo;

import de.bockermann.ft8monitor.overview.Maidenhead;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeoLookupTest {

    private static GeoLookup geo;

    @BeforeAll
    static void load() {
        geo = new GeoLookup();
        geo.load();
    }

    private static GeoLookup.Place placeOf(String grid) {
        double[] c = Maidenhead.center(grid);
        assertNotNull(c, grid);
        GeoLookup.Place p = geo.resolve(c[1], c[0]); // lon, lat
        assertNotNull(p, "no place for " + grid);
        return p;
    }

    @Test
    void resolvesCountryAndRegion() {
        GeoLookup.Place de = placeOf("JO62"); // Brandenburg / Berlin area
        assertEquals("DE", de.cc());
        assertEquals("Germany", de.country());
        assertFalse(de.region().isBlank(), "expected an admin-1 region");

        assertEquals("US", placeOf("FN42").cc()); // New England
        assertEquals("GB", placeOf("IO91").cc()); // southern England
    }

    @Test
    void countryNameByCode() {
        assertEquals("Germany", geo.countryName("DE"));
    }

    @Test
    void flagEmojiFromCode() {
        assertEquals("🇩🇪", GeoLookup.flag("DE")); // 🇩🇪
        assertEquals("", GeoLookup.flag(""));
    }
}
