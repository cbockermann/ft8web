package de.bockermann.ft8monitor.qsolog;

/**
 * A logged QSO for the QSOs view. {@code t} is the start time (unix seconds, UTC).
 */
public record LoggedQso(long t, String call, String grid, String band, String mode,
                        Integer freqHz, String rstSent, String rstRcvd) {
}
