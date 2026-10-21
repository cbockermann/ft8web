package de.bockermann.ft8monitor.web;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Response headers that make Server-Sent Events survive a reverse proxy.
 *
 * <ul>
 *   <li>{@code Cache-Control: no-cache, no-transform} — never cache the stream,
 *       and forbid proxies from transforming/compressing it (compression buffers).</li>
 *   <li>{@code X-Accel-Buffering: no} — tells nginx (and compatible proxies) not to
 *       buffer the response, so events reach the browser as they are produced.</li>
 * </ul>
 *
 * Idle timeouts are handled separately by periodic heartbeats on the stream.
 */
final class SseSupport {

    private SseSupport() {
    }

    static void prepare(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
