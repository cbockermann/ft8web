package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.pskreporter.PskMqttService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Diagnostics: live PSKReporter reports for a callsign, streamed from the public
 * MQTT gateway via SSE. {@code GET /api/mqtt/reports?call=DL1ABC&role=both}.
 */
@RestController
@RequestMapping("/api/mqtt")
public class MqttReportController {

    private final PskMqttService mqtt;

    public MqttReportController(PskMqttService mqtt) {
        this.mqtt = mqtt;
    }

    @GetMapping(path = "/reports", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reports(@RequestParam String call,
                              @RequestParam(defaultValue = "both") String role) {
        return mqtt.subscribe(call, role);
    }
}
