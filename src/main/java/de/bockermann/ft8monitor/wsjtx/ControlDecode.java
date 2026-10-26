package de.bockermann.ft8monitor.wsjtx;

/**
 * A WSJT-X decode for the Control view: {@code id} is used to request a reply,
 * {@code cq} flags a CQ call (the primary thing to answer), {@code at} is the
 * decode time (HH:MM:SS UTC).
 */
public record ControlDecode(String id, String at, int snr, double dt, int df,
                            String mode, String message, boolean cq, boolean isNew,
                            boolean lowConfidence) {

    public static ControlDecode of(String id, WsjtxCodec.Decode d) {
        long s = d.timeMs() / 1000;
        String at = String.format("%02d:%02d:%02d", (s / 3600) % 24, (s / 60) % 60, s % 60);
        String msg = d.message() == null ? "" : d.message();
        boolean cq = msg.trim().startsWith("CQ");
        return new ControlDecode(id, at, d.snr(), d.dt(), d.dfHz(), d.mode(), msg, cq,
                d.isNew(), d.lowConfidence());
    }
}
