package io.github.fortuneoflab.dualsimbands.radio;

/**
 * Rules for remembering an NR band that the platform once reported explicitly for an NR-ARFCN whose
 * frequency alone is ambiguous (e.g. 647328 = 3709.92 MHz is inside both n77 and n78). A remembered
 * band is shown as "past report", never as a current verified value, and only if it is still one of
 * the candidates for that frequency. Keys include MCC-MNC so one carrier's report never labels another's.
 */
public final class BandMemory {
    private BandMemory() {}

    public static String key(String mcc, String mnc, int nrArfcn) {
        return "nr:" + (mcc == null ? "" : mcc) + (mnc == null ? "" : mnc) + ":" + nrArfcn;
    }

    /** Worth remembering: a platform-reported band that resolves a real ambiguity. */
    public static boolean shouldLearn(NrChannel ch) {
        return ch != null && ch.nrArfcn >= 0 && ch.band > 0 && ch.bandProvenance == Provenance.VERIFIED
                && ch.candidateBands.length > 1 && contains(ch.candidateBands, ch.band);
    }

    /** The remembered band to show for an unresolved channel, or 0 when none applies. */
    public static int usable(NrChannel ch, int remembered) {
        if (ch == null || ch.band != 0 || remembered <= 0) return 0;
        return contains(ch.candidateBands, remembered) ? remembered : 0;
    }

    private static boolean contains(int[] values, int v) {
        for (int x : values) if (x == v) return true;
        return false;
    }
}
