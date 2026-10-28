package de.bockermann.ft8monitor.filter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Named, reusable Control-view filter expressions, stored in SQLite. Expressions
 * are kept as plain text (not serialised ASTs), so they survive a future DB
 * rework or export trivially.
 */
@Repository
public class SavedFilterRepository {

    private static final Logger log = LoggerFactory.getLogger(SavedFilterRepository.class);

    private final JdbcTemplate jdbc;

    public SavedFilterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS saved_filter (
                    name        TEXT PRIMARY KEY,
                    expression  TEXT NOT NULL,
                    created_unix INTEGER
                )""");
        log.info("Saved-filter store ready");
    }

    public List<SavedFilter> list() {
        return jdbc.query("SELECT name, expression FROM saved_filter ORDER BY name",
                (rs, i) -> new SavedFilter(rs.getString("name"), rs.getString("expression")));
    }

    /** Insert or replace a named filter. */
    public SavedFilter save(String name, String expression) {
        jdbc.update(
                "INSERT INTO saved_filter(name, expression, created_unix) VALUES (?, ?, ?) "
                        + "ON CONFLICT(name) DO UPDATE SET expression = excluded.expression",
                name.trim(), expression, Instant.now().getEpochSecond());
        return new SavedFilter(name.trim(), expression);
    }

    public void delete(String name) {
        jdbc.update("DELETE FROM saved_filter WHERE name = ?", name);
    }

    /** A named filter: {@code name} and its expression text. */
    public record SavedFilter(String name, String expression) {
    }
}
