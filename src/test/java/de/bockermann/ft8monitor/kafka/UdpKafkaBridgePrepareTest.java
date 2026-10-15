package de.bockermann.ft8monitor.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bockermann.ft8monitor.kafka.UdpKafkaBridge.Prepared;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdpKafkaBridgePrepareTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void noKeyNoValidatePassesThroughWithoutParsing() {
        // Even invalid JSON is forwarded (Python: no --key, no --validate).
        Prepared p = UdpKafkaBridge.prepare(MAPPER, "not json", null, false);
        assertFalse(p.skip());
        assertNull(p.key());
    }

    @Test
    void extractsKeyField() {
        Prepared p = UdpKafkaBridge.prepare(MAPPER,
                "{\"msg\":\"CQ AB1HL\",\"band\":\"40m\"}", "band", false);
        assertFalse(p.skip());
        assertEquals("40m", p.key());
    }

    @Test
    void missingKeyFieldSendsWithoutKey() {
        Prepared p = UdpKafkaBridge.prepare(MAPPER,
                "{\"msg\":\"CQ AB1HL\"}", "band", false);
        assertFalse(p.skip());
        assertNull(p.key());
    }

    @Test
    void invalidJsonSkippedWhenKeying() {
        Prepared p = UdpKafkaBridge.prepare(MAPPER, "not json", "band", false);
        assertTrue(p.skip());
    }

    @Test
    void invalidJsonSkippedWhenValidating() {
        Prepared p = UdpKafkaBridge.prepare(MAPPER, "not json", null, true);
        assertTrue(p.skip());
    }

    @Test
    void validateOnlyKeepsValidJsonWithoutKey() {
        Prepared p = UdpKafkaBridge.prepare(MAPPER, "{\"msg\":\"CQ AB1HL\"}", null, true);
        assertFalse(p.skip());
        assertNull(p.key());
    }
}
