package de.bockermann.ft8monitor.wsjtx;

/**
 * Maps a frequency to its amateur band label (e.g. {@code 20m}), so a decode can
 * be tagged with the band it was heard on even after the dial is moved.
 */
final class BandPlan {

    private record Range(long lo, long hi, String name) {
    }

    // Common HF + 6/2 m bands (Hz). Order does not matter; ranges don't overlap.
    private static final Range[] BANDS = {
            new Range(1_800_000L, 2_000_000L, "160m"),
            new Range(3_500_000L, 4_000_000L, "80m"),
            new Range(5_250_000L, 5_450_000L, "60m"),
            new Range(7_000_000L, 7_300_000L, "40m"),
            new Range(10_100_000L, 10_150_000L, "30m"),
            new Range(14_000_000L, 14_350_000L, "20m"),
            new Range(18_068_000L, 18_168_000L, "17m"),
            new Range(21_000_000L, 21_450_000L, "15m"),
            new Range(24_890_000L, 24_990_000L, "12m"),
            new Range(28_000_000L, 29_700_000L, "10m"),
            new Range(50_000_000L, 54_000_000L, "6m"),
            new Range(144_000_000L, 148_000_000L, "2m"),
    };

    private BandPlan() {
    }

    /** Band label for a frequency in Hz, or {@code null} if outside the known bands. */
    static String of(Long hz) {
        if (hz == null) {
            return null;
        }
        for (Range r : BANDS) {
            if (hz >= r.lo() && hz <= r.hi()) {
                return r.name();
            }
        }
        return null;
    }
}
