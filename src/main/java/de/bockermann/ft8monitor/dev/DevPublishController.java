package de.bockermann.ft8monitor.dev;

import de.bockermann.ft8monitor.config.Ft8Properties;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Development-only helper to publish decodes onto the Kafka topic so the app
 * can be exercised end-to-end without the real ft8mon pipeline. Enabled only
 * under the {@code dev} Spring profile.
 *
 * <pre>
 *   curl -X POST localhost:8080/api/dev/publish \
 *        -H 'Content-Type: application/json' \
 *        -d '{"msg":"CQ DX AB1HL FN42","band":"40m","snr":-12,"freq":1083}'
 * </pre>
 *
 * If {@code unix}/{@code time} are omitted, the current time is filled in.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevPublishController {

    private final KafkaTemplate<String, String> kafka;
    private final Ft8Properties props;

    public DevPublishController(KafkaTemplate<String, String> kafka, Ft8Properties props) {
        this.kafka = kafka;
        this.props = props;
    }

    @PostMapping("/publish")
    public String publish(@RequestBody(required = false) String body) {
        long now = Instant.now().getEpochSecond();
        String json = body;
        if (json == null || json.isBlank()) {
            json = "{\"msg\":\"CQ DX AB1HL FN42\"}";
        }
        // Fill in unix/time if the caller left them out (naive, dev-only).
        if (!json.contains("\"unix\"")) {
            json = json.replaceFirst("\\{",
                    "{\"unix\":" + now + ",\"time\":\"" + Instant.ofEpochSecond(now) + "\",");
        }
        String key = "dev";
        kafka.send(props.topic(), key, json);
        return "published: " + json;
    }
}
