package de.bockermann.ft8monitor.qsolog;

import de.bockermann.ft8monitor.qsolog.QsoLogRepository.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QsoLogRepositoryTest {

    private static final String ADIF = """
            <adif_ver:5>3.1.0<EOH>
            <call:6>DL1ABC<mode:3>FT8<band:3>20m<qso_date:8>20261027<time_on:6>120000<gridsquare:4>JO31<EOR>
            <call:4>W1AW<mode:3>FT8<band:3>40m<qso_date:8>20261027<time_on:6>121500<EOR>
            """;

    private QsoLogRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("qso.db"));
        repo = new QsoLogRepository(new JdbcTemplate(ds));
        repo.init();
    }

    @Test
    void importsQsosAndTracksWorkedCalls() {
        ImportResult r = repo.importAdif(ADIF, "test");
        assertEquals(2, r.added());
        assertEquals(0, r.skipped());
        assertEquals(2, repo.count());

        List<String> worked = repo.workedCalls();
        assertTrue(worked.contains("DL1ABC"));
        assertTrue(worked.contains("W1AW"));
    }

    @Test
    void reimportIgnoresDuplicates() {
        repo.importAdif(ADIF, "test");
        ImportResult again = repo.importAdif(ADIF, "test");
        assertEquals(0, again.added());
        assertEquals(2, again.skipped());
        assertEquals(2, repo.count()); // still just the two
    }

    @Test
    void recentReturnsNewestFirstWithParsedFields() {
        repo.importAdif(ADIF, "test");
        List<LoggedQso> recent = repo.recent(10);
        assertEquals(2, recent.size());
        assertEquals("W1AW", recent.get(0).call()); // 12:15 is newer than 12:00
        assertEquals("DL1ABC", recent.get(1).call());
        assertEquals("JO31", recent.get(1).grid());
        assertEquals("20m", recent.get(1).band());
    }
}
