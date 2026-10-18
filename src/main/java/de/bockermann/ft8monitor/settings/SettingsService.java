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
            return new Settings(
                    props.getProperty(KEY_CALLSIGN, ""),
                    props.getProperty(KEY_LOCATOR, ""),
                    props.getProperty(KEY_GRID_RESOLUTION, DEFAULT_GRID_RESOLUTION));
        }
    }

    /** Normalize and persist the given settings, returning the stored result. */
    public Settings update(Settings incoming) {
        synchronized (lock) {
            props.setProperty(KEY_CALLSIGN, norm(incoming.callsign()).toUpperCase());
            props.setProperty(KEY_LOCATOR, norm(incoming.locator()).toUpperCase());
            props.setProperty(KEY_GRID_RESOLUTION, normResolution(incoming.gridResolution()));
            save();
            return get();
        }
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
