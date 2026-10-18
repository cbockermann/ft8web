package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.stats.PskStatsRepository;
import de.bockermann.ft8monitor.stats.PskStatsRepository.BandCount;
import de.bockermann.ft8monitor.stats.PskStatsRepository.Bucket;
import de.bockermann.ft8monitor.stats.PskStatsRepository.CellCount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Statistics for the PSKReporter spots this tool has sent: the reports-rate time
 * series, per-band totals and the activity heatmap, over a selectable range.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final PskStatsRepository stats;

    public StatsController(PskStatsRepository stats) {
        this.stats = stats;
    }

    /** A range: how far back to look and the time-bucket size for the rate series. */
    private enum Range {
        HOUR(60, 1),
        DAY(1440, 30),
        WEEK(7 * 1440, 360),
        MONTH(30 * 1440, 1440);

        final long minutes;
        final int bucketMinutes;

        Range(long minutes, int bucketMinutes) {
            this.minutes = minutes;
            this.bucketMinutes = bucketMinutes;
        }

        static Range of(String s) {
            try {
                return valueOf(s.trim().toUpperCase());
            } catch (Exception e) {
                return HOUR;
            }
        }
    }

    @GetMapping
    public Summary summary(@RequestParam(defaultValue = "day") String range,
                           @RequestParam(defaultValue = "field") String level,
                           @RequestParam(required = false) String band) {
        Range r = Range.of(range);
        boolean square = "square".equalsIgnoreCase(level);
        return new Summary(
                r.name().toLowerCase(),
                r.bucketMinutes * 60L,
                stats.total(r.minutes),
                stats.rate(r.minutes, r.bucketMinutes, band),
                stats.bands(r.minutes),
                stats.heatmap(r.minutes, square, band));
    }

    public record Summary(
            String range,
            long bucketSeconds,
            long total,
            List<Bucket> rate,
            List<BandCount> bands,
            List<CellCount> heatmap
    ) {
    }
}
