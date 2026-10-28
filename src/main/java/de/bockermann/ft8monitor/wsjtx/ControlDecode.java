package de.bockermann.ft8monitor.wsjtx;

/**
 * A WSJT-X decode for the Control view: {@code id} is used to request a reply,
 * {@code cq} flags a CQ call (the primary thing to answer), {@code at} is the
 * decode time (HH:MM:SS UTC), {@code band} is the band it was heard on (from the
 * dial frequency at decode time, so it stays correct after a band change), and
 * {@code tx} marks our own transmission (as opposed to a received decode).
 */
public record ControlDecode(String id, String at, String band, int snr, double dt, int df,
                            String mode, String message, boolean cq, boolean isNew,
                            boolean lowConfidence, boolean tx, DecodeFacts facts) {

    public static ControlDecode of(String id, WsjtxCodec.Decode d, Long dialHz, DecodeFacts facts) {
        long s = d.timeMs() / 1000;
        String at = String.format("%02d:%02d:%02d", (s / 3600) % 24, (s / 60) % 60, s % 60);
        String msg = d.message() == null ? "" : d.message();
        boolean cq = msg.trim().startsWith("CQ");
        return new ControlDecode(id, at, BandPlan.of(dialHz), d.snr(), d.dt(), d.dfHz(), d.mode(),
                msg, cq, d.isNew(), d.lowConfidence(), false, facts);
    }

    /** Our own transmission (from WSJT-X txMessage while transmitting). */
    public static ControlDecode transmit(String id, String message, Long dialHz, int txDf) {
        long s = (System.currentTimeMillis() / 1000) % 86400; // seconds since UTC midnight
        String at = String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return new ControlDecode(id, at, BandPlan.of(dialHz), 0, 0.0, txDf, "FT8",
                message == null ? "" : message.trim(), false, true, false, true, null);
    }
}
