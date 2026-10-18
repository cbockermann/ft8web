package de.bockermann.ft8monitor.pskreporter;

import de.bockermann.ft8monitor.kafka.DecodeParser;
import de.bockermann.ft8monitor.model.Decode;
import de.bockermann.ft8monitor.settings.Settings;
import de.bockermann.ft8monitor.settings.SettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reports observed FT8 stations (spots) to the PSKReporter network as an
 * additional, switchable Kafka consumer (own consumer group, so it never
 * disturbs ingest/display). Fully driven by the web-editable settings: active
 * only when {@code psk.enabled} is set and the station callsign + locator are
 * configured; otherwise it consumes nothing and sends nothing.
 *
 * <p>Only decodes carrying a real RF frequency (rf_hz) are reportable; the
 * sender callsign must pass the mandatory plausibility filter, so i3= mis-decodes
 * and noise never reach PSKReporter. Spots are de-duplicated per (sender, band)
 * and flushed as one IPFIX datagram roughly every {@code intervalSeconds}
 * (≥ 120 s, with a small random jitter).
 */
@Component
public class PskReporterFeeder {

    private static final Logger log = LoggerFactory.getLogger(PskReporterFeeder.class);

    static final String LISTENER_ID = "pskreporter";
    /** Keep datagrams comfortably within a UDP payload; chunk spots if needed. */
    private static final int MAX_SPOTS_PER_DATAGRAM = 100;
    private static final int MAX_JITTER_SECONDS = 20;

    private final SettingsService settings;
    private final DecodeParser parser;
    private final KafkaListenerEndpointRegistry registry;
    private final SpotAggregator aggregator = new SpotAggregator();
    private final String software;

    private final AtomicInteger sequence = new AtomicInteger(0);
    private final int observationId = new Random().nextInt();
    private final Random jitter = new Random();
    private volatile long nextSendEpoch = 0;

    private DatagramSocket socket;

    public PskReporterFeeder(SettingsService settings, DecodeParser parser,
                             KafkaListenerEndpointRegistry registry,
                             @Value("${pskreporter.software:ft8web}") String software) {
        this.settings = settings;
        this.parser = parser;
        this.registry = registry;
        this.software = software;
    }

    @PostConstruct
    void start() {
        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            log.warn("PSKReporter: could not open UDP socket, feeder disabled: {}", e.getMessage());
        }
        if (active()) {
            log.info("PSKReporter feeder enabled for {} / {}",
                    settings.get().callsign(), settings.get().locator());
        } else if (settings.get().psk().enabled()) {
            log.info("PSKReporter enabled but callsign/locator not set — staying inactive");
        } else {
            log.info("PSKReporter feeder disabled");
        }
    }

    @PreDestroy
    void stop() {
        if (socket != null) {
            socket.close();
        }
    }

    @KafkaListener(
            id = LISTENER_ID,
            topics = "${ft8.topic}",
            groupId = "${pskreporter.kafka-group:pskreporter-${ft8.hostname}}",
            autoStartup = "false")
    public void onDecode(ConsumerRecord<String, String> record) {
        if (!active()) {
            return;
        }
        Decode decode = parser.parse(record.value(), record.partition() + "@" + record.offset());
        if (decode == null) {
            return;
        }
        SpotExtractor.extract(decode, settings.get().callsign()).ifPresent(aggregator::add);
    }

    /** Heartbeat: reconcile the listener with the current settings and flush when due. */
    @Scheduled(fixedDelayString = "${pskreporter.tick-ms:15000}")
    void tick() {
        reconcile();
        if (!active() || socket == null) {
            return;
        }
        long now = epoch();
        if (nextSendEpoch == 0) {
            scheduleNext(now);
            return;
        }
        if (now >= nextSendEpoch) {
            try {
                flushAndSend();
            } catch (Exception e) {
                log.warn("PSKReporter send failed: {}", e.getMessage());
            } finally {
                scheduleNext(now);
            }
        }
    }

    /** Start/stop the Kafka consumer to match the current enabled state. */
    private void reconcile() {
        MessageListenerContainer container = registry.getListenerContainer(LISTENER_ID);
        if (container == null) {
            return;
        }
        boolean active = active();
        if (active && !container.isRunning()) {
            container.start();
            log.info("PSKReporter feeder started (reporting to {}:{})",
                    settings.get().psk().host(), settings.get().psk().port());
        } else if (!active && container.isRunning()) {
            container.stop();
            aggregator.clear();
            nextSendEpoch = 0;
            log.info("PSKReporter feeder stopped");
        }
    }

    private void flushAndSend() throws Exception {
        List<Spot> spots = aggregator.drain();
        if (spots.isEmpty()) {
            return;
        }
        Settings s = settings.get();
        Ipfix.Receiver rx = new Ipfix.Receiver(s.callsign(), s.locator(), software, s.psk().antenna());
        String host = s.psk().host();
        int port = s.psk().port();
        InetAddress addr = InetAddress.getByName(host);

        for (int i = 0; i < spots.size(); i += MAX_SPOTS_PER_DATAGRAM) {
            List<Spot> chunk = spots.subList(i, Math.min(i + MAX_SPOTS_PER_DATAGRAM, spots.size()));
            int seq = sequence.incrementAndGet();
            byte[] datagram = Ipfix.build(rx, new ArrayList<>(chunk), seq, observationId, epoch());
            socket.send(new DatagramPacket(datagram, datagram.length, addr, port));
            log.info("PSKReporter: sent {} spots to {}:{} ({} bytes, seq {})",
                    chunk.size(), host, port, datagram.length, seq);
            if (log.isDebugEnabled()) {
                log.debug("PSKReporter IPFIX datagram: {}", hex(datagram));
            }
        }
    }

    private void scheduleNext(long now) {
        int interval = settings.get().psk().intervalSeconds();
        nextSendEpoch = now + interval + jitter.nextInt(MAX_JITTER_SECONDS + 1);
    }

    private boolean active() {
        Settings s = settings.get();
        Settings.Psk psk = s.psk();
        return psk != null && psk.enabled()
                && s.callsign() != null && !s.callsign().isBlank()
                && s.locator() != null && !s.locator().isBlank();
    }

    private static long epoch() {
        return Instant.now().getEpochSecond();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }
}
