package de.bockermann.ft8monitor.overview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaidenheadTest {

    @Test
    void extractsFieldFromCq() {
        assertEquals("FN", Maidenhead.fieldOf("CQ DX AB1HL FN42"));
    }

    @Test
    void extractsFieldFromGridExchange() {
        assertEquals("JN", Maidenhead.fieldOf("AB1HL DL1ABC JN49"));
    }

    @Test
    void extractsSquare() {
        assertEquals("FN42", Maidenhead.squareOf("CQ DX AB1HL FN42"));
        assertEquals("JN49", Maidenhead.squareOf("AB1HL DL1ABC JN49"));
        assertNull(Maidenhead.squareOf("AB1HL DL1ABC R-12"));
    }

    @Test
    void fieldOfSquare() {
        assertEquals("FN", Maidenhead.fieldOfSquare("FN42"));
    }

    @Test
    void nullWhenNoGrid() {
        assertNull(Maidenhead.fieldOf("AB1HL DL1ABC R-12"));
        assertNull(Maidenhead.fieldOf("DL1ABC AB1HL RR73"));
        assertNull(Maidenhead.fieldOf("CQ AB1HL"));
    }

    @Test
    void callsignIsNotAGrid() {
        // AB1HL looks grid-ish but isn't a valid locator token.
        assertNull(Maidenhead.fieldOf("AB1HL"));
    }
}
