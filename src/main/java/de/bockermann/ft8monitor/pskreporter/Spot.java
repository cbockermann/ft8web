package de.bockermann.ft8monitor.pskreporter;

/**
 * One reception report: a sending station observed on a real RF frequency.
 *
 * @param call     sender callsign (validated).
 * @param grid     sender's 4-char Maidenhead grid, or empty if not in the message.
 * @param freqHz   absolute transmit frequency in Hz (from the decode's rf_hz).
 * @param snr      signal-to-noise ratio in dB.
 * @param band     band tag (used for per-interval de-duplication).
 * @param timeUnix decode time (epoch seconds).
 */
public record Spot(String call, String grid, long freqHz, int snr, String band, long timeUnix) {
}
