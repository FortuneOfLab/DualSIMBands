package io.github.fortuneoflab.dualsimbands.radio;

/**
 * NR global frequency raster, 3GPP TS 38.104 Table 5.4.2.1-1:
 * F_REF = F_REF-Offs + ΔF_Global (N_REF − N_REF-Offs).
 */
public final class NrRaster {
    public static final String SOURCE = "3GPP TS 38.104 Table 5.4.2.1-1";
    public static final int MAX_NR_ARFCN = 3279165;

    private NrRaster() {}

    public static boolean isValid(int nrArfcn) { return nrArfcn >= 0 && nrArfcn <= MAX_NR_ARFCN; }

    /** Reference frequency in kHz, or -1 for an invalid NR-ARFCN. */
    public static long frequencyKhz(int n) {
        if (!isValid(n)) return -1;
        if (n < 600000) return 5L * n;                                // 0–3000 MHz, 5 kHz
        if (n < 2016667) return 3000000L + 15L * (n - 600000);        // 3000–24250 MHz, 15 kHz
        return 24250080L + 60L * (n - 2016667);                       // 24250–100000 MHz, 60 kHz
    }

    /** FR1 up to 7125 MHz, FR2 from 24250 MHz (TS 38.104 clause 5.1). 0 when neither. */
    public static int frequencyRange(long khz) {
        if (khz < 0) return 0;
        if (khz <= 7125000L) return 1;
        if (khz >= 24250000L) return 2;
        return 0;
    }
}
