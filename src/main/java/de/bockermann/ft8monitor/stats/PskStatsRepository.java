package de.bockermann.ft8monitor.stats;

import de.bockermann.ft8monitor.pskreporter.Spot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for the PSKReporter spots this tool sends, in a small
 * embedded SQLite table. Counts are bucketed per minute and broken down by band
 * and Maidenhead grid (4-char square), so a single table serves the reports-rate
 * time series, the per-band breakdown and the activity heatmap over any range.
 *
 * <p>Raw spots are never stored — only the aggregated counts.
 */
@Repository
public class PskStatsRepository {

    private static final Logger log = LoggerFactory.getLogger(PskStatsRepository.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public PskStatsRepository(JdbcTemplate jdbc,
                              @Value("${ft8.stats-retention-days:365}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS psk_report_stats (
                    bucket  INTEGER NOT NULL,   -- epoch minute (unix / 60)
                    band    TEXT    NOT NULL,   -- e.g. '40m', or '' if unknown
                    grid    TEXT    NOT NULL,   -- 4-char Maidenhead square, or ''
                    count   INTEGER NOT NULL,
                    PRIMARY KEY (bucket, band, grid)
                )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_psk_stats_bucket ON psk_report_stats(bucket)");
        log.info("PSK statistics store ready (retention {} days)", retentionDays);
    }

    /** Record a batch of sent spots at the given time (aggregated, not stored raw). */
    public void record(List<Spot> spots, long epochSeconds) {
        if (spots == null || spots.isEmpty()) {
            return;
        }
        long bucket = epochSeconds / 60;
        Map<String, int[]> agg = new HashMap<>(); // "band\u0000grid" -> count
        for (Spot s : spots) {
            String key = nz(s.band()) + "\u0000" + nz(s.grid());
            agg.computeIfAbsent(key, k -> new int[1])[0]++;
        }
        List<Object[]> batch = new ArrayList<>(agg.size());
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            String[] parts = e.getKey().split("\u0000", 2);
            batch.add(new Object[]{bucket, parts[0], parts[1], e.getValue()[0]});
        }
        jdbc.batchUpdate(
                "INSERT INTO psk_report_stats(bucket, band, grid, count) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(bucket, band, grid) DO UPDATE SET count = count + excluded.count",
                batch);
    }

    /** Reports summed into time buckets of {@code bucketMinutes} over the last {@code minutes}. */
    public List<Bucket> rate(long minutes, int bucketMinutes, String band) {
        long from = nowMinute() - minutes;
        int step = Math.max(1, bucketMinutes);
        StringBuilder sql = new StringBuilder(
                "SELECT (bucket/" + step + ")*" + step + " AS t, SUM(count) c "
                        + "FROM psk_report_stats WHERE bucket >= ?");
        List<Object> args = new ArrayList<>();
        args.add(from);
        appendBand(sql, args, band);
        sql.append(" GROUP BY t ORDER BY t");
        return jdbc.query(sql.toString(),
                (rs, i) -> new Bucket(rs.getLong("t") * 60, rs.getLong("c")), args.toArray());
    }

    /** Per-cell counts over the last {@code minutes}, at field (2-char) or square (4-char) level. */
    public List<CellCount> heatmap(long minutes, boolean squareLevel, String band) {
        long from = nowMinute() - minutes;
        String cell = squareLevel ? "grid" : "substr(grid, 1, 2)";
        StringBuilder sql = new StringBuilder(
                "SELECT " + cell + " AS cell, SUM(count) c "
                        + "FROM psk_report_stats WHERE bucket >= ? AND grid <> ''");
        List<Object> args = new ArrayList<>();
        args.add(from);
        appendBand(sql, args, band);
        sql.append(" GROUP BY cell");
        return jdbc.query(sql.toString(),
                (rs, i) -> new CellCount(rs.getString("cell"), rs.getLong("c")), args.toArray());
    }

    /** Per-band totals over the last {@code minutes}. */
    public List<BandCount> bands(long minutes) {
        long from = nowMinute() - minutes;
        return jdbc.query(
                "SELECT band, SUM(count) c FROM psk_report_stats WHERE bucket >= ? "
                        + "GROUP BY band ORDER BY c DESC",
                (rs, i) -> new BandCount(rs.getString("band"), rs.getLong("c")), from);
    }

    /** Total reports over the last {@code minutes}. */
    public long total(long minutes) {
        Long n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(count), 0) FROM psk_report_stats WHERE bucket >= ?",
                Long.class, nowMinute() - minutes);
        return n == null ? 0 : n;
    }

    /** Drop statistics older than the retention window. */
    @Scheduled(fixedRateString = "${ft8.stats-prune-interval-ms:86400000}")
    public void prune() {
        long horizon = nowMinute() - (long) retentionDays * 1440;
        int removed = jdbc.update("DELETE FROM psk_report_stats WHERE bucket < ?", horizon);
        if (removed > 0) {
            log.info("Pruned {} stale PSK statistics rows", removed);
        }
    }

    private static void appendBand(StringBuilder sql, List<Object> args, String band) {
        if (band != null && !band.isBlank()) {
            sql.append(" AND band = ?");
            args.add(band);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static long nowMinute() {
        return Instant.now().getEpochSecond() / 60;
    }

    /** A time bucket: its start (epoch seconds) and the report count. */
    public record Bucket(long t, long count) {
    }

    /** Count for a Maidenhead cell (field or square). */
    public record CellCount(String cell, long count) {
    }

    /** Count for a band. */
    public record BandCount(String band, long count) {
    }
}
