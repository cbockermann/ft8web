package de.bockermann.ft8monitor.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsServiceTest {

    @Test
    void defaultsWhenNoFile(@TempDir Path dir) {
        SettingsService svc = new SettingsService(dir.resolve("ft8web.properties").toString());
        assertEquals("", svc.get().callsign());
        assertEquals("", svc.get().locator());
        assertEquals("square", svc.get().gridResolution()); // finer grid by default
    }

    @Test
    void persistsAndReloads(@TempDir Path dir) {
        String path = dir.resolve("ft8web.properties").toString();
        SettingsService svc = new SettingsService(path);
        svc.update(new Settings("dl0abc", "jo31hk", "field"));

        // Normalized on save (calls/locator upper, resolution lower).
        assertEquals("DL0ABC", svc.get().callsign());
        assertEquals("JO31HK", svc.get().locator());
        assertEquals("field", svc.get().gridResolution());

        // A fresh service reads the persisted file.
        SettingsService reloaded = new SettingsService(path);
        assertEquals("DL0ABC", reloaded.get().callsign());
        assertEquals("JO31HK", reloaded.get().locator());
        assertEquals("field", reloaded.get().gridResolution());
    }

    @Test
    void invalidResolutionFallsBackToDefault(@TempDir Path dir) {
        SettingsService svc = new SettingsService(dir.resolve("ft8web.properties").toString());
        svc.update(new Settings("", "", "bogus"));
        assertEquals("square", svc.get().gridResolution());
    }
}
