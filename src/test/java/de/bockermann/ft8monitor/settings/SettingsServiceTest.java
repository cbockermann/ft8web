package de.bockermann.ft8monitor.settings;

import de.bockermann.ft8monitor.settings.Settings.Band;
import de.bockermann.ft8monitor.settings.Settings.Psk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsServiceTest {

    private static final int DEFAULT_BACKFILL = 15;

    private static SettingsService service(Path dir) {
        return new SettingsService(dir.resolve("ft8web.properties").toString(), DEFAULT_BACKFILL);
    }

    private static Settings settings(String call, String loc, String res, int backfill, Psk psk) {
        return new Settings(call, loc, res, backfill, psk, null);
    }

    @Test
    void defaultsWhenNoFile(@TempDir Path dir) {
        SettingsService svc = service(dir);
        assertEquals("", svc.get().callsign());
        assertEquals("", svc.get().locator());
        assertEquals("square", svc.get().gridResolution()); // finer grid by default
        assertEquals(15, svc.get().backfillMinutes());      // from ft8.backfill-minutes default

        Psk psk = svc.get().psk();
        assertFalse(psk.enabled());                       // off by default
        assertEquals("report.pskreporter.info", psk.host());
        assertEquals(4739, psk.port());
        assertEquals(300, psk.intervalSeconds());
    }

    @Test
    void persistsAndReloads(@TempDir Path dir) {
        String path = dir.resolve("ft8web.properties").toString();
        SettingsService svc = new SettingsService(path, DEFAULT_BACKFILL);
        svc.update(settings("dl0abc", "jo31hk", "field", 30,
                new Psk(true, "Longwire", "example.test", 14739, 240)));

        assertEquals("DL0ABC", svc.get().callsign());
        assertEquals("JO31HK", svc.get().locator());
        assertEquals("field", svc.get().gridResolution());
        assertEquals(30, svc.get().backfillMinutes());
        assertEquals(true, svc.get().psk().enabled());
        assertEquals("example.test", svc.get().psk().host());
        assertEquals(14739, svc.get().psk().port());

        // A fresh service reads the persisted file.
        SettingsService reloaded = new SettingsService(path, DEFAULT_BACKFILL);
        assertEquals("DL0ABC", reloaded.get().callsign());
        assertEquals(30, reloaded.get().backfillMinutes());
        assertEquals(14739, reloaded.get().psk().port());
    }

    @Test
    void invalidResolutionFallsBackToDefault(@TempDir Path dir) {
        SettingsService svc = service(dir);
        svc.update(settings("", "", "bogus", 15, null));
        assertEquals("square", svc.get().gridResolution());
    }

    @Test
    void backfillClampedToRange(@TempDir Path dir) {
        SettingsService svc = service(dir);
        svc.update(settings("", "", "square", -5, null));
        assertEquals(0, svc.get().backfillMinutes());       // negative → 0 (off)
        svc.update(settings("", "", "square", 100000, null));
        assertEquals(1440, svc.get().backfillMinutes());    // above 24 h → capped
    }

    @Test
    void intervalFlooredAndPortValidated(@TempDir Path dir) {
        SettingsService svc = service(dir);
        svc.update(settings("", "", "square", 15, new Psk(false, "", "", 99999, 30)));
        assertEquals(4739, svc.get().psk().port());        // invalid port → default
        assertEquals(120, svc.get().psk().intervalSeconds()); // below floor → 120
        assertEquals("report.pskreporter.info", svc.get().psk().host()); // blank → default
    }

    @Test
    void defaultBandsWhenUnset(@TempDir Path dir) {
        List<Band> bands = service(dir).get().bands();
        assertEquals(4, bands.size()); // 80/40/20/10m
        assertTrue(bands.stream().anyMatch(b -> b.name().equals("20m") && b.freqHz() == 14_074_000L));
    }

    @Test
    void persistsCustomBandsAcrossReload(@TempDir Path dir) {
        SettingsService svc = service(dir);
        svc.update(new Settings("", "", "square", 15, new Psk(false, "", "", 4739, 300),
                List.of(new Band("60m", 5_357_000L, true, "PKTUSB"),
                        new Band("30m", 10_136_000L, false, "USB"))));
        List<Band> bands = service(dir).get().bands(); // re-read from the file
        assertEquals(2, bands.size());
        assertEquals("60m", bands.get(0).name());
        assertEquals("PKTUSB", bands.get(0).mode());
        assertEquals(10_136_000L, bands.get(1).freqHz());
        assertEquals("USB", bands.get(1).mode());
        assertFalse(bands.get(1).enabled());
    }

    @Test
    void blankBandModeDefaultsToPktusb(@TempDir Path dir) {
        SettingsService svc = service(dir);
        svc.update(new Settings("", "", "square", 15, new Psk(false, "", "", 4739, 300),
                List.of(new Band("20m", 14_074_000L, true, null))));
        assertEquals("PKTUSB", svc.get().bands().get(0).mode());
    }

    @Test
    void defaultBandsCarryPktusbMode(@TempDir Path dir) {
        assertTrue(service(dir).get().bands().stream().allMatch(b -> "PKTUSB".equals(b.mode())));
    }

    @Test
    void emptyBandsFallBackToDefaults(@TempDir Path dir) {
        SettingsService svc = service(dir);
        svc.update(new Settings("", "", "square", 15, new Psk(false, "", "", 4739, 300), List.of()));
        assertEquals(4, svc.get().bands().size());
    }
}
