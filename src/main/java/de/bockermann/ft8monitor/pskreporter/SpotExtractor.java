package de.bockermann.ft8monitor.pskreporter;

import de.bockermann.ft8monitor.model.Decode;
import de.bockermann.ft8monitor.overview.Maidenhead;
import de.bockermann.ft8monitor.qso.Callsigns;
import de.bockermann.ft8monitor.qso.Callsigns.Stations;

import java.util.Optional;

/**
 * Turns a decode into a PSKReporter {@link Spot}, or nothing if it must not be
 * reported. This is the mandatory garbage filter: only decodes with a real RF
 * frequency (rf_hz) and a plausible SENDER callsign pass — so {@code i3=}
 * mis-decodes, noise, reports and sign-offs never reach PSKReporter.
 */
public final class SpotExtractor {

    private SpotExtractor() {
    }

    /** Longest plausible callsign (incl. portable prefix/suffix). */
    private static final int MAX_CALL_LEN = 12;

    /**
     * @param decode  the decode.
     * @param ownCall the operator's own callsign (own transmissions are not
     *                reported); may be empty.
     * @return the spot to report, or empty.
     */
    public static Optional<Spot> extract(Decode decode, String ownCall) {
        if (decode == null || decode.msg() == null || decode.snr() == null) {
            return Optional.empty();
        }
        // PSKReporter needs the true transmit frequency: require rf_hz.
        Long freqHz = rfHz(decode);
        if (freqHz == null) {
            return Optional.empty();
        }

        String sender = senderCall(decode.msg());
        if (sender == null || !isReportable(sender)) {
            return Optional.empty();
        }
        if (ownCall != null && sender.equalsIgnoreCase(ownCall.trim())) {
            return Optional.empty(); // never report our own transmissions
        }

        String grid = Maidenhead.squareOf(decode.msg()); // sender's grid, or null
        String band = decode.tags().getOrDefault("band", "");
        return Optional.of(new Spot(sender, grid == null ? "" : grid,
                freqHz, decode.snr(), band, decode.unix()));
    }

    /** The SENDER callsign: CQ caller, or the second call of a directed message. */
    static String senderCall(String msg) {
        Stations st = Callsigns.parse(msg);
        if (st instanceof Stations.Cq cq) {
            return cq.caller();
        }
        if (st instanceof Stations.Directed dir) {
            return dir.b();
        }
        return null;
    }

    /** Plausibility gate: a real callsign, bounded length, not a directive/grid/report. */
    static boolean isReportable(String call) {
        return call != null
                && call.length() <= MAX_CALL_LEN
                && Callsigns.isCallsign(call)
                && !Callsigns.isSignoff(call);
    }

    /** Absolute RF frequency in Hz from the rf_hz tag, or null if absent/unparseable. */
    static Long rfHz(Decode decode) {
        String raw = decode.tags().get("rf_hz");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // May arrive as "7074000" or "7074000.0".
            return (long) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
