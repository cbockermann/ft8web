package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.overview.GridFieldAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Overview data: per-Maidenhead-field message counts within a recent window,
 * used to seed the activity heatmap (the client then keeps it live from the SSE
 * feed).
 */
@RestController
@RequestMapping("/api")
public class OverviewController {

    private final GridFieldAggregator aggregator;

    public OverviewController(GridFieldAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/fields")
    public List<FieldCount> fields(@RequestParam(defaultValue = "15") long minutes) {
        return aggregator.countsSince(minutes).entrySet().stream()
                .map(e -> new FieldCount(e.getKey(), e.getValue()))
                .toList();
    }

    /** Activity in one Maidenhead field. */
    public record FieldCount(String field, int count) {
    }
}
