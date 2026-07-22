package de.bockermann.ft8monitor.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bockermann.ft8monitor.model.Decode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns a raw Kafka JSON value into a {@link Decode}. Coded defensively: every
 * field except {@code msg} may be missing. Any keys beyond the known schema are
 * collected as tags (e.g. {@code band}, {@code site}).
 */
@Component
public class DecodeParser {

    /** Known top-level schema keys; everything else becomes a tag. */
    private static final Set<String> KNOWN = Set.of(
            "time", "unix", "snr", "dt", "freq", "correct_bits", "msg");

    private final ObjectMapper mapper;

    public DecodeParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parse the JSON payload of a decode.
     *
     * @param json the Kafka message value.
     * @param id   stable id to assign ({@code partition@offset}).
     * @return the parsed decode, or {@code null} if the payload is unusable.
     */
    public Decode parse(String json, String id) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }

            String msg = text(root, "msg");
            if (msg == null || msg.isBlank()) {
                return null;
            }

            long unix = root.has("unix") && root.get("unix").isNumber()
                    ? root.get("unix").asLong()
                    : parseUnixFromTime(root);

            Instant time = null;
            if (root.hasNonNull("time")) {
                try {
                    time = Instant.parse(root.get("time").asText());
                } catch (Exception ignored) {
                    // leave time null if unparseable
                }
            } else if (unix > 0) {
                time = Instant.ofEpochSecond(unix);
            }

            Map<String, String> tags = new LinkedHashMap<>();
            Iterator<String> names = root.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!KNOWN.contains(name)) {
                    tags.put(name, root.get(name).asText());
                }
            }

            return new Decode(
                    id,
                    time,
                    unix,
                    intOrNull(root, "snr"),
                    doubleOrNull(root, "dt"),
                    doubleOrNull(root, "freq"),
                    intOrNull(root, "correct_bits"),
                    msg,
                    tags);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseUnixFromTime(JsonNode root) {
        if (root.hasNonNull("time")) {
            try {
                return Instant.parse(root.get("time").asText()).getEpochSecond();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return 0L;
    }

    private static String text(JsonNode root, String field) {
        return root.hasNonNull(field) ? root.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode root, String field) {
        return root.hasNonNull(field) && root.get(field).isNumber() ? root.get(field).asInt() : null;
    }

    private static Double doubleOrNull(JsonNode root, String field) {
        return root.hasNonNull(field) && root.get(field).isNumber() ? root.get(field).asDouble() : null;
    }
}
