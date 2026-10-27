package de.bockermann.ft8monitor.qsolog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdifParserTest {

    private static final String ADIF = """
            Some header text
            <adif_ver:5>3.1.0
            <programid:6>WSJT-X
            <EOH>
            <call:6>DL1ABC <gridsquare:4>JO31 <mode:3>FT8 <rst_sent:3>-12 <rst_rcvd:3>-08 \
            <qso_date:8>20261027 <time_on:6>120000 <band:3>20m <freq:9>14.074123 \
            <station_callsign:6>DN9CBO <EOR>
            <call:4>W1AW <gridsquare:4>FN31 <mode:3>FT8 <qso_date:8>20261027 <time_on:6>121500 <band:3>40m <EOR>
            """;

    @Test
    void parsesRecordsSkippingTheHeader() {
        List<Map<String, String>> recs = AdifParser.parse(ADIF);
        assertEquals(2, recs.size());
        assertFalse(recs.get(0).containsKey("programid")); // header not mixed into a record
    }

    @Test
    void readsFieldsByDeclaredLength() {
        Map<String, String> r = AdifParser.parse(ADIF).get(0);
        assertEquals("DL1ABC", r.get("call"));
        assertEquals("JO31", r.get("gridsquare"));
        assertEquals("FT8", r.get("mode"));
        assertEquals("-12", r.get("rst_sent"));
        assertEquals("20261027", r.get("qso_date"));
        assertEquals("120000", r.get("time_on"));
        assertEquals("20m", r.get("band"));
        assertEquals("14.074123", r.get("freq"));
    }

    @Test
    void emptyOrNullInputYieldsNoRecords() {
        assertEquals(0, AdifParser.parse(null).size());
        assertEquals(0, AdifParser.parse("").size());
    }
}
