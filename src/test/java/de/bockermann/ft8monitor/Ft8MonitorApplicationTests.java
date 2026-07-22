package de.bockermann.ft8monitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "ft8.decodes")
class Ft8MonitorApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context (Kafka listener, controllers, grouper) wires up.
    }
}
