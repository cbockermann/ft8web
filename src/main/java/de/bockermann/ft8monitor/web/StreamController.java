package de.bockermann.ft8monitor.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live decode feed via Server-Sent Events. The browser subscribes with
 * {@code new EventSource('/api/stream')} and receives one {@code decode} event
 * per incoming FT8 decode.
 */
@RestController
@RequestMapping("/api")
public class StreamController {

    private final DecodeBroadcaster broadcaster;

    public StreamController(DecodeBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
