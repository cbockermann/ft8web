package de.bockermann.ft8monitor.pskreporter;

import de.bockermann.ft8monitor.pskreporter.PskMqttService.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PskMqttServiceTest {

    @Test
    void senderTopic() {
        assertEquals(Set.of("pskr/filter/v2/+/+/DL1ABC/+/#"),
                PskMqttService.topicsFor("DL1ABC", Role.SENDER));
    }

    @Test
    void receiverTopic() {
        assertEquals(Set.of("pskr/filter/v2/+/+/+/DL1ABC/#"),
                PskMqttService.topicsFor("DL1ABC", Role.RECEIVER));
    }

    @Test
    void bothTopics() {
        Set<String> t = PskMqttService.topicsFor("DL1ABC", Role.BOTH);
        assertTrue(t.contains("pskr/filter/v2/+/+/DL1ABC/+/#"));
        assertTrue(t.contains("pskr/filter/v2/+/+/+/DL1ABC/#"));
        assertEquals(2, t.size());
    }

    @Test
    void slashBecomesDot() {
        // Compound calls: '/' is the MQTT topic separator, so it is sent as '.'.
        assertEquals(Set.of("pskr/filter/v2/+/+/F.DL1ABC/+/#"),
                PskMqttService.topicsFor("F/DL1ABC", Role.SENDER));
    }

    @Test
    void roleParsingDefaultsToBoth() {
        assertEquals(Role.SENDER, Role.of("sender"));
        assertEquals(Role.RECEIVER, Role.of("RECEIVER"));
        assertEquals(Role.BOTH, Role.of("nonsense"));
    }
}
