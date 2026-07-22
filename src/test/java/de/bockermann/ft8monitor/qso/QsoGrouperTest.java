package de.bockermann.ft8monitor.qso;

import de.bockermann.ft8monitor.config.Ft8Properties;
import de.bockermann.ft8monitor.model.Decode;
import de.bockermann.ft8monitor.model.Qsl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QsoGrouperTest {

    private static final Ft8Properties PROPS = new Ft8Properties("ft8.decodes", 3600, 180, 60000);

    private static Decode decode(String id, long unix, String msg) {
        return new Decode(id, null, unix, -10, 0.1, 1000.0, 174, msg, Map.of());
    }

    @Test
    void groupsCqAndDirectedIntoOneQso() {
        QsoGrouper g = new QsoGrouper(PROPS);
        g.add(decode("0@0", 1000, "CQ DX AB1HL FN42"));
        g.add(decode("0@1", 1015, "AB1HL DL1ABC FN42"));
        g.add(decode("0@2", 1030, "DL1ABC AB1HL -12"));
        g.add(decode("0@3", 1045, "AB1HL DL1ABC R-08"));
        g.add(decode("0@4", 1060, "DL1ABC AB1HL RR73"));

        Qsl qsl = g.qslFor("0@2");
        assertNotNull(qsl);
        assertEquals("AB1HL", qsl.callA());
        assertEquals("DL1ABC", qsl.callB());
        // Opening CQ is attached, so all 5 messages belong to the QSO.
        assertEquals(5, qsl.messages().size());
        assertEquals("0@0", qsl.messages().get(0).id()); // CQ first
    }

    @Test
    void splitsSamePairOnLargeGap() {
        QsoGrouper g = new QsoGrouper(PROPS);
        g.add(decode("0@0", 1000, "AB1HL DL1ABC -12"));
        g.add(decode("0@1", 1015, "DL1ABC AB1HL R-08"));
        // > 180s later (but still inside the window): a new QSO of the same pair.
        g.add(decode("0@2", 1300, "AB1HL DL1ABC -05"));
        g.add(decode("0@3", 1315, "DL1ABC AB1HL RR73"));

        Qsl first = g.qslFor("0@0");
        Qsl second = g.qslFor("0@2");
        assertEquals(2, first.messages().size());
        assertEquals(2, second.messages().size());
        assertTrue(first.messages().stream().noneMatch(d -> d.id().equals("0@2")));
    }

    @Test
    void cqLookupResolvesToFollowingQso() {
        QsoGrouper g = new QsoGrouper(PROPS);
        g.add(decode("0@0", 1000, "CQ AB1HL FN42"));
        g.add(decode("0@1", 1015, "AB1HL DL1ABC FN42"));
        g.add(decode("0@2", 1030, "DL1ABC AB1HL -12"));

        Qsl qsl = g.qslFor("0@0"); // asked for the CQ itself
        assertNotNull(qsl);
        assertEquals(List.of("0@0", "0@1", "0@2"),
                qsl.messages().stream().map(Decode::id).toList());
    }

    @Test
    void evictsOutsideWindow() {
        Ft8Properties shortWindow = new Ft8Properties("ft8.decodes", 100, 180, 60000);
        QsoGrouper g = new QsoGrouper(shortWindow);
        g.add(decode("0@0", 1000, "AB1HL DL1ABC -12"));
        g.add(decode("0@1", 2000, "DL1ABC AB1HL R-08")); // advances horizon past the first

        assertTrue(g.qslFor("0@0") == null, "old decode should be evicted");
        assertNotNull(g.qslFor("0@1"));
    }
}
