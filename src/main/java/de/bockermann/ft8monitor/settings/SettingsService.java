package de.bockermann.ft8monitor.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads and persists the station {@link Settings} as a local properties file
 * ({@code ft8.config-file}, default {@code ft8web.properties} in the working
 * directory). Read at startup; written on every update.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private static final String KEY_CALLSIGN = "callsign";
    private static final String KEY_LOCATOR = "locator";
    private static final String KEY_GRID_RESOLUTION = "grid-resolution";
    private static final String DEFAULT_GRID_RESOLUTION = "square";

    private static final String KEY_BACKFILL = "backfill-minutes";
    private static final int MAX_BACKFILL = 1440; // 24 h

    private static final String KEY_PSK_ENABLED = "psk.enabled";
    private static final String KEY_PSK_ANTENNA = "psk.antenna";
    private static final String KEY_PSK_HOST = "psk.host";
    private static final String KEY_PSK_PORT = "psk.port";
    private static final String KEY_PSK_INTERVAL = "psk.interval-seconds";

    private static final String KEY_BANDS = "bands";
    /** Hamlib mode for FT8 on an IC-7300 (Data-U). */
    private static final String DEFAULT_MODE = "PKTUSB";
    /** Standard FT8 dial frequencies for the bands the operator asked for. */
    private static final List<Settings.Band> DEFAULT_BANDS = List.of(
            new Settings.Band("80m", 3_573_000L, true, DEFAULT_MODE),
            new Settings.Band("40m", 7_074_000L, true, DEFAULT_MODE),
            new Settings.Band("20m", 14_074_000L, true, DEFAULT_MODE),
            new Settings.Band("10m", 28_074_000L, true, DEFAULT_MODE));

    private static final String DEFAULT_PSK_HOST = "report.pskreporter.info";
    private static final int DEFAULT_PSK_PORT = 4739;
    private static final int DEFAULT_PSK_INTERVAL = 300;
    private static final int MIN_PSK_INTERVAL = 120; // PSKReporter etiquette floor

    private final Path file;
    private final int defaultBackfillMinutes;
    private final Object lock = new Object();
    private final Properties props = new Properties();

    public SettingsService(@Value("${ft8.config-file:ft8web.properties}") String path,
                           @Value("${ft8.backfill-minutes:15}") int defaultBackfillMinutes) {
        this.file = Path.of(path);
        this.defaultBackfillMinutes = defaultBackfillMinutes;
        load();
    }

    private void load() {
        synchronized (lock) {
            if (!Files.exists(file)) {
                log.info("No settings file at {} yet; using empty defaults", file.toAbsolutePath());
                return;
            }
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
                log.info("Loaded settings from {}", file.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Could not read settings file {}: {}", file.toAbsolutePath(), e.getMessage());
            }
        }
    }

    public Settings get() {
        synchronized (lock) {
            Settings.Psk psk = new Settings.Psk(
                    Boolean.parseBoolean(props.getProperty(KEY_PSK_ENABLED, "false")),
                    props.getProperty(KEY_PSK_ANTENNA, ""),
                    props.getProperty(KEY_PSK_HOST, DEFAULT_PSK_HOST),
                    parseInt(props.getProperty(KEY_PSK_PORT), DEFAULT_PSK_PORT),
                    Math.max(MIN_PSK_INTERVAL, parseInt(props.getProperty(KEY_PSK_INTERVAL), DEFAULT_PSK_INTERVAL)));
            int backfill = clamp(parseInt(props.getProperty(KEY_BACKFILL), defaultBackfillMinutes),
                    0, MAX_BACKFILL);
            return new Settings(
                    props.getProperty(KEY_CALLSIGN, ""),
                    props.getProperty(KEY_LOCATOR, ""),
                    props.getProperty(KEY_GRID_RESOLUTION, DEFAULT_GRID_RESOLUTION),
                    backfill,
                    psk,
                    parseBands(props.getProperty(KEY_BANDS)));
        }
    }

    /** Normalize and persist the given settings, returning the stored result. */
    public Settings update(Settings incoming) {
        synchronized (lock) {
            props.setProperty(KEY_CALLSIGN, norm(incoming.callsign()).toUpperCase());
            props.setProperty(KEY_LOCATOR, norm(incoming.locator()).toUpperCase());
            props.setProperty(KEY_GRID_RESOLUTION, normResolution(incoming.gridResolution()));
            props.setProperty(KEY_BACKFILL,
                    Integer.toString(clamp(incoming.backfillMinutes(), 0, MAX_BACKFILL)));

            Settings.Psk psk = incoming.psk() != null ? incoming.psk()
                    : new Settings.Psk(false, "", DEFAULT_PSK_HOST, DEFAULT_PSK_PORT, DEFAULT_PSK_INTERVAL);
            props.setProperty(KEY_PSK_ENABLED, Boolean.toString(psk.enabled()));
            props.setProperty(KEY_PSK_ANTENNA, norm(psk.antenna()));
            props.setProperty(KEY_PSK_HOST, norm(psk.host()).isEmpty() ? DEFAULT_PSK_HOST : norm(psk.host()));
            props.setProperty(KEY_PSK_PORT, Integer.toString(validPort(psk.port())));
            props.setProperty(KEY_PSK_INTERVAL,
                    Integer.toString(Math.max(MIN_PSK_INTERVAL, psk.intervalSeconds())));
            props.setProperty(KEY_BANDS, formatBands(normalizeBands(incoming.bands())));
            save();
            return get();
        }
    }

    /** Keep valid bands (named, positive frequency); fall back to the defaults. */
    private static List<Settings.Band> normalizeBands(List<Settings.Band> bands) {
        if (bands == null || bands.isEmpty()) {
            return DEFAULT_BANDS;
        }
        List<Settings.Band> out = new ArrayList<>(bands.size());
        for (Settings.Band b : bands) {
            String name = b.name() == null ? "" : b.name().trim().replaceAll("[,:]", "");
            if (!name.isEmpty() && b.freqHz() > 0) {
                out.add(new Settings.Band(name, b.freqHz(), b.enabled(), mode(b.mode())));
            }
        }
        return out.isEmpty() ? DEFAULT_BANDS : out;
    }

    private static List<Settings.Band> parseBands(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BANDS;
        }
        List<Settings.Band> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String[] f = part.split(":");
            if (f.length < 3) {
                continue;
            }
            try {
                String name = f[0].trim();
                long hz = Long.parseLong(f[1].trim());
                boolean enabled = Boolean.parseBoolean(f[2].trim());
                String mode = f.length >= 4 ? mode(f[3]) : DEFAULT_MODE; // legacy rows had no mode
                if (!name.isEmpty() && hz > 0) {
                    out.add(new Settings.Band(name, hz, enabled, mode));
                }
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return out.isEmpty() ? DEFAULT_BANDS : out;
    }

    private static String formatBands(List<Settings.Band> bands) {
        StringBuilder sb = new StringBuilder();
        for (Settings.Band b : bands) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(b.name()).append(':').append(b.freqHz()).append(':').append(b.enabled())
                    .append(':').append(mode(b.mode()));
        }
        return sb.toString();
    }

    private static String mode(String m) {
        String v = m == null ? "" : m.trim().replaceAll("[,:]", "");
        return v.isEmpty() ? DEFAULT_MODE : v;
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int validPort(int port) {
        return (port >= 1 && port <= 65535) ? port : DEFAULT_PSK_PORT;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    private void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "ft8web station settings");
            }
            log.info("Saved settings to {}", file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save settings to " + file.toAbsolutePath(), e);
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    private static String normResolution(String s) {
        String v = norm(s).toLowerCase();
        return "field".equals(v) ? "field" : DEFAULT_GRID_RESOLUTION;
    }
}
