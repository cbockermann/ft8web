package de.bockermann.ft8monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the FT8 monitor. Bound from the {@code ft8.*} namespace in
 * {@code application.yml}.
 *
 * @param topic             Kafka topic to consume decodes from.
 * @param windowSeconds     rolling in-memory retention window; decodes older
 *                          than this (relative to the newest seen) are evicted.
 * @param gapSeconds        max gap between consecutive messages of the same pair
 *                          before they are split into separate QSOs.
 * @param evictionIntervalMs how often (ms) the background eviction sweep runs.
 * @param bridge            optional bundled UDP→Kafka bridge (off by default).
 */
@ConfigurationProperties(prefix = "ft8")
public record Ft8Properties(
        String topic,
        long windowSeconds,
        long gapSeconds,
        long evictionIntervalMs,
        Bridge bridge
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
        if (bridge == null) {
            bridge = new Bridge(false, null, 0, null, false);
        }
    }

    /**
     * Bundled UDP→Kafka bridge: receives ft8mon's {@code -json host:port}
     * datagrams on a UDP port and produces each one to {@link #topic()}. A
     * drop-in replacement for ft8mon's {@code scripts/udp_kafka_bridge.py}.
     * Enable only on the host that receives ft8mon's UDP output.
     *
     * @param enabled   whether to run the bridge.
     * @param bind      address to bind (default {@code 0.0.0.0}).
     * @param port      UDP port to listen on (default {@code 5001}).
     * @param keyField  JSON field to use as the Kafka message key (e.g.
     *                  {@code band}), or {@code null}/blank for no key. Setting
     *                  it implies JSON validation.
     * @param validate  parse every datagram as JSON and skip invalid ones
     *                  (implied when {@code keyField} is set).
     */
    public record Bridge(
            boolean enabled,
            String bind,
            int port,
            String keyField,
            boolean validate
    ) {
        public Bridge {
            if (bind == null || bind.isBlank()) {
                bind = "0.0.0.0";
            }
            if (port < 0) {
                port = 5001;
            }
            if (keyField != null && keyField.isBlank()) {
                keyField = null;
            }
        }

        /** Whether datagrams must be parsed as JSON (to key or to validate). */
        public boolean parsesJson() {
            return validate || keyField != null;
        }
    }
}
