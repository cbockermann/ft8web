package de.bockermann.ft8monitor.settings;

import java.util.List;

/**
 * Editable station settings for this ft8web installation.
 *
 * @param callsign       the operator's own callsign (may be empty if unset).
 * @param locator        the station's Maidenhead locator (4 or 6 chars; may be
 *                       empty).
 * @param gridResolution default activity-map resolution: {@code field} (2-char)
 *                       or {@code square} (4-char). Viewers can override it live.
 * @param backfillMinutes on startup, replay this many minutes from Kafka to
 *                        repopulate the in-memory views (0 = off). Takes effect
 *                        on the next restart.
 * @param psk            PSKReporter feeder settings (reuses callsign/locator as
 *                       the reporting station's identity).
 * @param bands          selectable FT8 bands for the Control view (name + dial
 *                       frequency + whether it is offered). May be empty/null to
 *                       keep the current/default list.
 */
public record Settings(String callsign, String locator, String gridResolution,
                       int backfillMinutes, Psk psk, List<Band> bands) {

    /**
     * A selectable band: a display {@code name} (e.g. "20m"), its FT8 dial
     * frequency in Hz, whether it is offered in the band picker, and the rig
     * {@code mode} to set with it (Hamlib name, e.g. {@code PKTUSB} = Data-U for
     * FT8).
     */
    public record Band(String name, long freqHz, boolean enabled, String mode) {
    }

    /**
     * PSKReporter feeder configuration.
     *
     * @param enabled         report observed stations to PSKReporter.
     * @param antenna         optional antenna description (free text).
     * @param host            PSKReporter collector host.
     * @param port            collector UDP port (4739 live, 14739 test).
     * @param intervalSeconds how often to flush spots (≥ 120 s per etiquette).
     */
    public record Psk(
            boolean enabled,
            String antenna,
            String host,
            int port,
            int intervalSeconds
    ) {
    }
}
