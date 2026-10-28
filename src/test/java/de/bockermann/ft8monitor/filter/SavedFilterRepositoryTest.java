package de.bockermann.ft8monitor.filter;

import de.bockermann.ft8monitor.filter.SavedFilterRepository.SavedFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedFilterRepositoryTest {

    private SavedFilterRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("f.db"));
        repo = new SavedFilterRepository(new JdbcTemplate(ds));
        repo.init();
    }

    @Test
    void savesListsUpdatesAndDeletes() {
        repo.save("DX hunt", "cq and new and snr > -18");
        repo.save("Rare", "new_dxcc or last_worked > 30days");
        List<SavedFilter> list = repo.list();
        assertEquals(2, list.size());
        assertEquals("DX hunt", list.get(0).name()); // ordered by name

        // same name updates (no duplicate)
        repo.save("DX hunt", "cq and new");
        assertEquals(2, repo.list().size());
        assertEquals("cq and new", repo.list().get(0).expression());

        repo.delete("Rare");
        assertEquals(1, repo.list().size());
    }
}
