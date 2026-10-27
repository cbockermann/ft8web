package de.bockermann.ft8monitor.wsjtx;

import de.bockermann.ft8monitor.web.ControlBroadcaster;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MVP transmit backend: talks to a locally running <b>WSJT-X</b> over its UDP
 * Message Protocol. Read side — receives Status/Decode/Heartbeat and relays them
 * to the browser (Control view) via {@link ControlBroadcaster}. Write side —
 * {@link #reply(String)} tells WSJT-X to answer a decode (its auto-sequencer then
 * runs the QSO), {@link #halt(boolean)} stops transmitting.
 *
 * <p>ft8web only speaks UDP here; WSJT-X keeps exclusive CAT/audio ownership, so
 * there is no hardware contention. This is the {@code StationBackend} MVP; a
 * future ft8_lib + Hamlib daemon can replace it behind the same control API.
 */
@Service
public class WsjtxBackend {

    private static final Logger log = LoggerFactory.getLogger(WsjtxBackend.class);

    /** Mark WSJT-X "down" if no datagram arrives for this long (it beats ~15s). */
    private static final long STALE_SECONDS = 30;
    private static final int MAX_RECENT = 500;

    private final boolean enabled;
    private final String bind;
    private final int port;
    private final ControlBroadcaster broadcaster;

    private volatile DatagramSocket socket;
    private volatile Thread receiver;
    private volatile String wsjtxId;        // instance id to echo in commands
    private volatile SocketAddress peer;     // where to send commands back
    private volatile long lastRxEpoch;
    private volatile boolean markedDown = true;
    private volatile boolean lastTransmitting = false;
    private volatile ControlStatus latest = ControlStatus.down();

    // Recent decodes, keyed by a synthetic id, so a reply can echo the exact fields.
    private final Map<String, WsjtxCodec.Decode> recent = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WsjtxCodec.Decode> e) {
            return size() > MAX_RECENT;
        }
    };

    public WsjtxBackend(@Value("${ft8.wsjtx.enabled:true}") boolean enabled,
                        @Value("${ft8.wsjtx.bind:0.0.0.0}") String bind,
                        @Value("${ft8.wsjtx.port:2237}") int port,
                        ControlBroadcaster broadcaster) {
        this.enabled = enabled;
        this.bind = bind;
        this.port = port;
        this.broadcaster = broadcaster;
    }

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info("WSJT-X backend disabled (ft8.wsjtx.enabled=false)");
            return;
        }
        try {
            socket = new DatagramSocket(new InetSocketAddress(bind, port));
        } catch (Exception e) {
            log.warn("WSJT-X backend: cannot bind udp/{} ({}). Is another UDP consumer "
                    + "(GridTracker/JTAlert) using it? Set ft8.wsjtx.port.", port, e.getMessage());
            return;
        }
        receiver = new Thread(this::receiveLoop, "wsjtx-udp");
        receiver.setDaemon(true);
        receiver.start();
        log.info("WSJT-X backend listening on udp/{} (point WSJT-X 'UDP Server' here)", port);
    }

    @PreDestroy
    void stop() {
        DatagramSocket s = socket;
        if (s != null) {
            s.close();
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[65535];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (Exception e) {
                if (!socket.isClosed()) {
                    log.debug("WSJT-X receive error: {}", e.getMessage());
                }
                return;
            }
            WsjtxCodec.In msg = WsjtxCodec.parse(packet.getData(), packet.getLength());
            if (msg == null) {
                continue;
            }
            peer = packet.getSocketAddress();
            wsjtxId = msg.id();
            lastRxEpoch = Instant.now().getEpochSecond();
            if (markedDown) {
                markedDown = false;
            }
            handle(msg);
        }
    }

    private void handle(WsjtxCodec.In msg) {
        switch (msg) {
            case WsjtxCodec.Status s -> {
                latest = ControlStatus.of(s);
                broadcaster.sendStatus(latest);
                // Rising edge of 'transmitting' = we just started sending txMessage.
                if (s.transmitting() && !lastTransmitting) {
                    String txMsg = s.txMessage() == null ? "" : s.txMessage().trim();
                    if (!txMsg.isEmpty()) {
                        broadcaster.sendDecode(
                                ControlDecode.transmit("tx-" + System.nanoTime(), txMsg, s.dialHz(), s.txDf()));
                    }
                }
                lastTransmitting = s.transmitting();
            }
            case WsjtxCodec.Decode d -> {
                String id = idFor(d);
                synchronized (recent) {
                    recent.put(id, d);
                }
                // Tag with the band from the dial frequency current at decode time.
                broadcaster.sendDecode(ControlDecode.of(id, d, latest.dialHz()));
            }
            case WsjtxCodec.LoggedAdif a -> log.info("WSJT-X logged QSO (ADIF): {}", a.adif());
            case WsjtxCodec.Closed ignored -> {
                markedDown = true;
                latest = ControlStatus.down();
                broadcaster.sendStatus(latest);
            }
            default -> {
                // Heartbeat / Unknown: liveness already updated above.
            }
        }
    }

    /** Ask WSJT-X to answer a previously seen decode. Returns false if unknown or offline. */
    public boolean reply(String decodeId) {
        WsjtxCodec.Decode d;
        synchronized (recent) {
            d = recent.get(decodeId);
        }
        if (d == null) {
            return false;
        }
        byte[] msg = WsjtxCodec.reply(wsjtxId, d.timeMs(), d.snr(), d.dt(), d.dfHz(),
                d.mode(), d.message(), d.lowConfidence());
        boolean sent = send(msg);
        if (sent) {
            log.info("WSJT-X reply → {}", d.message());
        }
        return sent;
    }

    /** Stop transmitting. {@code auto=false} halts now, {@code true} after this Tx. */
    public boolean halt(boolean auto) {
        boolean sent = send(WsjtxCodec.halt(wsjtxId, auto));
        if (sent) {
            log.info("WSJT-X halt tx (auto={})", auto);
        }
        return sent;
    }

    public ControlStatus status() {
        return isStale() ? ControlStatus.down() : latest;
    }

    private boolean send(byte[] data) {
        DatagramSocket s = socket;
        SocketAddress to = peer;
        if (s == null || to == null || wsjtxId == null) {
            return false; // no WSJT-X seen yet
        }
        try {
            s.send(new DatagramPacket(data, data.length, to));
            return true;
        } catch (Exception e) {
            log.warn("WSJT-X send failed: {}", e.getMessage());
            return false;
        }
    }

    /** Watchdog: if WSJT-X goes silent, flip the Control view to "disconnected". */
    @Scheduled(fixedDelayString = "${ft8.wsjtx.watchdog-ms:10000}")
    void watchdog() {
        if (!markedDown && isStale()) {
            markedDown = true;
            latest = ControlStatus.down();
            broadcaster.sendStatus(latest);
            log.info("WSJT-X went silent (> {}s) — marked disconnected", STALE_SECONDS);
        }
    }

    private boolean isStale() {
        return lastRxEpoch == 0 || Instant.now().getEpochSecond() - lastRxEpoch > STALE_SECONDS;
    }

    private static String idFor(WsjtxCodec.Decode d) {
        int h = d.message() == null ? 0 : d.message().hashCode();
        return d.timeMs() + "_" + d.dfHz() + "_" + Integer.toHexString(h);
    }
}
