package de.bockermann.ft8monitor.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

/**
 * Serves the single-page app's {@code index.html} for the client-side routes so
 * deep links and reloads (e.g. {@code /overview}) work, and injects the servlet
 * context-path into the page:
 *
 * <ul>
 *   <li>{@code <base href="{ctx}/">} so the relatively-referenced JS/CSS assets
 *       resolve under a non-root context-path and on nested routes;</li>
 *   <li>{@code window.__CONTEXT_PATH__} so the SPA can set the router basename
 *       and build API URLs.</li>
 * </ul>
 *
 * Static assets (under {@code /assets/**}) and the {@code /api/**} endpoints are
 * handled elsewhere and are not matched here.
 */
@Controller
public class SpaController {

    /** Cached raw index.html (context-path is injected per request). */
    private volatile String template;

    @GetMapping(value = {
            "/", "/index.html",
            "/feed", "/feed/**",
            "/overview", "/overview/**",
            "/stats", "/stats/**",
            "/reports", "/reports/**",
            "/config", "/config/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> index(HttpServletRequest request) throws IOException {
        String html = indexTemplate();
        if (html == null) {
            return ResponseEntity.status(503).contentType(MediaType.TEXT_PLAIN).body(
                    "Frontend not built. Run `mvn package` (frontend profile) or `npm run build`.");
        }
        String ctx = request.getContextPath(); // "" at root, "/ft8" under a context-path
        String inject = "<base href=\"" + ctx + "/\">"
                + "<script>window.__CONTEXT_PATH__=\"" + ctx + "\";</script>";
        String body = html.replaceFirst("(?i)<head>", "<head>" + Matcher.quoteReplacement(inject));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    private String indexTemplate() throws IOException {
        String t = template;
        if (t == null) {
            ClassPathResource res = new ClassPathResource("static/index.html");
            if (!res.exists()) {
                return null; // frontend not built (e.g. backend-only test run)
            }
            try (InputStream in = res.getInputStream()) {
                t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                template = t;
            }
        }
        return t;
    }
}
