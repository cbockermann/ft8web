package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.overview.GridFieldAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Overview data: per-Maidenhead-cell message counts within a recent window,
 * used to seed the activity heatmap (the client then keeps it live from the SSE
 * feed). The cell is a 4-char square or a 2-char field, per {@code level}.
 */
@RestController
@RequestMapping("/api")
public class OverviewController {

    private final GridFieldAggregator aggregator;

    public OverviewController(GridFieldAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/fields")
    public List<CellCount> fields(@RequestParam(defaultValue = "15") long minutes,
                                  @RequestParam(defaultValue = "field") String level) {
        boolean squareLevel = "square".equalsIgnoreCase(level);
        return aggregator.countsSince(minutes, squareLevel).entrySet().stream()
                .map(e -> new CellCount(e.getKey(), e.getValue()))
                .toList();
    }

    /** Activity in one Maidenhead cell (field "JO" or square "JO62"). */
    public record CellCount(String cell, int count) {
    }
}
