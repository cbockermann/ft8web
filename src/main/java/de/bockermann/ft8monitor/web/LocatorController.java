package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.geo.CountryLookup;
import de.bockermann.ft8monitor.overview.Maidenhead;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolve Maidenhead locators to a position and country — the "where does this
 * come from?" lookup. {@code GET /api/locator/{grid}} for one, and
 * {@code GET /api/locators?grids=JO31,FN42,...} to annotate a feed in one call.
 */
@RestController
@RequestMapping("/api")
public class LocatorController {

    private final CountryLookup countries;

    public LocatorController(CountryLookup countries) {
        this.countries = countries;
    }

    /**
     * @param grid     the locator (4 or 6 chars).
     * @param lat      center latitude, or {@code null} if the grid is invalid.
     * @param lon      center longitude.
     * @param country  country name, or {@code null} if unresolved (e.g. at sea).
     * @param cc       ISO alpha-2 country code, or {@code ""}.
     * @param flag     flag emoji for the country, or {@code ""}.
     */
    public record LocatorInfo(String grid, Double lat, Double lon, String country, String cc, String flag) {
    }

    @GetMapping("/locator/{grid}")
    public ResponseEntity<LocatorInfo> locator(@PathVariable String grid) {
        LocatorInfo info = resolve(grid);
        return info == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(info);
    }

    @GetMapping("/locators")
    public Map<String, LocatorInfo> locators(@RequestParam String grids) {
        Map<String, LocatorInfo> out = new LinkedHashMap<>();
        for (String g : grids.split(",")) {
            String grid = g.trim();
            if (grid.isEmpty() || out.containsKey(grid)) {
                continue;
            }
            LocatorInfo info = resolve(grid);
            if (info != null) {
                out.put(grid, info);
            }
        }
        return out;
    }

    private LocatorInfo resolve(String grid) {
        double[] center = Maidenhead.center(grid);
        if (center == null) {
            return null;
        }
        double lat = center[0];
        double lon = center[1];
        CountryLookup.Match m = countries.resolve(lon, lat);
        String country = m == null ? null : m.country();
        String cc = m == null ? "" : m.cc();
        return new LocatorInfo(grid.toUpperCase(), lat, lon, country, cc, CountryLookup.flag(cc));
    }
}
