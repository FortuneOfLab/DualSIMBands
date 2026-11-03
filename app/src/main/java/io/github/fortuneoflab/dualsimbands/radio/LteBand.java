package io.github.fortuneoflab.dualsimbands.radio;

/**
 * One row of 3GPP TS 36.101 Table 5.7.3-1. In that table N_Offs equals the first channel
 * number of each range, so only the first and last EARFCN are stored.
 */
public final class LteBand {
    public final int number;
    public final Duplex duplex;
    public final long dlLowKhz;
    public final int dlFirst, dlLast;
    /** For TDD these equal the downlink values; for SDL they are -1. */
    public final long ulLowKhz;
    public final int ulFirst, ulLast;

    LteBand(int number, Duplex duplex, long dlLowKhz, int dlFirst, int dlLast,
            long ulLowKhz, int ulFirst, int ulLast) {
        this.number = number; this.duplex = duplex;
        this.dlLowKhz = dlLowKhz; this.dlFirst = dlFirst; this.dlLast = dlLast;
        this.ulLowKhz = ulLowKhz; this.ulFirst = ulFirst; this.ulLast = ulLast;
    }

    public boolean containsDownlink(int earfcn) { return earfcn >= dlFirst && earfcn <= dlLast; }

    /** F_DL = F_DL_low + 0.1 (N_DL - N_Offs-DL) MHz, in kHz. */
    public long downlinkKhz(int earfcn) { return dlLowKhz + 100L * (earfcn - dlFirst); }

    /** True when the paired FDD ranges have the same width, i.e. a fixed default separation. */
    boolean hasFixedPairing() {
        return duplex == Duplex.FDD && ulFirst >= 0 && (dlLast - dlFirst) == (ulLast - ulFirst);
    }
}
