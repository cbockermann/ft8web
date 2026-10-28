package de.bockermann.ft8monitor.wsjtx;

/**
 * Resolved facts for a decode, used by the Control-view filter predicates (and
 * later the auto-responder). Computed once on the backend from the QSO log, the
 * prefix/country data and the station locator, then evaluated client-side.
 *
 * @param call         the subject callsign (the station we would work)
 * @param grid         their Maidenhead grid from the message, or ""
 * @param cc           ISO country code of the subject, or ""
 * @param country      country name, or ""
 * @param worked       have we logged a QSO with this call before?
 * @param lastWorkedSec seconds since we last worked it (null = never)
 * @param workedCount  how many QSOs we've logged with it
 * @param distanceKm   great-circle distance to their grid (null if unknown)
 * @param directedAtMe is the message addressed to our station?
 * @param ageSec       seconds since we first saw this call in this session window
 */
public record DecodeFacts(String call, String grid, String cc, String country,
                          boolean worked, Long lastWorkedSec, int workedCount,
                          Integer distanceKm, boolean directedAtMe, long ageSec) {
}
