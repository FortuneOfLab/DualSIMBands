package io.github.fortuneoflab.dualsimbands.radio;

/** Rejects platform sentinels (CellInfo.UNAVAILABLE = Integer.MAX_VALUE) and out-of-range values. */
public final class Measure {
    public static final int UNAVAILABLE = Integer.MAX_VALUE;
    public static final long UNAVAILABLE_LONG = Long.MAX_VALUE;

    private Measure() {}

    /** The value when within [min, max], otherwise null (unknown). */
    public static Integer valid(int value, int min, int max) {
        return value == UNAVAILABLE || value < min || value > max ? null : value;
    }

    public static Long valid(long value, long min, long max) {
        return value == UNAVAILABLE_LONG || value < min || value > max ? null : value;
    }

    /** Valid EARFCN or -1. */
    public static int earfcn(int value) {
        return value == UNAVAILABLE || value < 0 || value > LteBandTable.MAX_EARFCN ? -1 : value;
    }

    /** Valid NR-ARFCN or -1. */
    public static int nrArfcn(int value) {
        return NrRaster.isValid(value) ? value : -1;
    }

    /** "440-51" from CellIdentity MCC/MNC strings; null unless both are 3 and 2-3 digits. */
    public static String plmn(String mcc, String mnc) {
        if (mcc == null || mnc == null || !mcc.matches("\\d{3}") || !mnc.matches("\\d{2,3}")) return null;
        return mcc + "-" + mnc;
    }
}
