package de.bockermann.ft8monitor.settings;

/**
 * Editable station settings for this ft8web installation.
 *
 * @param callsign the operator's own callsign (may be empty if unset).
 * @param locator  the station's Maidenhead locator (4 or 6 chars; may be empty).
 */
public record Settings(String callsign, String locator) {
}
