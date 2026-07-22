package de.bockermann.ft8monitor.qso;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses the participating stations out of an FT8 message text. Port of the
 * callsign logic in ft8mon's {@code scripts/qsls.py}.
 *
 * <p>A token is a callsign when it consists only of {@code A-Z 0-9 /} (after
 * dropping surrounding {@code <...>} hash brackets), has at least one letter
 * AND one digit, and is not a grid, report or sign-off.
 */
public final class Callsigns {

    private Callsigns() {
    }

    /** Grid locator: {@code [A-R]{2}[0-9]{2}} optionally + 2 more letters. */
    private static final Pattern GRID = Pattern.compile("^[A-R]{2}[0-9]{2}([A-X]{2})?$");
    /** Signal report: optional leading {@code R} then +/- and 1–2 digits. */
    private static final Pattern REPORT = Pattern.compile("^R?[-+]\\d{1,2}$");
    /** Allowed callsign characters. */
    private static final Pattern CALL_CHARS = Pattern.compile("^[A-Z0-9/]+$");
    /** Sign-off / directive tokens that are never callsigns. */
    private static final Set<String> SIGNOFFS = Set.of(
            "RRR", "RR73", "73", "R73", "TU", "TU73", "QSL", "DE", "CQ", "DX");

    /** The stations a message is between. */
    public sealed interface Stations {
        /** A {@code CQ} calling: a single caller looking for a contact. */
        record Cq(String caller) implements Stations {
        }

        /** A directed exchange between two stations. */
        record Directed(String a, String b) implements Stations {
        }

        /** Not groupable (no two clear callsigns, and not a CQ). */
        record None() implements Stations {
        }
    }

    /** Strip surrounding {@code <...>} hash brackets from a token. */
    private static String unhash(String token) {
        if (token.length() >= 2 && token.charAt(0) == '<' && token.charAt(token.length() - 1) == '>') {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    /** Is {@code token} a callsign (after unhashing)? */
    public static boolean isCallsign(String token) {
        String t = unhash(token);
        if (t.isEmpty() || !CALL_CHARS.matcher(t).matches()) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                hasLetter = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            return false;
        }
        if (SIGNOFFS.contains(t) || GRID.matcher(t).matches() || REPORT.matcher(t).matches()) {
            return false;
        }
        return true;
    }

    /**
     * Determine the stations involved in a message.
     *
     * <ul>
     *   <li>{@code CQ ...} → the first callsign after {@code CQ} (a directive
     *       such as {@code DX} or a region has no digit and is skipped
     *       automatically).</li>
     *   <li>otherwise the first two callsign-like tokens are the two
     *       stations.</li>
     * </ul>
     */
    public static Stations parse(String msg) {
        if (msg == null || msg.isBlank()) {
            return new Stations.None();
        }
        String[] tokens = msg.trim().split("\\s+");

        if (tokens[0].equals("CQ")) {
            for (int i = 1; i < tokens.length; i++) {
                if (isCallsign(tokens[i])) {
                    return new Stations.Cq(unhash(tokens[i]));
                }
            }
            return new Stations.None();
        }

        List<String> calls = new ArrayList<>(2);
        for (String token : tokens) {
            if (isCallsign(token)) {
                calls.add(unhash(token));
                if (calls.size() == 2) {
                    break;
                }
            }
        }
        if (calls.size() == 2) {
            return new Stations.Directed(calls.get(0), calls.get(1));
        }
        return new Stations.None();
    }
}
