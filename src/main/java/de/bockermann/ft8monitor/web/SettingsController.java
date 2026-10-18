package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.qso.Callsigns;
import de.bockermann.ft8monitor.settings.Settings;
import de.bockermann.ft8monitor.settings.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read and update the station {@link Settings} (callsign, locator). Empty values
 * are allowed (unset); non-empty values are validated.
 */
@RestController
@RequestMapping("/api")
public class SettingsController {

    /** Maidenhead locator: field + square, optional subsquare (case-insensitive). */
    private static final Pattern LOCATOR = Pattern.compile("^[A-Ra-r]{2}[0-9]{2}([A-Xa-x]{2})?$");

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings")
    public Settings get() {
        return settings.get();
    }

    @PutMapping("/settings")
    public ResponseEntity<?> update(@RequestBody Settings incoming) {
        String error = validate(incoming);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.ok(settings.update(incoming));
    }

    private static String validate(Settings s) {
        String callsign = s.callsign() == null ? "" : s.callsign().trim();
        String locator = s.locator() == null ? "" : s.locator().trim();

        if (!callsign.isEmpty() && !Callsigns.isCallsign(callsign.toUpperCase())) {
            return "Invalid callsign: " + callsign;
        }
        if (!locator.isEmpty() && !LOCATOR.matcher(locator).matches()) {
            return "Invalid Maidenhead locator: " + locator + " (expected e.g. JO31 or JO31HK)";
        }
        return null;
    }
}
