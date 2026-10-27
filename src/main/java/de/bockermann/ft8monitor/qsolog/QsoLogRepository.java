package de.bockermann.ft8monitor.qsolog;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Logged QSOs in the embedded SQLite database. Records come from WSJT-X (its
 * {@code LoggedADIF} UDP message) and from {@code .adi} file imports; both go
 * through {@link AdifParser}. Duplicates are ignored via a dedup key
 * (call|band|mode|minute) — an FT8 QSO starts in a definite UTC minute, so that
 * uniquely identifies it across re-imports.
 */
@Repository
public class QsoLogRepository {

    private static final Logger log = LoggerFactory.getLogger(QsoLogRepository.class);

    private final JdbcTemplate jdbc;

    public QsoLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS qso (
                    dkey       TEXT PRIMARY KEY,  -- call|band|mode|minute (dedup)
                    call       TEXT NOT NULL,
                    grid       TEXT,
                    band       TEXT,
                    mode       TEXT,
                    start_unix INTEGER,
                    freq_hz    INTEGER,
                    rst_sent   TEXT,
                    rst_rcvd   TEXT,
                    my_call    TEXT,
                    my_grid    TEXT,
                    source     TEXT,
                    logged_unix INTEGER
                )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_qso_call ON qso(call)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_qso_start ON qso(start_unix)");
        log.info("QSO log store ready");
    }

    /** Parse an ADIF document and insert its QSOs, ignoring duplicates. */
    public ImportResult importAdif(String adif, String source) {
        List<Map<String, String>> records = AdifParser.parse(adif);
        int added = 0;
        int skipped = 0;
        long now = System.currentTimeMillis() / 1000;
        for (Map<String, String> r : records) {
            String call = up(r.get("call"));
            if (call.isEmpty()) {
                continue;
            }
            String band = low(r.get("band"));
            String mode = up(r.get("mode"));
            long start = epoch(r.get("qso_date"), r.get("time_on"));
            String dkey = call + "|" + band + "|" + mode + "|" + (start / 60);
            int n = jdbc.update(
                    "INSERT OR IGNORE INTO qso(dkey, call, grid, band, mode, start_unix, freq_hz, "
                            + "rst_sent, rst_rcvd, my_call, my_grid, source, logged_unix) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    dkey, call, up(r.get("gridsquare")), band, mode, start, freqHz(r.get("freq")),
                    nz(r.get("rst_sent")), nz(r.get("rst_rcvd")), up(firstOf(r, "station_callsign", "operator")),
                    up(r.get("my_gridsquare")), source, now);
            if (n > 0) {
                added++;
            } else {
                skipped++;
            }
        }
        if (added > 0 || skipped > 0) {
            log.info("QSO import ({}): {} added, {} skipped (duplicates)", source, added, skipped);
        }
        return new ImportResult(added, skipped, added + skipped);
    }

    /** Logged QSOs, newest first. */
    public List<LoggedQso> recent(int limit) {
        return jdbc.query(
                "SELECT start_unix, call, grid, band, mode, freq_hz, rst_sent, rst_rcvd "
                        + "FROM qso ORDER BY start_unix DESC LIMIT ?",
                (rs, i) -> new LoggedQso(rs.getLong("start_unix"), rs.getString("call"),
                        rs.getString("grid"), rs.getString("band"), rs.getString("mode"),
                        (Integer) rs.getObject("freq_hz"), rs.getString("rst_sent"), rs.getString("rst_rcvd")),
                Math.max(1, limit));
    }

    /** Distinct callsigns worked, for the "worked before" marking. */
    public List<String> workedCalls() {
        return jdbc.queryForList("SELECT DISTINCT call FROM qso", String.class);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM qso", Long.class);
        return n == null ? 0 : n;
    }

    private static long epoch(String date, String time) {
        if (date == null || date.length() < 8) {
            return 0;
        }
        try {
            int y = Integer.parseInt(date.substring(0, 4));
            int mo = Integer.parseInt(date.substring(4, 6));
            int d = Integer.parseInt(date.substring(6, 8));
            int hh = 0;
            int mm = 0;
            int ss = 0;
            if (time != null && time.length() >= 4) {
                hh = Integer.parseInt(time.substring(0, 2));
                mm = Integer.parseInt(time.substring(2, 4));
                if (time.length() >= 6) {
                    ss = Integer.parseInt(time.substring(4, 6));
                }
            }
            return LocalDateTime.of(y, mo, d, hh, mm, ss).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Integer freqHz(String mhz) {
        if (mhz == null || mhz.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(mhz.trim()) * 1_000_000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstOf(Map<String, String> r, String... keys) {
        for (String k : keys) {
            String v = r.get(k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String up(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String low(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** Result of an import: how many rows were new vs. ignored as duplicates. */
    public record ImportResult(int added, int skipped, int total) {
    }
}
