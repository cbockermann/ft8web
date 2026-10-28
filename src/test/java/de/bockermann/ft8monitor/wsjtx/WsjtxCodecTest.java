package de.bockermann.ft8monitor.wsjtx;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsjtxCodecTest {

    // Real datagrams captured from a running WSJT-X 3.0.2 (schema 2). Ground truth.
    private static final byte[] HEARTBEAT = HexFormat.of().parseHex(
            "adbccbda00000002000000000000000657534a542d58"
                    + "0000000300000005332e302e3200000006636364666166");
    private static final byte[] STATUS = HexFormat.of().parseHex(
            "adbccbda00000002000000010000000657534a542d58"
                    + "0000000001ac601000000003465438ffffffff000000032d31350000"
                    + "0003465438010000000006c0000006c000000006444e3943424f0000"
                    + "00064a4f3331554dffffffff00ffffffff0000ffffffffffffffff00"
                    + "00000749432d3733303000000025435120444e3943424f204a4f3331"
                    + "2020202020202020202020202020202020202020202020");

    @Test
    void parsesRealHeartbeat() {
        WsjtxCodec.In in = WsjtxCodec.parse(HEARTBEAT, HEARTBEAT.length);
        WsjtxCodec.Heartbeat hb = assertInstanceOf(WsjtxCodec.Heartbeat.class, in);
        assertEquals("WSJT-X", hb.id());
        assertEquals("3.0.2", hb.version());
        assertEquals(3, hb.maxSchema());
    }

    @Test
    void parsesRealStatus() {
        WsjtxCodec.In in = WsjtxCodec.parse(STATUS, STATUS.length);
        WsjtxCodec.Status s = assertInstanceOf(WsjtxCodec.Status.class, in);
        assertEquals("WSJT-X", s.id());
        assertEquals(28_074_000L, s.dialHz()); // 10 m FT8, 0x01AC6010
        assertEquals("FT8", s.mode());
        assertEquals("-15", s.report());
        assertTrue(s.txEnabled());
        assertFalse(s.transmitting());
        assertEquals(1728, s.rxDf());
        assertEquals("DN9CBO", s.deCall());
        assertEquals("JO31UM", s.deGrid());
        assertEquals("IC-7300", s.configName());
        assertTrue(s.txMessage().startsWith("CQ DN9CBO JO31"));
    }

    @Test
    void parsesHandBuiltDecode() {
        ByteBuffer b = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0xADBCCBDA);      // magic
        b.putInt(2);               // schema
        b.putInt(WsjtxCodec.DECODE);
        putStr(b, "WSJT-X");       // id
        b.put((byte) 1);           // new
        b.putInt(39_600_000);      // time ms (11:00:00)
        b.putInt(-7);              // snr
        b.putDouble(0.2);          // dt
        b.putInt(1500);            // df
        putStr(b, "~");            // mode
        putStr(b, "CQ DL1ABC JO31");
        b.put((byte) 0);           // lowConfidence
        b.put((byte) 0);           // offAir
        byte[] data = new byte[b.position()];
        b.flip();
        b.get(data);

        WsjtxCodec.In in = WsjtxCodec.parse(data, data.length);
        WsjtxCodec.Decode d = assertInstanceOf(WsjtxCodec.Decode.class, in);
        assertTrue(d.isNew());
        assertEquals(-7, d.snr());
        assertEquals(0.2, d.dt(), 1e-9);
        assertEquals(1500, d.dfHz());
        assertEquals("CQ DL1ABC JO31", d.message());
        // And the Control DTO flags it as a CQ and tags the band from the dial freq.
        ControlDecode cd = ControlDecode.of("x", d, 14_074_000L, null);
        assertTrue(cd.cq());
        assertEquals("20m", cd.band());
    }

    @Test
    void transmitEntryIsFlaggedBandedAndTrimmed() {
        ControlDecode tx = ControlDecode.transmit("tx-1", "DL1ABC DN9CBO 73 ", 14_074_000L, 1500);
        assertTrue(tx.tx());
        assertFalse(tx.cq());
        assertEquals("20m", tx.band());
        assertEquals("DL1ABC DN9CBO 73", tx.message());
    }

    @Test
    void buildsReplyEchoingTheDecode() {
        byte[] msg = WsjtxCodec.reply("WSJT-X", 39_600_000, -7, 0.2, 1500, "~", "CQ DL1ABC JO31", false);
        ByteBuffer b = ByteBuffer.wrap(msg).order(ByteOrder.BIG_ENDIAN);
        assertEquals(0xADBCCBDAL, b.getInt() & 0xFFFFFFFFL);
        assertEquals(WsjtxCodec.SCHEMA, b.getInt());
        assertEquals(WsjtxCodec.REPLY, b.getInt());
        assertEquals("WSJT-X", getStr(b));
        assertEquals(39_600_000L, b.getInt() & 0xFFFFFFFFL);
        assertEquals(-7, b.getInt());
        assertEquals(0.2, b.getDouble(), 1e-9);
        assertEquals(1500, b.getInt());
        assertEquals("~", getStr(b));
        assertEquals("CQ DL1ABC JO31", getStr(b));
        assertEquals(0, b.get());            // lowConfidence
        assertEquals(0, b.get());            // modifiers
    }

    @Test
    void buildsHalt() {
        byte[] msg = WsjtxCodec.halt("WSJT-X", true);
        ByteBuffer b = ByteBuffer.wrap(msg).order(ByteOrder.BIG_ENDIAN);
        b.getInt(); // magic
        b.getInt(); // schema
        assertEquals(WsjtxCodec.HALT_TX, b.getInt());
        assertEquals("WSJT-X", getStr(b));
        assertEquals(1, b.get());            // auto = true
    }

    private static void putStr(ByteBuffer b, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        b.putInt(bytes.length);
        b.put(bytes);
    }

    private static String getStr(ByteBuffer b) {
        int len = b.getInt();
        byte[] s = new byte[len];
        b.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }
}
