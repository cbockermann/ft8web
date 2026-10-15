package de.bockermann.ft8monitor.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end: a UDP datagram sent to the bundled bridge must land on the Kafka
 * topic with the configured field as its key.
 */
@SpringBootTest(properties = {
        "ft8.bridge.enabled=true",
        "ft8.bridge.port=0",        // ephemeral; read back via boundPort()
        "ft8.bridge.key-field=band"
})
@EmbeddedKafka(partitions = 1, topics = "ft8.decodes")
class UdpKafkaBridgeIntegrationTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    UdpKafkaBridge bridge;

    @Test
    void forwardsUdpDatagramToKafkaWithKey() throws Exception {
        String json = "{\"msg\":\"CQ DX AB1HL FN42\",\"unix\":1784671470,\"band\":\"40m\"}";

        // Consumer on its own group so it sees the record alongside the app.
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "bridge-it", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(java.util.List.of("ft8.decodes"));
            consumer.poll(Duration.ofMillis(200)); // join + assignment

            // Fire the datagram at the bridge's bound UDP port.
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            try (DatagramSocket sender = new DatagramSocket()) {
                sender.send(new DatagramPacket(payload, payload.length,
                        InetAddress.getLoopbackAddress(), bridge.boundPort()));
            }

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "ft8.decodes", Duration.ofSeconds(10));
            assertNotNull(record);
            assertEquals(json, record.value());
            assertEquals("40m", record.key());
        }
    }
}
