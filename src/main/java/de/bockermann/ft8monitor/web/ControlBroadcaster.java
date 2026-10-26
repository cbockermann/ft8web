package de.bockermann.ft8monitor.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans out WSJT-X {@code status} and {@code decode} events to the Control view
 * over SSE. Same idle-heartbeat hardening as the decode feed so the stream
 * survives a reverse proxy.
 */
@Component
public class ControlBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ControlBroadcaster.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void sendStatus(Object status) {
        broadcast("status", status);
    }

    public void sendDecode(Object decode) {
        broadcast("decode", decode);
    }

    private void broadcast(String event, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("Dropped dead control SSE emitter: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedRateString = "${ft8.sse-heartbeat-ms:15000}")
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("hb"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
