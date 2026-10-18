package de.bockermann.ft8monitor.stats;

import de.bockermann.ft8monitor.pskreporter.Spot;
import de.bockermann.ft8monitor.stats.PskStatsRepository.BandCount;
import de.bockermann.ft8monitor.stats.PskStatsRepository.CellCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PskStatsRepositoryTest {

    private PskStatsRepository repo;
    private final long now = Instant.now().getEpochSecond();

    @BeforeEach
    void setUp(@TempDir Path dir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("stats.db"));
        repo = new PskStatsRepository(new JdbcTemplate(ds), 365);
        repo.init();
    }

    private static Spot spot(String call, String grid, long freq, int snr, String band, long t) {
        return new Spot(call, grid, freq, snr, band, t);
    }

    private List<Spot> sample() {
        return List.of(
                spot("AB1HL", "FN42", 7074000, -10, "40m", now),
                spot("W2XYZ", "FN31", 7074000, -5, "40m", now),
                spot("DL1ABC", "JO62", 14074000, 3, "20m", now),
                spot("K1ABC", "", 7074000, -8, "40m", now)); // no grid
    }

    private static Map<String, Long> cellMap(List<CellCount> cells) {
        return cells.stream().collect(Collectors.toMap(CellCount::cell, CellCount::count));
    }

    @Test
    void recordsTotalsBandsAndHeatmap() {
        repo.record(sample(), now);

        assertEquals(4, repo.total(60));

        Map<String, Long> bands = repo.bands(60).stream()
                .collect(Collectors.toMap(BandCount::band, BandCount::count));
        assertEquals(3L, bands.get("40m"));
        assertEquals(1L, bands.get("20m"));

        Map<String, Long> fields = cellMap(repo.heatmap(60, false, null));
        assertEquals(2L, fields.get("FN")); // FN42 + FN31 summed
        assertEquals(1L, fields.get("JO"));

        Map<String, Long> squares = cellMap(repo.heatmap(60, true, null));
        assertEquals(1L, squares.get("FN42"));
        assertEquals(1L, squares.get("FN31"));
        assertEquals(1L, squares.get("JO62"));
    }

    @Test
    void bandFilterNarrowsHeatmap() {
        repo.record(sample(), now);
        Map<String, Long> only20 = cellMap(repo.heatmap(60, false, "20m"));
        assertEquals(1L, only20.get("JO"));
        assertEquals(null, only20.get("FN")); // 40m excluded
    }

    @Test
    void upsertAccumulates() {
        repo.record(sample(), now);
        repo.record(sample(), now); // same minute → counts add up
        assertEquals(8, repo.total(60));
        assertEquals(4L, cellMap(repo.heatmap(60, false, null)).get("FN"));
    }

    @Test
    void rateBucketsOverTime() {
        repo.record(sample(), now);
        List<PskStatsRepository.Bucket> rate = repo.rate(60, 1, null);
        long sum = rate.stream().mapToLong(PskStatsRepository.Bucket::count).sum();
        assertEquals(4, sum);
    }
}
