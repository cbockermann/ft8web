package de.bockermann.ft8monitor.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.InetAddress;
import java.util.Map;

/**
 * Publishes {@code ft8.hostname} into the environment very early in startup so
 * {@code application.yml} can use it as the default Kafka consumer group
 * ({@code ft8-monitor-${ft8.hostname}}). This gives each host its own consumer
 * group by default, so monitors on different systems don't compete for the
 * topic's partitions. Override explicitly with {@code KAFKA_GROUP}.
 *
 * <p>Registered via {@code META-INF/spring.factories}.
 */
public class HostnameEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(
                new MapPropertySource("ft8Hostname", Map.of("ft8.hostname", resolveHostname())));
    }

    /**
     * Best-effort local hostname, sanitized to characters safe for a Kafka
     * group id. Falls back through the common env vars to a DNS lookup, then to
     * {@code "unknown"}.
     */
    static String resolveHostname() {
        String host = firstNonBlank(System.getenv("HOSTNAME"), System.getenv("COMPUTERNAME"));
        if (isBlank(host)) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                // leave host null → "unknown" below
            }
        }
        if (isBlank(host)) {
            host = "unknown";
        }
        host = host.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        return host.isEmpty() ? "unknown" : host;
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
