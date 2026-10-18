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
     * The first Maidenhead locator token in a decoded message, or {@code null}
     * if the message carries no grid (reports, sign-offs, ...).
     */
    private static String locatorOf(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }
        for (String token : msg.trim().split("\\s+")) {
            // RR73 and friends match the locator shape but are sign-offs, not grids.
            if (!Callsigns.isSignoff(token) && LOCATOR.matcher(token).matches()) {
                return token;
            }
        }
        return null;
    }

    /** The 2-character field (e.g. {@code JO}) of the first locator, or {@code null}. */
    public static String fieldOf(String msg) {
        String loc = locatorOf(msg);
        return loc == null ? null : loc.substring(0, 2);
    }

    /**
     * The 4-character square (field + square, e.g. {@code JO62}) of the first
     * locator, or {@code null}. FT8's standard grid exchange carries exactly
     * these 4 characters.
     */
    public static String squareOf(String msg) {
        String loc = locatorOf(msg);
        return loc == null ? null : loc.substring(0, 4);
    }

    /** The field (2 chars) that a square (4 chars) belongs to. */
    public static String fieldOfSquare(String square) {
        return square.substring(0, 2);
    }
}
