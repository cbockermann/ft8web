package de.bockermann.ft8monitor.geo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * Maps a callsign to its country's ISO code via a prefix table derived from
 * ft8mon's upstream world of DXCC prefixes (cty.dat), resolved to ISO codes.
 * Lookup is longest-prefix-wins, so exact-call overrides and portable prefixes
 * (e.g. {@code F/DL1ABC} → France) work too.
 */
@Component
public class CallsignFlags {

    private static final Logger log = LoggerFactory.getLogger(CallsignFlags.class);

    private Map<String, String> prefixToCc = Map.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("callsign-prefixes.json").getInputStream()) {
            prefixToCc = new ObjectMapper().readValue(in, new TypeReference<Map<String, String>>() {
            });
            log.info("Loaded {} callsign prefixes for country flags", prefixToCc.size());
        } catch (Exception e) {
            log.warn("Could not load callsign prefixes; callsign flags disabled: {}", e.getMessage());
        }
    }

    /** ISO alpha-2 country code for a callsign (longest matching prefix), or "". */
    public String countryCode(String callsign) {
        if (callsign == null || callsign.isBlank()) {
            return "";
        }
        String c = callsign.trim().toUpperCase();
        for (int n = c.length(); n >= 1; n--) {
            String cc = prefixToCc.get(c.substring(0, n));
            if (cc != null) {
                return cc;
            }
        }
        return "";
    }
}
