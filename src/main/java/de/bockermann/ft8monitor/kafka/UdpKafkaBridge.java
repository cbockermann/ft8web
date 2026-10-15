package de.bockermann.ft8monitor.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bockermann.ft8monitor.config.Ft8Properties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Bundled UDP→Kafka bridge: listens for ft8mon's {@code -json host:port}
 * datagrams (one JSON decode per datagram) and produces each payload to the
 * configured Kafka topic. A drop-in replacement for ft8mon's
 * {@code scripts/udp_kafka_bridge.py}, so the monitor host can run a single
 * process instead of a separate Python bridge.
 *
 * <p>Created only when {@code ft8.bridge.enabled=true}; otherwise no UDP port is
 * opened. Enable it on the host that receives ft8mon's UDP output.
 */
@Component
@ConditionalOnProperty(prefix = "ft8.bridge", name = "enabled", havingValue = "true")
public class UdpKafkaBridge {

    private static final Logger log = LoggerFactory.getLogger(UdpKafkaBridge.class);

    /** Max UDP payload; one decode's JSON comfortably fits. */
    private static final int MAX_DATAGRAM = 65535;

    private final Ft8Properties props;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    private volatile boolean running;
    private DatagramSocket socket;
    private Thread thread;

    public UdpKafkaBridge(Ft8Properties props, KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.props = props;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @PostConstruct
    void start() throws SocketException {
        Ft8Properties.Bridge cfg = props.bridge();
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(cfg.bind(), cfg.port()));
        running = true;
        thread = new Thread(this::receiveLoop, "udp-kafka-bridge");
        thread.setDaemon(true);
        thread.start();
        log.info("UDP→Kafka bridge listening on {}:{}, producing to '{}'{}",
                cfg.bind(), boundPort(), props.topic(),
                cfg.keyField() != null ? ", key='" + cfg.keyField() + "'" : "");
    }

    @PreDestroy
    void stop() {
        running = false;
        if (socket != null) {
            socket.close(); // unblocks receive()
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            kafka.flush(); // deliver buffered datagrams before exit
        } catch (Exception e) {
            log.warn("Flush on shutdown failed: {}", e.getMessage());
        }
        log.info("UDP→Kafka bridge stopped");
    }

    /** The actual bound port (useful when configured with port 0 in tests). */
    public int boundPort() {
        return socket != null ? socket.getLocalPort() : -1;
    }

    private void receiveLoop() {
        byte[] buf = new byte[MAX_DATAGRAM];
        Ft8Properties.Bridge cfg = props.bridge();
        while (running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (running) {
                    log.warn("UDP receive failed: {}", e.getMessage());
                }
                continue; // running flipped to false on shutdown → loop exits
            }
            String text = new String(packet.getData(), packet.getOffset(),
                    packet.getLength(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                continue;
            }

            Prepared prepared = prepare(mapper, text, cfg.keyField(), cfg.validate());
            if (prepared.skip()) {
                log.debug("Skipping datagram from {}: invalid JSON",
                        packet.getSocketAddress());
                continue;
            }
            kafka.send(props.topic(), prepared.key(), text);
        }
    }

    /** Outcome of preparing a datagram for Kafka: whether to skip, and the key. */
    record Prepared(boolean skip, String key) {
        static final Prepared SKIP = new Prepared(true, null);

        static Prepared withKey(String key) {
            return new Prepared(false, key);
        }
    }

    /**
     * Decide how to produce a datagram, mirroring the Python bridge:
     * <ul>
     *   <li>no keyField and no validation → send as-is, no key;</li>
     *   <li>otherwise parse the JSON: invalid → skip;</li>
     *   <li>keyField present but missing/null in the payload → send without a key.</li>
     * </ul>
     */
    static Prepared prepare(ObjectMapper mapper, String text, String keyField, boolean validate) {
        if (keyField == null && !validate) {
            return Prepared.withKey(null);
        }
        JsonNode node;
        try {
            node = mapper.readTree(text);
        } catch (Exception e) {
            return Prepared.SKIP;
        }
        if (keyField == null) {
            return Prepared.withKey(null); // validation only
        }
        JsonNode value = node.get(keyField);
        String key = (value != null && !value.isNull()) ? value.asText() : null;
        return Prepared.withKey(key);
    }
}
