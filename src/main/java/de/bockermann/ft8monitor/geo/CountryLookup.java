package de.bockermann.ft8monitor.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a geographic point (lon/lat) to a country by point-in-polygon against
 * bundled Natural Earth country borders ({@code countries.json}, coordinates
 * rounded to ~1&nbsp;km). Used to annotate decodes/locators with the country and
 * its ISO code (for a flag).
 */
@Component
public class CountryLookup {

    private static final Logger log = LoggerFactory.getLogger(CountryLookup.class);

    /** One country: name, ISO alpha-2, bounding box, and its polygons (rings). */
    private record Country(String name, String cc,
                           double minLon, double minLat, double maxLon, double maxLat,
                           List<List<float[]>> polygons) {
    }

    /** Resolved country for a point. */
    public record Match(String country, String cc) {
    }

    private List<Country> countries = List.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("countries.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            List<Country> list = new ArrayList<>(root.size());
            for (JsonNode c : root) {
                list.add(parse(c));
            }
            countries = list;
            log.info("Loaded {} country borders for locator resolution", countries.size());
        } catch (Exception e) {
            log.warn("Could not load country borders; locator country lookup disabled: {}", e.getMessage());
        }
    }

    private static Country parse(JsonNode c) {
        double minLon = Double.MAX_VALUE, minLat = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        List<List<float[]>> polygons = new ArrayList<>();
        for (JsonNode poly : c.get("polys")) {
            List<float[]> rings = new ArrayList<>();
            for (JsonNode ring : poly) {
                float[] flat = new float[ring.size() * 2];
                int i = 0;
                for (JsonNode pt : ring) {
                    float lon = (float) pt.get(0).asDouble();
                    float lat = (float) pt.get(1).asDouble();
                    flat[i++] = lon;
                    flat[i++] = lat;
                    minLon = Math.min(minLon, lon);
                    minLat = Math.min(minLat, lat);
                    maxLon = Math.max(maxLon, lon);
                    maxLat = Math.max(maxLat, lat);
                }
                rings.add(flat);
            }
            polygons.add(rings);
        }
        return new Country(c.get("name").asText(), c.get("cc").asText(""),
                minLon, minLat, maxLon, maxLat, polygons);
    }

    /** The country containing the point, or {@code null} if none (e.g. open sea). */
    public Match resolve(double lon, double lat) {
        for (Country c : countries) {
            if (lon < c.minLon || lon > c.maxLon || lat < c.minLat || lat > c.maxLat) {
                continue;
            }
            if (contains(c, lon, lat)) {
                return new Match(c.name, c.cc);
            }
        }
        return null;
    }

    private static boolean contains(Country c, double lon, double lat) {
        for (List<float[]> polygon : c.polygons) {
            boolean in = false;
            for (float[] ring : polygon) {
                if (rayInside(ring, lon, lat)) {
                    in = !in; // XOR across rings handles holes (enclaves)
                }
            }
            if (in) {
                return true;
            }
        }
        return false;
    }

    private static boolean rayInside(float[] ring, double x, double y) {
        boolean in = false;
        int n = ring.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[2 * i], yi = ring[2 * i + 1];
            double xj = ring[2 * j], yj = ring[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /** Flag emoji for an ISO alpha-2 code (regional indicator letters), or "". */
    public static String flag(String cc) {
        if (cc == null || cc.length() != 2) {
            return "";
        }
        String up = cc.toUpperCase();
        if (up.charAt(0) < 'A' || up.charAt(0) > 'Z' || up.charAt(1) < 'A' || up.charAt(1) > 'Z') {
            return "";
        }
        int a = 0x1F1E6 + (up.charAt(0) - 'A');
        int b = 0x1F1E6 + (up.charAt(1) - 'A');
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }
}
