package de.bockermann.ft8monitor.web;

import de.bockermann.ft8monitor.filter.SavedFilterRepository;
import de.bockermann.ft8monitor.filter.SavedFilterRepository.SavedFilter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Named Control-view filter expressions (list / save / delete). */
@RestController
@RequestMapping("/api/filters")
public class FilterController {

    private final SavedFilterRepository filters;

    public FilterController(SavedFilterRepository filters) {
        this.filters = filters;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SavedFilter> list() {
        return filters.list();
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public SavedFilter save(@RequestBody SavedFilter body) {
        return filters.save(body.name(), body.expression());
    }

    @DeleteMapping("/{name}")
    public void delete(@PathVariable String name) {
        filters.delete(name);
    }
}
