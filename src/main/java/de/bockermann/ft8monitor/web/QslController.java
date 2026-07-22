package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.model.Qsl;
import de.bockermann.ft8monitor.qso.QsoGrouper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint returning the QSO ("QSL") a given decode belongs to.
 */
@RestController
@RequestMapping("/api")
public class QslController {

    private final QsoGrouper grouper;

    public QslController(QsoGrouper grouper) {
        this.grouper = grouper;
    }

    @GetMapping("/qsl/{decodeId}")
    public ResponseEntity<Qsl> qsl(@PathVariable String decodeId) {
        Qsl qsl = grouper.qslFor(decodeId);
        return qsl == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(qsl);
    }
}
