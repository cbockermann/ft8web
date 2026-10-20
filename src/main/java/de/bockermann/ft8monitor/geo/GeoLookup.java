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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a geographic point (lon/lat) to its country and finest region
 * (admin-1 state/province) by point-in-polygon against bundled Natural Earth
 * borders ({@code regions.json}, 10m admin-1 simplified to ~11&nbsp;km). Also
 * provides country name by ISO code and the flag emoji.
 */
@Component
public class GeoLookup {

    private static final Logger log = LoggerFactory.getLogger(GeoLookup.class);

    private record Region(String region, String country, String cc,
                          double minLon, double minLat, double maxLon, double maxLat,
                          List<List<float[]>> polygons) {
    }

    /** Resolved place for a point: country, finest region (may be empty), ISO code. */
    public record Place(String country, String region, String cc) {
    }

    private List<Region> regions = List.of();
    private final Map<String, String> countryByCc = new HashMap<>();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("regions.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            List<Region> list = new ArrayList<>(root.size());
            for (JsonNode c : root) {
                Region r = parse(c);
                list.add(r);
                if (!r.cc.isEmpty()) {
                    countryByCc.putIfAbsent(r.cc, r.country);
                }
            }
            regions = list;
            log.info("Loaded {} admin-1 regions for locator resolution", regions.size());
        } catch (Exception e) {
            log.warn("Could not load region borders; locator lookup disabled: {}", e.getMessage());
        }
    }

    private static Region parse(JsonNode c) {
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
        return new Region(c.get("name").asText(""), c.get("admin").asText(""), c.get("cc").asText(""),
                minLon, minLat, maxLon, maxLat, polygons);
    }

    /** The place containing the point, or {@code null} if none (e.g. open sea). */
    public Place resolve(double lon, double lat) {
        for (Region r : regions) {
            if (lon < r.minLon || lon > r.maxLon || lat < r.minLat || lat > r.maxLat) {
                continue;
            }
            if (contains(r, lon, lat)) {
                return new Place(r.country, r.region, r.cc);
            }
        }
        return null;
    }

    /** Country name for an ISO alpha-2 code, or empty. */
    public String countryName(String cc) {
        return cc == null ? "" : countryByCc.getOrDefault(cc.toUpperCase(), "");
    }

    private static boolean contains(Region r, double lon, double lat) {
        for (List<float[]> polygon : r.polygons) {
            boolean in = false;
            for (float[] ring : polygon) {
                if (rayInside(ring, lon, lat)) {
                    in = !in; // XOR across rings handles holes
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
