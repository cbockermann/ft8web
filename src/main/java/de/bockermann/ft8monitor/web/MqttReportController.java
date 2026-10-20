package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.pskreporter.PskMqttService;
import de.bockermann.ft8monitor.pskreporter.PskReportStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

/**
 * Diagnostics: live PSKReporter reports for a callsign, streamed from the public
 * MQTT gateway via SSE ({@code GET /api/mqtt/reports?call=DL1ABC&role=both}),
 * plus the persisted history of the own callsign's reports
 * ({@code GET /api/mqtt/stored?call=DL1ABC}) that seeds the receivability view.
 */
@RestController
@RequestMapping("/api/mqtt")
public class MqttReportController {

    private static final int MAX_STORED = 5000;

    private final PskMqttService mqtt;
    private final PskReportStore store;

    public MqttReportController(PskMqttService mqtt, PskReportStore store) {
        this.mqtt = mqtt;
        this.store = store;
    }

    @GetMapping(path = "/reports", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reports(@RequestParam String call,
                              @RequestParam(defaultValue = "both") String role) {
        return mqtt.subscribe(call, role);
    }

    /**
     * Stored own-call reports (newest first), shaped like the live payload so the
     * frontend can seed the list/map before live events arrive. Only the own
     * callsign has history; other calls return an empty list.
     */
    @GetMapping(path = "/stored", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PskReportStore.RxReport> stored(@RequestParam String call,
                                                @RequestParam(defaultValue = "720") int minutes,
                                                @RequestParam(defaultValue = "2000") int limit) {
        long since = Instant.now().getEpochSecond() - (long) Math.max(1, minutes) * 60;
        return store.recent(call, since, Math.min(Math.max(1, limit), MAX_STORED));
    }
}
