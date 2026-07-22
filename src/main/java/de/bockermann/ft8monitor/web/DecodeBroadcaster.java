package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.model.Decode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans out live decodes to all connected Server-Sent-Events clients.
 */
@Component
public class DecodeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DecodeBroadcaster.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Register a new SSE subscriber. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; browser reconnects via EventSource
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            // Flush the response headers immediately so the browser's EventSource
            // fires 'onopen' right away instead of waiting for the first decode.
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Push a decode to every subscriber, dropping any that have gone away. */
    public void broadcast(Decode decode) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("decode").data(decode));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("Dropped dead SSE emitter: {}", e.getMessage());
            }
        }
    }

    public int subscriberCount() {
        return emitters.size();
    }
}
