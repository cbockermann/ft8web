package de.bockermann.ft8monitor.settings;

/**
 * Editable station settings for this ft8web installation.
 *
 * @param callsign       the operator's own callsign (may be empty if unset).
 * @param locator        the station's Maidenhead locator (4 or 6 chars; may be
 *                       empty).
 * @param gridResolution default activity-map resolution: {@code field} (2-char)
 *                       or {@code square} (4-char). Viewers can override it live.
 */
public record Settings(String callsign, String locator, String gridResolution) {
}
