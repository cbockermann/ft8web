package de.bockermann.ft8monitor.pskreporter;

import de.bockermann.ft8monitor.model.Decode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotExtractorTest {

    private static Decode decode(String msg, Map<String, String> tags) {
        return new Decode("0@0", null, 1700000000L, -12, 0.1, 1500.0, 174, msg, tags);
    }

    private static Map<String, String> withRf(String hz, String band) {
        return Map.of("rf_hz", hz, "band", band);
    }

    @Test
    void extractsCqSenderAndGrid() {
        Optional<Spot> spot = SpotExtractor.extract(
                decode("CQ DX AB1HL FN42", withRf("7074000", "40m")), "");
        assertTrue(spot.isPresent());
        assertEquals("AB1HL", spot.get().call());
        assertEquals("FN42", spot.get().grid());
        assertEquals(7074000L, spot.get().freqHz());
        assertEquals(-12, spot.get().snr());
        assertEquals("40m", spot.get().band());
    }

    @Test
    void directedSenderIsSecondCall() {
        // CALL1 CALL2 <exchange>: CALL2 (DL1ABC) is the sender.
        Optional<Spot> spot = SpotExtractor.extract(
                decode("AB1HL DL1ABC R-09", withRf("7074000", "40m")), "");
        assertTrue(spot.isPresent());
        assertEquals("DL1ABC", spot.get().call());
        assertEquals("", spot.get().grid()); // report, no grid
    }

    @Test
    void skipsWithoutRfHz() {
        assertTrue(SpotExtractor.extract(
                decode("CQ AB1HL FN42", Map.of("band", "40m")), "").isEmpty());
    }

    @Test
    void skipsI3MisDecodes() {
        assertTrue(SpotExtractor.extract(
                decode("i3=5.0 n3=7 AB1HL", withRf("7074000", "40m")), "").isEmpty());
    }

    @Test
    void skipsSignoffAndReportOnlyGarbage() {
        assertTrue(SpotExtractor.extract(decode("TNX 73 GL", withRf("7074000", "40m")), "").isEmpty());
        assertTrue(SpotExtractor.extract(decode("RR73", withRf("7074000", "40m")), "").isEmpty());
    }

    @Test
    void skipsOwnTransmissions() {
        assertTrue(SpotExtractor.extract(
                decode("CQ DL0ABC JO62", withRf("7074000", "40m")), "dl0abc").isEmpty());
    }

    @Test
    void skipsImplausiblyLongCall() {
        // A 13-char token is not a real callsign.
        assertTrue(SpotExtractor.extract(
                decode("CQ AB1CDEFGH2IJK JO62", withRf("7074000", "40m")), "").isEmpty());
    }

    @Test
    void parsesDecimalRfHz() {
        Optional<Spot> spot = SpotExtractor.extract(
                decode("CQ AB1HL FN42", withRf("7074000.0", "40m")), "");
        assertTrue(spot.isPresent());
        assertEquals(7074000L, spot.get().freqHz());
    }
}
