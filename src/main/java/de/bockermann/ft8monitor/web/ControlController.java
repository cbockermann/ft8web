package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.rig.RigControl;
import de.bockermann.ft8monitor.wsjtx.ControlStatus;
import de.bockermann.ft8monitor.wsjtx.WsjtxBackend;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Control view API: live WSJT-X status + decodes over SSE, and the two write
 * actions the MVP needs — {@code reply} (answer a decode; WSJT-X then auto-
 * sequences the QSO) and {@code halt} (stop transmitting).
 */
@RestController
@RequestMapping("/api/control")
public class ControlController {

    private final WsjtxBackend backend;
    private final ControlBroadcaster broadcaster;
    private final RigControl rig;

    public ControlController(WsjtxBackend backend, ControlBroadcaster broadcaster, RigControl rig) {
        this.backend = backend;
        this.broadcaster = broadcaster;
        this.rig = rig;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        // Keep SSE intact through a reverse proxy (no buffering); heartbeats in
        // ControlBroadcaster handle idle timeouts.
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = broadcaster.subscribe();
        try {
            emitter.send(SseEmitter.event().name("status").data(backend.status()));
        } catch (IOException ignored) {
            // client went away before the first push; the broadcaster cleans up
        }
        return emitter;
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ControlStatus status() {
        return backend.status();
    }

    /** Ask WSJT-X to answer a decode (by the id from the decode stream). */
    @PostMapping(path = "/reply", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Boolean> reply(@RequestParam String id) {
        return Map.of("ok", backend.reply(id));
    }

    /** Stop transmitting now (auto=false) or after the current Tx (auto=true). */
    @PostMapping(path = "/halt", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Boolean> halt(@RequestParam(defaultValue = "false") boolean auto) {
        return Map.of("ok", backend.halt(auto));
    }

    /** Retune the rig to a band's dial frequency (Hz) + mode via rigctld (if enabled). */
    @PostMapping(path = "/frequency", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> frequency(@RequestParam long hz,
                                         @RequestParam(required = false) String mode) {
        RigControl.Result r = rig.tune(hz, mode);
        return Map.of("ok", r.ok(), "message", r.message());
    }
}
