package io.github.fortuneoflab.dualsimbands.radio;

/** Exact kHz to MHz text, without floating-point rounding. */
public final class Mhz {
    private Mhz() {}

    /** 2120000 -> "2120.0", 1503400 -> "1503.4", 3650010 -> "3650.01". */
    public static String format(long khz) {
        long whole = khz / 1000, frac = Math.abs(khz % 1000);
        String sign = khz < 0 && whole == 0 ? "-" : "";
        String f = String.format(java.util.Locale.ROOT, "%03d", frac);
        while (f.length() > 1 && f.endsWith("0")) f = f.substring(0, f.length() - 1);
        return sign + whole + "." + f;
    }
}
