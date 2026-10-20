package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.geo.CallsignFlags;
import de.bockermann.ft8monitor.geo.GeoLookup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolve a callsign to its country (ISO code + flag) from its prefix.
 * {@code GET /api/callsign/{call}} for one, {@code GET /api/callsigns?calls=...}
 * to flag a whole feed in one call.
 */
@RestController
@RequestMapping("/api")
public class CallsignController {

    private final CallsignFlags flags;
    private final GeoLookup geo;

    public CallsignController(CallsignFlags flags, GeoLookup geo) {
        this.flags = flags;
        this.geo = geo;
    }

    /** @param country country name for the code, or "" if unknown. */
    public record CallInfo(String call, String cc, String country, String flag) {
    }

    @GetMapping("/callsign/{call}")
    public ResponseEntity<CallInfo> callsign(@PathVariable String call) {
        CallInfo info = resolve(call);
        return info == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(info);
    }

    @GetMapping("/callsigns")
    public Map<String, CallInfo> callsigns(@RequestParam String calls) {
        Map<String, CallInfo> out = new LinkedHashMap<>();
        for (String c : calls.split(",")) {
            String call = c.trim().toUpperCase();
            if (call.isEmpty() || out.containsKey(call)) {
                continue;
            }
            CallInfo info = resolve(call);
            if (info != null) {
                out.put(call, info);
            }
        }
        return out;
    }

    private CallInfo resolve(String call) {
        String cc = flags.countryCode(call);
        if (cc.isEmpty()) {
            return null;
        }
        return new CallInfo(call.toUpperCase(), cc, geo.countryName(cc), GeoLookup.flag(cc));
    }
}
