package de.bockermann.ft8monitor.model;

import java.util.List;

/**
 * A QSL — one QSO between two stations: the (unordered) pair of callsigns plus
 * the time-ordered list of messages exchanged. For a lone {@code CQ} with no
 * following contact, {@code callB} may be {@code null} and {@code messages}
 * holds just the CQ itself.
 *
 * @param id       stable id for this QSO ({@code CALL_A|CALL_B@firstUnix}).
 * @param callA    first callsign (lexicographically smaller of the pair).
 * @param callB    second callsign, or {@code null} for a lone CQ.
 * @param messages the ordered decodes belonging to this QSO.
 */
public record Qsl(
        String id,
        String callA,
        String callB,
        List<Decode> messages
) {
    public Qsl {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
