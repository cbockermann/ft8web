package de.bockermann.ft8monitor.wsjtx;

/**
 * Station status for the Control view, normalised from a WSJT-X Status message.
 * {@code up=false} means no WSJT-X datagrams are arriving.
 */
public record ControlStatus(boolean up, Long dialHz, String mode, String dxCall, String report,
                            boolean txEnabled, boolean transmitting, boolean decoding,
                            Integer rxDf, Integer txDf, String deCall, String deGrid,
                            String configName, String txMessage, boolean txWatchdog) {

    public static ControlStatus down() {
        return new ControlStatus(false, null, null, null, null, false, false, false,
                null, null, null, null, null, null, false);
    }

    public static ControlStatus of(WsjtxCodec.Status s) {
        return new ControlStatus(true, s.dialHz(), s.mode(), s.dxCall(), s.report(),
                s.txEnabled(), s.transmitting(), s.decoding(), s.rxDf(), s.txDf(),
                s.deCall(), s.deGrid(), s.configName(), s.txMessage(), s.txWatchdog());
    }
}
