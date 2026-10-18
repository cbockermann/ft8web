package de.bockermann.ft8monitor.overview;

import de.bockermann.ft8monitor.qso.Callsigns;

import java.util.regex.Pattern;

/**
 * Maidenhead locator helpers for the activity map.
 */
public final class Maidenhead {

    private Maidenhead() {
    }

    /** A locator token: field (2× A–R) + square (2 digits) + optional subsquare. */
    private static final Pattern LOCATOR = Pattern.compile("^[A-R]{2}[0-9]{2}([A-X]{2})?$");

    /**
     * The 2-character field (e.g. {@code JO}) of the first Maidenhead locator in
     * a decoded message, or {@code null} if the message carries no grid (reports,
     * sign-offs, ...).
     */
    public static String fieldOf(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }
        for (String token : msg.trim().split("\\s+")) {
            // RR73 and friends match the locator shape but are sign-offs, not grids.
            if (!Callsigns.isSignoff(token) && LOCATOR.matcher(token).matches()) {
                return token.substring(0, 2);
            }
        }
        return null;
    }
}
