package de.bockermann.ft8monitor;

import de.bockermann.ft8monitor.config.Ft8Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(Ft8Properties.class)
@EnableScheduling
public class Ft8MonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(Ft8MonitorApplication.class, args);
    }
}
