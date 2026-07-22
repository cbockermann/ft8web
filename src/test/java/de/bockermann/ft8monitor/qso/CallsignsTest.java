package de.bockermann.ft8monitor.qso;

import de.bockermann.ft8monitor.qso.Callsigns.Stations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallsignsTest {

    @Test
    void detectsCallsigns() {
        assertTrue(Callsigns.isCallsign("AB1HL"));
        assertTrue(Callsigns.isCallsign("DL1ABC"));
        assertTrue(Callsigns.isCallsign("<AB1HL>"));   // hash brackets stripped
        assertTrue(Callsigns.isCallsign("F/DL1ABC"));  // portable prefix
    }

    @Test
    void rejectsNonCallsigns() {
        assertFalse(Callsigns.isCallsign("FN42"));   // grid
        assertFalse(Callsigns.isCallsign("RR73"));   // sign-off
        assertFalse(Callsigns.isCallsign("73"));     // sign-off / no letter
        assertFalse(Callsigns.isCallsign("-12"));    // report
        assertFalse(Callsigns.isCallsign("R-09"));   // report
        assertFalse(Callsigns.isCallsign("CQ"));     // directive
        assertFalse(Callsigns.isCallsign("DX"));     // directive, no digit
    }

    @Test
    void parsesCqCaller() {
        Stations st = Callsigns.parse("CQ DX AB1HL FN42");
        assertInstanceOf(Stations.Cq.class, st);
        assertEquals("AB1HL", ((Stations.Cq) st).caller());
    }

    @Test
    void parsesDirectedPair() {
        Stations st = Callsigns.parse("AB1HL DL1ABC -12");
        assertInstanceOf(Stations.Directed.class, st);
        Stations.Directed d = (Stations.Directed) st;
        assertEquals("AB1HL", d.a());
        assertEquals("DL1ABC", d.b());
    }

    @Test
    void ungroupableMessage() {
        assertInstanceOf(Stations.None.class, Callsigns.parse("RR73"));
    }
}
