package de.bockermann.ft8monitor.pskreporter;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpfixTest {

    private static int u16(byte[] a, int off) {
        return ((a[off] & 0xff) << 8) | (a[off + 1] & 0xff);
    }

    private static long u32(byte[] a, int off) {
        return ((long) (a[off] & 0xff) << 24) | ((a[off + 1] & 0xff) << 16)
                | ((a[off + 2] & 0xff) << 8) | (a[off + 3] & 0xff);
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    void datagramHeaderAndStructure() {
        Ipfix.Receiver rx = new Ipfix.Receiver("DL0ABC", "JO62", "ft8web", "Longwire");
        Spot spot = new Spot("AB1HL", "FN42", 7074000L, -12, "40m", 1700000000L);
        byte[] dg = Ipfix.build(rx, List.of(spot), 1, 0x12345678, 1700000123L);

        assertEquals(0x000A, u16(dg, 0), "version 10");
        assertEquals(dg.length, u16(dg, 2), "message length field matches size");
        assertEquals(0, dg.length % 4, "4-byte aligned");
        assertEquals(1700000123L, u32(dg, 4), "export time");
        assertEquals(1L, u32(dg, 8), "sequence number");
        assertEquals(0x12345678L, u32(dg, 12), "observation domain id");

        // First set is the sender template set (setId 2).
        assertEquals(2, u16(dg, 16), "first set is a template set");

        // PSKReporter PEN 30351 (0x0000768F) must appear.
        assertTrue(contains(dg, new byte[]{0x00, 0x00, 0x76, (byte) 0x8F}), "PEN 30351 present");

        // Both template ids present.
        assertTrue(contains(dg, new byte[]{0x50, (byte) 0xe3}), "sender template id");
        assertTrue(contains(dg, new byte[]{0x50, (byte) 0xe2}), "receiver template id");

        // Frequency encoded as 5-byte big-endian (7074000 = 0x00 00 6B F0 D0).
        assertTrue(contains(dg, new byte[]{0x00, 0x00, 0x6B, (byte) 0xF0, (byte) 0xD0}), "freq 5-byte");

        // Receiver and sender callsigns present as UTF-8.
        assertTrue(contains(dg, "DL0ABC".getBytes(StandardCharsets.UTF_8)), "receiver call");
        assertTrue(contains(dg, "AB1HL".getBytes(StandardCharsets.UTF_8)), "sender call");
        assertTrue(contains(dg, "FT8".getBytes(StandardCharsets.UTF_8)), "mode");
    }

    @Test
    void handlesEmptyGridAndManySpots() {
        Ipfix.Receiver rx = new Ipfix.Receiver("DL0ABC", "JO62", "ft8web", "");
        Spot a = new Spot("AB1HL", "", 7074000L, -5, "40m", 1700000000L);
        Spot b = new Spot("W2XYZ", "FN31", 14074000L, 3, "20m", 1700000050L);
        byte[] dg = Ipfix.build(rx, List.of(a, b), 7, 42, 1700000200L);

        assertEquals(dg.length, u16(dg, 2));
        assertEquals(0, dg.length % 4);
        assertEquals(7L, u32(dg, 8));
    }
}
