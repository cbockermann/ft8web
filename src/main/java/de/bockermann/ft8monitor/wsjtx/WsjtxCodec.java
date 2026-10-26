package de.bockermann.ft8monitor.wsjtx;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the WSJT-X UDP Message Protocol (the one GridTracker/JTAlert use).
 *
 * <p>Datagrams are Qt {@code QDataStream}, big-endian: {@code quint32 magic}
 * (0xadbccbda), {@code quint32 schema}, {@code quint32 type}, {@code QString id},
 * then type-specific fields. A {@code QString} is a {@code quint32} byte length
 * (0xffffffff = null) followed by that many <b>UTF-8</b> bytes — verified against
 * a live WSJT-X 3.0.2 capture.
 *
 * <p>Parses the inbound messages we use (Heartbeat/Status/Decode/LoggedADIF) and
 * builds the outbound ones (Reply/Halt). Everything else is reported as
 * {@link Unknown}.
 */
public final class WsjtxCodec {

    public static final long MAGIC = 0xADBCCBDAL;
    /** Schema we emit; WSJT-X negotiates down to the lower of the two. */
    public static final int SCHEMA = 2;

    // Message type ids.
    public static final int HEARTBEAT = 0;
    public static final int STATUS = 1;
    public static final int DECODE = 2;
    public static final int CLEAR = 3;
    public static final int REPLY = 4;
    public static final int QSO_LOGGED = 5;
    public static final int CLOSE = 6;
    public static final int HALT_TX = 8;
    public static final int LOGGED_ADIF = 12;

    private WsjtxCodec() {
    }

    /** Marker for any parsed inbound message; carries the WSJT-X instance id. */
    public sealed interface In permits Heartbeat, Status, Decode, LoggedAdif, Closed, Unknown {
        String id();
    }

    public record Heartbeat(String id, int maxSchema, String version, String revision) implements In {
    }

    public record Status(String id, long dialHz, String mode, String dxCall, String report,
                         String txMode, boolean txEnabled, boolean transmitting, boolean decoding,
                         int rxDf, int txDf, String deCall, String deGrid, String dxGrid,
                         boolean txWatchdog, String configName, String txMessage) implements In {
    }

    public record Decode(String id, boolean isNew, long timeMs, int snr, double dt, int dfHz,
                         String mode, String message, boolean lowConfidence, boolean offAir) implements In {
    }

    public record LoggedAdif(String id, String adif) implements In {
    }

    public record Closed(String id) implements In {
    }

    public record Unknown(String id, int type) implements In {
    }

    /** Parse a datagram, or {@code null} if it is not a WSJT-X message. */
    public static In parse(byte[] data, int length) {
        Reader r = new Reader(data, length);
        try {
            if (r.u32() != MAGIC) {
                return null;
            }
            r.u32(); // schema (negotiated; we don't branch on it here)
            int type = (int) r.u32();
            String id = r.str();
            return switch (type) {
                case HEARTBEAT -> new Heartbeat(id, (int) r.u32(), r.str(), r.str());
                case STATUS -> new Status(id, r.u64(), r.str(), r.str(), r.str(), r.str(),
                        r.bool(), r.bool(), r.bool(), (int) r.u32(), (int) r.u32(), r.str(), r.str(),
                        r.str(), r.bool(), skipToConfig(r), r.str());
                case DECODE -> new Decode(id, r.bool(), r.u32(), r.i32(), r.dbl(), (int) r.u32(),
                        r.str(), r.str(), r.bool(), r.bool());
                case LOGGED_ADIF -> new LoggedAdif(id, r.str());
                case CLOSE -> new Closed(id);
                default -> new Unknown(id, type);
            };
        } catch (BufferUnderflowException e) {
            return null; // truncated / unexpected schema layout
        }
    }

    /**
     * Status carries, between {@code txWatchdog} and {@code configName}, a few
     * fields that vary across schema versions (sub-mode, fast-mode, special-op,
     * frequency tolerance, T/R period). We don't use them, so read past them to
     * reach {@code configName}. Layout per WSJT-X 3.x schema 2.
     */
    private static String skipToConfig(Reader r) {
        r.str();  // subMode (QString, may be null)
        r.bool(); // fastMode
        r.u8();   // specialOpMode
        r.u32();  // frequencyTolerance
        r.u32();  // trPeriod
        return r.str(); // configName
    }

    /**
     * Build a Reply datagram: tells WSJT-X to answer this decode (as if the user
     * double-clicked it). The fields must echo the original Decode. {@code id} must
     * be the WSJT-X instance id.
     */
    public static byte[] reply(String id, long timeMs, int snr, double dt, int dfHz,
                               String mode, String message, boolean lowConfidence) {
        Writer w = new Writer(REPLY, id);
        w.u32(timeMs);
        w.i32(snr);
        w.dbl(dt);
        w.u32(dfHz);
        w.str(mode);
        w.str(message);
        w.bool(lowConfidence);
        w.u8(0); // modifiers (no keyboard modifiers)
        return w.toBytes();
    }

    /** Build a Halt-Tx datagram. {@code auto=false} halts now, {@code true} after this Tx. */
    public static byte[] halt(String id, boolean auto) {
        Writer w = new Writer(HALT_TX, id);
        w.bool(auto);
        return w.toBytes();
    }

    // --- QDataStream primitives (big-endian) --------------------------------

    private static final class Reader {
        private final ByteBuffer b;

        Reader(byte[] data, int length) {
            b = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        }

        long u32() {
            return b.getInt() & 0xFFFFFFFFL;
        }

        int i32() {
            return b.getInt();
        }

        long u64() {
            return b.getLong();
        }

        int u8() {
            return b.get() & 0xFF;
        }

        boolean bool() {
            return b.get() != 0;
        }

        double dbl() {
            return b.getDouble();
        }

        String str() {
            long len = b.getInt() & 0xFFFFFFFFL;
            if (len == 0xFFFFFFFFL) {
                return null; // null QString
            }
            if (len > b.remaining()) {
                throw new BufferUnderflowException();
            }
            byte[] s = new byte[(int) len];
            b.get(s);
            return new String(s, StandardCharsets.UTF_8);
        }
    }

    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        private final DataOutputStream d = new DataOutputStream(out); // big-endian

        Writer(int type, String id) {
            u32(MAGIC);
            u32(SCHEMA);
            u32(type);
            str(id);
        }

        void u32(long v) {
            write(() -> d.writeInt((int) v));
        }

        void i32(int v) {
            write(() -> d.writeInt(v));
        }

        void u64(long v) {
            write(() -> d.writeLong(v));
        }

        void u8(int v) {
            write(() -> d.writeByte(v));
        }

        void bool(boolean v) {
            write(() -> d.writeByte(v ? 1 : 0));
        }

        void dbl(double v) {
            write(() -> d.writeDouble(v));
        }

        void str(String s) {
            write(() -> {
                if (s == null) {
                    d.writeInt(-1); // 0xffffffff null QString
                } else {
                    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                    d.writeInt(bytes.length);
                    d.write(bytes);
                }
            });
        }

        byte[] toBytes() {
            return out.toByteArray();
        }

        private void write(IoOp op) {
            try {
                op.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e); // ByteArrayOutputStream never throws
            }
        }

        private interface IoOp {
            void run() throws IOException;
        }
    }
}
