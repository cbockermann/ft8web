package de.bockermann.ft8monitor.settings;

import de.bockermann.ft8monitor.settings.Settings.Psk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SettingsServiceTest {

    private static Settings settings(String call, String loc, String res, Psk psk) {
        return new Settings(call, loc, res, psk);
    }

    @Test
    void defaultsWhenNoFile(@TempDir Path dir) {
        SettingsService svc = new SettingsService(dir.resolve("ft8web.properties").toString());
        assertEquals("", svc.get().callsign());
        assertEquals("", svc.get().locator());
        assertEquals("square", svc.get().gridResolution()); // finer grid by default

        Psk psk = svc.get().psk();
        assertFalse(psk.enabled());                       // off by default
        assertEquals("report.pskreporter.info", psk.host());
        assertEquals(4739, psk.port());
        assertEquals(300, psk.intervalSeconds());
    }

    @Test
    void persistsAndReloads(@TempDir Path dir) {
        String path = dir.resolve("ft8web.properties").toString();
        SettingsService svc = new SettingsService(path);
        svc.update(settings("dl0abc", "jo31hk", "field",
                new Psk(true, "Longwire", "example.test", 14739, 240)));

        // Normalized on save (calls/locator upper, resolution lower).
        assertEquals("DL0ABC", svc.get().callsign());
        assertEquals("JO31HK", svc.get().locator());
        assertEquals("field", svc.get().gridResolution());
        assertEquals(true, svc.get().psk().enabled());
        assertEquals("Longwire", svc.get().psk().antenna());
        assertEquals("example.test", svc.get().psk().host());
        assertEquals(14739, svc.get().psk().port());
        assertEquals(240, svc.get().psk().intervalSeconds());

        // A fresh service reads the persisted file.
        SettingsService reloaded = new SettingsService(path);
        assertEquals("DL0ABC", reloaded.get().callsign());
        assertEquals(14739, reloaded.get().psk().port());
        assertEquals(240, reloaded.get().psk().intervalSeconds());
    }

    @Test
    void invalidResolutionFallsBackToDefault(@TempDir Path dir) {
        SettingsService svc = new SettingsService(dir.resolve("ft8web.properties").toString());
        svc.update(settings("", "", "bogus", null));
        assertEquals("square", svc.get().gridResolution());
    }

    @Test
    void intervalFlooredAndPortValidated(@TempDir Path dir) {
        SettingsService svc = new SettingsService(dir.resolve("ft8web.properties").toString());
        svc.update(settings("", "", "square", new Psk(false, "", "", 99999, 30)));
        assertEquals(4739, svc.get().psk().port());        // invalid port → default
        assertEquals(120, svc.get().psk().intervalSeconds()); // below floor → 120
        assertEquals("report.pskreporter.info", svc.get().psk().host()); // blank → default
    }
}
