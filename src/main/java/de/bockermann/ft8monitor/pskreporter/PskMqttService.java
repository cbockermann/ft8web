package de.bockermann.ft8monitor.pskreporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bockermann.ft8monitor.settings.SettingsService;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
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

    private final SettingsService settings;
    private final PskReportStore store;

    // Standing subscription to the own callsign (as sender = "being heard"), kept
    // alive whenever a callsign is configured so its reports are always captured.
    private volatile String standingCall;
    private volatile String standingTopic;

    private MqttClient client;

    public PskMqttService(@Value("${pskreporter.mqtt-host:mqtt.pskreporter.info}") String host,
                          @Value("${pskreporter.mqtt-port:1883}") int port,
                          SettingsService settings, PskReportStore store) {
        this.brokerUrl = "tcp://" + host + ":" + port;
        this.settings = settings;
        this.store = store;
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
            for (String t : topics) {
                retainTopic(t);
            }
            subscribers.put(emitter, sub);
            emitter.send(SseEmitter.event().name("subscribed").data(call));
            log.info("MQTT diagnostics: watching {} ({})", call, role);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** Subscribe to a topic (ref-counted; only the first ref hits the broker). */
    private void retainTopic(String topic) throws MqttException {
        synchronized (lock) {
            if (topicRefs.merge(topic, 1, Integer::sum) == 1) {
                client.subscribe(topic, 0);
            }
        }
    }

    /** Release a topic ref; unsubscribe from the broker when the last ref drops. */
    private void releaseTopic(String topic) {
        synchronized (lock) {
            Integer n = topicRefs.merge(topic, -1, Integer::sum);
            if (n != null && n <= 0) {
                topicRefs.remove(topic);
                try {
                    if (client != null && client.isConnected()) {
                        client.unsubscribe(topic);
                    }
                } catch (MqttException e) {
                    log.debug("Unsubscribe failed for {}: {}", topic, e.getMessage());
                }
            }
        }
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
        for (String t : sub.topics()) {
            releaseTopic(t);
        }
    }

    /**
     * Keep a standing subscription to the configured own callsign (as sender) so
     * its reports are always captured and persisted, even with no browser open.
     * Reconciled on a heartbeat; re-points when the callsign changes.
     */
    @Scheduled(fixedDelayString = "${pskreporter.mqtt-standing-tick-ms:20000}")
    void reconcileStanding() {
        String call = normCall(settings.get().callsign());
        if (Objects.equals(call, standingCall)) {
            return;
        }
        // Drop the previous standing topic, if any.
        if (standingTopic != null) {
            releaseTopic(standingTopic);
            standingTopic = null;
        }
        standingCall = null;
        if (call == null) {
            log.info("No own callsign set — standing MQTT subscription cleared");
            return;
        }
        try {
            ensureConnected();
            String topic = topicsFor(call, Role.SENDER).iterator().next();
            retainTopic(topic);
            standingTopic = topic;
            standingCall = call;
            log.info("Standing MQTT subscription for own call {} (receivability)", call);
        } catch (Exception e) {
            // Leave standingCall null so the next tick retries.
            log.warn("Standing MQTT subscription for {} failed, will retry: {}", call, e.getMessage());
        }
    }

    private static String normCall(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim().toUpperCase();
        return v.isEmpty() ? null : v;
    }

    /**
     * Keep the per-browser report streams alive. PSKReporter delivers in bursts
     * (minutes apart), so without a heartbeat a reverse proxy's idle timeout would
     * close the SSE stream between bursts. EventSource ignores comment lines.
     */
    @Scheduled(fixedRateString = "${ft8.sse-heartbeat-ms:15000}")
    void heartbeat() {
        for (SseEmitter emitter : subscribers.keySet()) {
            try {
                emitter.send(SseEmitter.event().comment("hb"));
            } catch (IOException | IllegalStateException e) {
                remove(emitter);
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
        JsonNode n;
        try {
            n = mapper.readTree(payload);
        } catch (Exception e) {
            return;
        }
        String sc = n.path("sc").asText("").toUpperCase();
        String rc = n.path("rc").asText("").toUpperCase();

        // Persist reports of our own call being heard (the receivability history).
        String own = standingCall;
        if (own != null && own.equals(sc)) {
            try {
                store.save(
                        n.path("t").asLong(Instant.now().getEpochSecond()),
                        sc, rc,
                        n.path("rl").asText(""),
                        n.path("b").asText(""),
                        n.path("md").asText(""),
                        n.hasNonNull("rp") ? n.path("rp").asInt() : null,
                        n.hasNonNull("f") ? n.path("f").asDouble() : null);
            } catch (Exception e) {
                log.debug("Failed to store own-call report: {}", e.getMessage());
            }
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
