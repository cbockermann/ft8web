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

    private static final String KEY_PSK_ENABLED = "psk.enabled";
    private static final String KEY_PSK_ANTENNA = "psk.antenna";
    private static final String KEY_PSK_HOST = "psk.host";
    private static final String KEY_PSK_PORT = "psk.port";
    private static final String KEY_PSK_INTERVAL = "psk.interval-seconds";

    private static final String DEFAULT_PSK_HOST = "report.pskreporter.info";
    private static final int DEFAULT_PSK_PORT = 4739;
    private static final int DEFAULT_PSK_INTERVAL = 300;
    private static final int MIN_PSK_INTERVAL = 120; // PSKReporter etiquette floor

    private final Path file;
    private final Object lock = new Object();
    private final Properties props = new Properties();

    public SettingsService(@Value("${ft8.config-file:ft8web.properties}") String path) {
        this.file = Path.of(path);
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
            return new Settings(
                    props.getProperty(KEY_CALLSIGN, ""),
                    props.getProperty(KEY_LOCATOR, ""),
                    props.getProperty(KEY_GRID_RESOLUTION, DEFAULT_GRID_RESOLUTION),
                    psk);
        }
    }

    /** Normalize and persist the given settings, returning the stored result. */
    public Settings update(Settings incoming) {
        synchronized (lock) {
            props.setProperty(KEY_CALLSIGN, norm(incoming.callsign()).toUpperCase());
            props.setProperty(KEY_LOCATOR, norm(incoming.locator()).toUpperCase());
            props.setProperty(KEY_GRID_RESOLUTION, normResolution(incoming.gridResolution()));

            Settings.Psk psk = incoming.psk() != null ? incoming.psk()
                    : new Settings.Psk(false, "", DEFAULT_PSK_HOST, DEFAULT_PSK_PORT, DEFAULT_PSK_INTERVAL);
            props.setProperty(KEY_PSK_ENABLED, Boolean.toString(psk.enabled()));
            props.setProperty(KEY_PSK_ANTENNA, norm(psk.antenna()));
            props.setProperty(KEY_PSK_HOST, norm(psk.host()).isEmpty() ? DEFAULT_PSK_HOST : norm(psk.host()));
            props.setProperty(KEY_PSK_PORT, Integer.toString(validPort(psk.port())));
            props.setProperty(KEY_PSK_INTERVAL,
                    Integer.toString(Math.max(MIN_PSK_INTERVAL, psk.intervalSeconds())));
            save();
            return get();
        }
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
