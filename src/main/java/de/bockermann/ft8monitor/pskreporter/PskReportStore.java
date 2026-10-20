package de.bockermann.ft8monitor.pskreporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.List;

/**
 * Persists the raw PSKReporter MQTT reports for the station's own callsign — the
 * spots where <em>other</em> stations heard us — in the embedded SQLite database.
 * This backs the "own receivability" default of the Reports view: the standing
 * MQTT subscription ({@link PskMqttService}) writes each own-call report here, so
 * the view opens pre-populated with history instead of waiting for the next burst.
 *
 * <p>Unlike {@code psk_report_stats} (aggregated counts of what we <em>send</em>),
 * this keeps individual reports so the map can colour cells by mean SNR and the
 * hover chart can show each report.
 */
@Repository
public class PskReportStore {

    private static final Logger log = LoggerFactory.getLogger(PskReportStore.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public PskReportStore(JdbcTemplate jdbc,
                          @Value("${pskreporter.rx-retention-days:30}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS psk_rx_report (
                    t     INTEGER NOT NULL,  -- unix seconds
                    call  TEXT    NOT NULL,  -- our callsign (sender)
                    rx    TEXT    NOT NULL,  -- receiving station (heard us)
                    grid  TEXT    NOT NULL,  -- receiver Maidenhead locator, or ''
                    band  TEXT    NOT NULL,  -- e.g. '20m', or ''
                    mode  TEXT    NOT NULL,  -- e.g. 'FT8', or ''
                    snr   INTEGER,           -- reception SNR in dB (nullable)
                    freq  REAL               -- frequency in Hz (nullable)
                )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_psk_rx_call_t ON psk_rx_report(call, t)");
        log.info("Own-call PSK report store ready (retention {} days)", retentionDays);
    }

    /** Store one own-call report (a station that heard us). */
    public void save(long t, String call, String rx, String grid, String band, String mode,
                     Integer snr, Double freq) {
        jdbc.update(
                "INSERT INTO psk_rx_report(t, call, rx, grid, band, mode, snr, freq) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                t, nz(call).toUpperCase(), nz(rx), nz(grid), nz(band), nz(mode), snr, freq);
    }

    /**
     * The most recent stored reports for {@code call} within the last
     * {@code sinceEpoch}..now window, newest first, shaped like the live MQTT
     * payload (sc/rc/rl/rp/f/b/md/t) so the frontend can merge them seamlessly.
     */
    public List<RxReport> recent(String call, long sinceEpoch, int limit) {
        return jdbc.query(
                "SELECT t, call, rx, grid, band, mode, snr, freq FROM psk_rx_report "
                        + "WHERE call = ? AND t >= ? ORDER BY t DESC LIMIT ?",
                (rs, i) -> new RxReport(
                        rs.getLong("t"),
                        rs.getString("call"),
                        rs.getString("rx"),
                        rs.getString("grid"),
                        (Integer) rs.getObject("snr"),
                        (Double) rs.getObject("freq"),
                        rs.getString("band"),
                        rs.getString("mode")),
                nz(call).toUpperCase(), sinceEpoch, Math.max(1, limit));
    }

    /** Drop reports older than the retention window. */
    @Scheduled(fixedRateString = "${pskreporter.rx-prune-interval-ms:86400000}")
    public void prune() {
        long horizon = Instant.now().getEpochSecond() - (long) retentionDays * 86400;
        int removed = jdbc.update("DELETE FROM psk_rx_report WHERE t < ?", horizon);
        if (removed > 0) {
            log.info("Pruned {} stale own-call PSK reports", removed);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * A stored report in the live MQTT payload's shape: our call is the sender
     * (sc), the receiver (rc) at grid (rl) heard us at rp dB.
     */
    public record RxReport(long t, String sc, String rc, String rl, Integer rp, Double f,
                           String b, String md) {
    }
}
