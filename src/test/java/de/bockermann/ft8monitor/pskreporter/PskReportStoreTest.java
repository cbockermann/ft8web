package de.bockermann.ft8monitor.pskreporter;

import de.bockermann.ft8monitor.pskreporter.PskReportStore.RxReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PskReportStoreTest {

    private PskReportStore store;
    private final long now = Instant.now().getEpochSecond();

    @BeforeEach
    void setUp(@TempDir Path dir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("rx.db"));
        store = new PskReportStore(new JdbcTemplate(ds), 30);
        store.init();
    }

    @Test
    void savesAndReturnsNewestFirstForTheCall() {
        store.save(now - 30, "DL1ABC", "G4XYZ", "IO91", "20m", "FT8", -12, 14074000.0);
        store.save(now - 10, "DL1ABC", "W1AW", "FN31", "20m", "FT8", -3, 14074000.0);
        store.save(now - 20, "OTHER", "W1AW", "FN31", "40m", "FT8", -5, 7074000.0);

        List<RxReport> reports = store.recent("DL1ABC", now - 3600, 100);

        assertEquals(2, reports.size(), "only DL1ABC rows");
        assertEquals("W1AW", reports.get(0).rc(), "newest first");
        assertEquals("G4XYZ", reports.get(1).rc());
        // Shaped like the live payload: our call is the sender, receiver grid in rl.
        assertEquals("DL1ABC", reports.get(0).sc());
        assertEquals("FN31", reports.get(0).rl());
        assertEquals(-3, reports.get(0).rp());
    }

    @Test
    void respectsTheSinceWindowAndLimit() {
        store.save(now - 10000, "DL1ABC", "OLD", "IO91", "20m", "FT8", -20, null);
        store.save(now - 100, "DL1ABC", "NEW1", "IO91", "20m", "FT8", -8, null);
        store.save(now - 50, "DL1ABC", "NEW2", "IO91", "20m", "FT8", -8, null);

        assertEquals(2, store.recent("DL1ABC", now - 3600, 100).size(), "old row excluded by window");
        assertEquals(1, store.recent("DL1ABC", now - 3600, 1).size(), "limit applied");
    }

    @Test
    void callLookupIsCaseInsensitiveAndNullableSnrSurvives() {
        store.save(now - 5, "dl1abc", "RX", "", "", "", null, null);

        List<RxReport> reports = store.recent("DL1ABC", now - 3600, 10);
        assertEquals(1, reports.size());
        assertNull(reports.get(0).rp());
        assertTrue(reports.get(0).rl().isEmpty());
    }
}
