package de.bockermann.ft8monitor.model;

import java.time.Instant;
import java.util.Map;

/**
 * A single FT8 decode as consumed from Kafka.
 *
 * <p>Fields beyond {@code msg}/{@code unix} may be absent for some sources, so
 * boxed types are used and {@code null} is a valid value. Any {@code -tag
 * key:value} fields ft8mon was started with (e.g. {@code band}, {@code site})
 * are collected into {@link #tags()}.
 *
 * @param id          stable id ({@code partition@offset}), used by the frontend
 *                    to request the QSL a message belongs to.
 * @param time        decode time (UTC), or {@code null} if not provided.
 * @param unix        decode time as unix epoch seconds; the primary ordering key.
 * @param snr         signal-to-noise ratio in dB, or {@code null}.
 * @param dt          time offset in seconds, or {@code null}.
 * @param freq        audio frequency in Hz, or {@code null}.
 * @param correctBits FEC correct-bit count, or {@code null}.
 * @param msg         the decoded FT8 message text.
 * @param tags        additional key/value tags (band, site, ...).
 */
public record Decode(
        String id,
        Instant time,
        long unix,
        Integer snr,
        Double dt,
        Double freq,
        Integer correctBits,
        String msg,
        Map<String, String> tags
) {
    public Decode {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
