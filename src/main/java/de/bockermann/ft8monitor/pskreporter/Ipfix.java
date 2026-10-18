package de.bockermann.ft8monitor.pskreporter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds a PSKReporter IPFIX (RFC 7011) datagram, byte-for-byte after the
 * reference implementation in WSJT-X {@code Network/PSKReporter.cpp}:
 * PSKReporter enterprise number (PEN) 30351, sender template id 0x50e3,
 * receiver options template id 0x50e2, big-endian, variable-length strings with
 * a 1-byte length prefix, and 4-byte set alignment padding.
 *
 * <p>Each datagram is self-contained: message header, the two template sets, the
 * receiver data set (one record), then the sender data set (one record per
 * spot). Templates are repeated in every datagram so the collector always has
 * them.
 */
public final class Ipfix {

    private Ipfix() {
    }

    static final int PEN = 30351;
    static final int SENDER_TEMPLATE_ID = 0x50e3;
    static final int RECEIVER_TEMPLATE_ID = 0x50e2;

    private static final int E = 0x8000; // enterprise-specific IE flag

    /** The reporting station (us). */
    public record Receiver(String call, String grid, String software, String antenna) {
    }

    /** Build one IPFIX datagram for the given spots. */
    public static byte[] build(Receiver rx, List<Spot> spots,
                               int sequenceNumber, int observationId, long exportTimeUnix) {
        Buf msg = new Buf();
        // Message header (16 bytes).
        msg.u16(10);                 // version
        msg.u16(0);                  // total length (placeholder, offset 2)
        msg.u32(exportTimeUnix);     // export time (offset 4)
        msg.u32(sequenceNumber);     // sequence number
        msg.u32(observationId);      // observation domain id

        msg.raw(senderTemplateSet());
        msg.raw(receiverTemplateSet());
        msg.raw(receiverDataSet(rx));
        msg.raw(senderDataSet(spots));

        byte[] out = msg.toByteArray();
        putU16(out, 2, out.length);  // fill in total length
        return out;
    }

    // --- Template sets ------------------------------------------------------

    private static byte[] senderTemplateSet() {
        Buf c = new Buf();
        c.u16(SENDER_TEMPLATE_ID);
        c.u16(7); // field count
        entField(c, E + 1, 0xffff);  // senderCallsign (var)
        entField(c, E + 5, 5);       // frequency (5 bytes, Hz)
        entField(c, E + 6, 1);       // sNR (1 byte)
        entField(c, E + 10, 0xffff); // mode (var)
        entField(c, E + 3, 0xffff);  // senderLocator (var)
        entField(c, E + 11, 1);      // informationSource (1 byte)
        c.u16(150);                  // dateTimeSeconds (standard IE, no PEN)
        c.u16(4);
        return set(2, c.toByteArray());
    }

    private static byte[] receiverTemplateSet() {
        Buf c = new Buf();
        c.u16(RECEIVER_TEMPLATE_ID);
        c.u16(5); // field count
        c.u16(0); // scope field count
        entField(c, E + 2, 0xffff);  // receiverCallsign (var)
        entField(c, E + 4, 0xffff);  // receiverLocator (var)
        entField(c, E + 8, 0xffff);  // decodingSoftware (var)
        entField(c, E + 9, 0xffff);  // antennaInformation (var)
        entField(c, E + 13, 0xffff); // rigInformation (var)
        return set(3, c.toByteArray());
    }

    // --- Data sets ----------------------------------------------------------

    private static byte[] receiverDataSet(Receiver rx) {
        Buf c = new Buf();
        c.str(rx.call());
        c.str(rx.grid());
        c.str(rx.software());
        c.str(rx.antenna());
        c.str(""); // rigInformation (unused)
        return set(RECEIVER_TEMPLATE_ID, c.toByteArray());
    }

    private static byte[] senderDataSet(List<Spot> spots) {
        Buf c = new Buf();
        for (Spot s : spots) {
            c.str(s.call());
            c.freq5(s.freqHz());
            c.i8(s.snr());
            c.str("FT8");
            c.str(s.grid());
            c.u8(1); // informationSource = automatic
            c.u32(s.timeUnix());
        }
        return set(SENDER_TEMPLATE_ID, c.toByteArray());
    }

    // --- Set framing (setId + length + 4-byte padding) ----------------------

    private static byte[] set(int setId, byte[] content) {
        Buf b = new Buf();
        b.u16(setId);
        b.u16(0); // length placeholder
        b.raw(content);
        int pad = (4 - b.size() % 4) % 4;
        for (int i = 0; i < pad; i++) {
            b.u8(0);
        }
        byte[] out = b.toByteArray();
        putU16(out, 2, out.length); // length includes padding
        return out;
    }

    private static void entField(Buf b, int ie, int length) {
        b.u16(ie);
        b.u16(length);
        b.u32(PEN);
    }

    private static void putU16(byte[] a, int off, int value) {
        a[off] = (byte) ((value >> 8) & 0xff);
        a[off + 1] = (byte) (value & 0xff);
    }

    /** Minimal big-endian byte buffer. */
    private static final class Buf {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void u8(int v) {
            out.write(v & 0xff);
        }

        void i8(int v) {
            out.write(v & 0xff);
        }

        void u16(int v) {
            out.write((v >> 8) & 0xff);
            out.write(v & 0xff);
        }

        void u32(long v) {
            out.write((int) ((v >> 24) & 0xff));
            out.write((int) ((v >> 16) & 0xff));
            out.write((int) ((v >> 8) & 0xff));
            out.write((int) (v & 0xff));
        }

        /** 5-byte (40-bit) big-endian frequency in Hz. */
        void freq5(long hz) {
            out.write((int) ((hz >> 32) & 0xff));
            out.write((int) ((hz >> 24) & 0xff));
            out.write((int) ((hz >> 16) & 0xff));
            out.write((int) ((hz >> 8) & 0xff));
            out.write((int) (hz & 0xff));
        }

        /** Variable-length string: 1-byte length prefix + UTF-8 (truncated to 254). */
        void str(String s) {
            byte[] utf = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
            int len = Math.min(utf.length, 254);
            out.write(len);
            out.write(utf, 0, len);
        }

        void raw(byte[] b) {
            out.write(b, 0, b.length);
        }

        int size() {
            return out.size();
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
