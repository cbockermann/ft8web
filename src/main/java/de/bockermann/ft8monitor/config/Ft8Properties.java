package de.bockermann.ft8monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the FT8 monitor. Bound from the {@code ft8.*} namespace in
 * {@code application.yml}.
 *
 * @param topic            Kafka topic to consume decodes from.
 * @param windowSeconds    rolling in-memory retention window; decodes older than
 *                         this (relative to the newest seen) are evicted.
 * @param gapSeconds       max gap between consecutive messages of the same pair
 *                         before they are split into separate QSOs.
 * @param evictionInterval how often (ms) the background eviction sweep runs.
 */
@ConfigurationProperties(prefix = "ft8")
public record Ft8Properties(
        String topic,
        long windowSeconds,
        long gapSeconds,
        long evictionIntervalMs
) {
    public Ft8Properties {
        if (topic == null || topic.isBlank()) {
            topic = "ft8.decodes";
        }
        if (windowSeconds <= 0) {
            windowSeconds = 3600;
        }
        if (gapSeconds <= 0) {
            gapSeconds = 180;
        }
        if (evictionIntervalMs <= 0) {
            evictionIntervalMs = 60_000;
        }
    }
}
