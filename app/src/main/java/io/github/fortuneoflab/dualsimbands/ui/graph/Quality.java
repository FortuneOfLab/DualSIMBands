package io.github.fortuneoflab.dualsimbands.ui.graph;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

/**
 * Rough signal-quality bands for meters. These are common rules of thumb, not 3GPP thresholds,
 * so the UI labels them as a guide (目安). Unknown values stay unknown: no bar is drawn.
 */
public final class Quality {
    public enum Level { GOOD, FAIR, POOR, UNKNOWN }

    public enum Metric {
        /** LTE RSRP or NR SS-RSRP, dBm. */
        RSRP(-140, -44, -90, -105, "dBm"),
        /** LTE RSRQ or NR SS-RSRQ, dB. */
        RSRQ(-20, -3, -10, -15, "dB"),
        /** LTE RS-SINR or NR SS-SINR, dB. */
        SINR(-10, 30, 13, 0, "dB"),
        /** LTE RSSI (whole-carrier power incl. interference), dBm. */
        RSSI(-113, -51, -70, -85, "dBm");

        final int min, max, good, fair;
        public final String unit;
        Metric(int min, int max, int good, int fair, String unit) {
            this.min = min; this.max = max; this.good = good; this.fair = fair; this.unit = unit;
        }
        public int min() { return min; }
        public int max() { return max; }
        public int goodAt() { return good; }
        public int fairAt() { return fair; }
    }

    private Quality() {}

    public static Level level(Metric m, Integer value) {
        if (value == null) return Level.UNKNOWN;
        if (value >= m.good) return Level.GOOD;
        if (value >= m.fair) return Level.FAIR;
        return Level.POOR;
    }

    /** Bar fill 0..1 over the metric's display range; -1 when unknown. */
    public static float fraction(Metric m, Integer value) {
        if (value == null) return -1f;
        float f = (value - m.min) / (float) (m.max - m.min);
        return Math.max(0f, Math.min(1f, f));
    }

    public static String label(Level level) {
        switch (level) {
            case GOOD: return t("良好", "good");
            case FAIR: return t("普通", "fair");
            case POOR: return t("弱い", "weak");
            default: return t("不明", "unknown");
        }
    }
}
