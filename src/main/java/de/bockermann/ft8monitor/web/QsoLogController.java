package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.qsolog.LoggedQso;
import de.bockermann.ft8monitor.qsolog.QsoLogRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Logged QSOs: the QSOs view list, the worked-callsign set (for "worked before"
 * marking across the UI), and ADIF import (duplicates ignored).
 */
@RestController
@RequestMapping("/api/qsos")
public class QsoLogController {

    private final QsoLogRepository qsos;

    public QsoLogController(QsoLogRepository qsos) {
        this.qsos = qsos;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LoggedQso> list(@RequestParam(defaultValue = "1000") int limit) {
        return qsos.recent(Math.min(Math.max(1, limit), 50000));
    }

    @GetMapping(path = "/calls", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> workedCalls() {
        return qsos.workedCalls();
    }

    /** Import an uploaded ADIF (.adi/.adif) file; duplicates are ignored. */
    @PostMapping(path = "/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public QsoLogRepository.ImportResult importAdif(@RequestParam("file") MultipartFile file)
            throws IOException {
        String adif = new String(file.getBytes(), StandardCharsets.UTF_8);
        return qsos.importAdif(adif, "adif-import");
    }
}
