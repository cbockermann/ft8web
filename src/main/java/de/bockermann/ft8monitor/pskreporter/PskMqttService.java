package de.bockermann.ft8monitor.pskreporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diagnostics: subscribes to PSKReporter's public MQTT gateway and streams the
 * live reports for a callsign to the browser via SSE. One shared MQTT connection
 * (TCP) is opened lazily; per-request topic subscriptions are reference-counted
 * so the broker is only asked for what viewers are actually watching.
 *
 * <p>Topics (v2): {@code pskr/filter/v2/{band}/{mode}/{sender}/{receiver}/#}.
 * For a callsign we subscribe with wildcards for band/mode and the call in the
 * sender and/or receiver slot. Payload is JSON with short keys (sc, rc, sl, rl,
 * rp, f, b, md, t), forwarded to the browser as-is.
 */
@Service
public class PskMqttService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(PskMqttService.class);

    /** Which side of the report the callsign is on. */
    public enum Role {
        SENDER, RECEIVER, BOTH;

        static Role of(String s) {
            try {
                return valueOf(s.trim().toUpperCase());
            } catch (Exception e) {
                return BOTH;
            }
        }
    }

    private record Sub(String call, Role role, Set<String> topics, SseEmitter emitter) {
    }

    private final String brokerUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();
    private final Map<SseEmitter, Sub> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Integer> topicRefs = new HashMap<>(); // guarded by lock

    private MqttClient client;

    public PskMqttService(@Value("${pskreporter.mqtt-host:mqtt.pskreporter.info}") String host,
                          @Value("${pskreporter.mqtt-port:1883}") int port) {
        this.brokerUrl = "tcp://" + host + ":" + port;
    }

    /** Subscribe to the live reports for a callsign; returns the SSE stream. */
    public SseEmitter subscribe(String callsign, String roleStr) {
        String call = callsign == null ? "" : callsign.trim().toUpperCase();
        if (call.isEmpty()) {
            throw new IllegalArgumentException("callsign required");
        }
        Role role = Role.of(roleStr);
        Set<String> topics = topicsFor(call, role);

        SseEmitter emitter = new SseEmitter(0L); // no timeout; browser closes it
        Sub sub = new Sub(call, role, topics, emitter);
        emitter.onCompletion(() -> remove(emitter));
        emitter.onTimeout(() -> remove(emitter));
        emitter.onError(e -> remove(emitter));

        try {
            ensureConnected();
            synchronized (lock) {
                for (String t : topics) {
                    if (topicRefs.merge(t, 1, Integer::sum) == 1) {
                        client.subscribe(t, 0);
                    }
                }
            }
            subscribers.put(emitter, sub);
            emitter.send(SseEmitter.event().name("subscribed").data(call));
            log.info("MQTT diagnostics: watching {} ({})", call, role);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    static Set<String> topicsFor(String call, Role role) {
        String c = call.replace('/', '.'); // '/' is the topic separator
        Set<String> topics = new LinkedHashSet<>();
        if (role == Role.SENDER || role == Role.BOTH) {
            topics.add("pskr/filter/v2/+/+/" + c + "/+/#");
        }
        if (role == Role.RECEIVER || role == Role.BOTH) {
            topics.add("pskr/filter/v2/+/+/+/" + c + "/#");
        }
        return topics;
    }

    private void ensureConnected() throws MqttException {
        synchronized (lock) {
            if (client != null && client.isConnected()) {
                return;
            }
            if (client == null) {
                client = new MqttClient(brokerUrl, "ft8web-" + UUID.randomUUID(), new MemoryPersistence());
                client.setCallback(this);
            }
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            opts.setConnectionTimeout(10);
            client.connect(opts);
            log.info("Connected to PSKReporter MQTT {}", brokerUrl);
        }
    }

    private void remove(SseEmitter emitter) {
        Sub sub = subscribers.remove(emitter);
        if (sub == null) {
            return;
        }
        synchronized (lock) {
            for (String t : sub.topics()) {
                Integer n = topicRefs.merge(t, -1, Integer::sum);
                if (n != null && n <= 0) {
                    topicRefs.remove(t);
                    try {
                        if (client != null && client.isConnected()) {
                            client.unsubscribe(t);
                        }
                    } catch (MqttException e) {
                        log.debug("Unsubscribe failed for {}: {}", t, e.getMessage());
                    }
                }
            }
        }
    }

    // --- MqttCallbackExtended ----------------------------------------------

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            synchronized (lock) {
                for (String t : topicRefs.keySet()) {
                    try {
                        client.subscribe(t, 0);
                    } catch (MqttException e) {
                        log.warn("Re-subscribe failed for {}: {}", t, e.getMessage());
                    }
                }
            }
            log.info("Reconnected to PSKReporter MQTT; re-subscribed {} topics", topicRefs.size());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String sc;
        String rc;
        try {
            JsonNode n = mapper.readTree(payload);
            sc = n.path("sc").asText("").toUpperCase();
            rc = n.path("rc").asText("").toUpperCase();
        } catch (Exception e) {
            return;
        }
        for (Sub sub : subscribers.values()) {
            boolean match = switch (sub.role()) {
                case SENDER -> sub.call().equals(sc);
                case RECEIVER -> sub.call().equals(rc);
                case BOTH -> sub.call().equals(sc) || sub.call().equals(rc);
            };
            if (match) {
                try {
                    sub.emitter().send(SseEmitter.event().name("report").data(payload));
                } catch (IOException | IllegalStateException e) {
                    remove(sub.emitter());
                }
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("PSKReporter MQTT connection lost: {}", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // not used (subscribe-only)
    }

    @PreDestroy
    void shutdown() {
        synchronized (lock) {
            if (client != null) {
                try {
                    client.disconnectForcibly(500);
                    client.close();
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }
}
