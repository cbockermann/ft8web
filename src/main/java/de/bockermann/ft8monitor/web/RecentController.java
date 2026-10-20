package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.model.Decode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backlog of recent decodes (newest first), so the browser can seed the live
 * feed on load instead of starting empty.
 */
@RestController
@RequestMapping("/api")
public class RecentController {

    private final RecentDecodes recent;

    public RecentController(RecentDecodes recent) {
        this.recent = recent;
    }

    @GetMapping("/recent")
    public List<Decode> recent(@RequestParam(defaultValue = "200") int limit) {
        return recent.latest(limit);
    }
}
