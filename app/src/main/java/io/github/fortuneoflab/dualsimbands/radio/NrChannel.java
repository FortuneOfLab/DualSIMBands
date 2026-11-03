package io.github.fortuneoflab.dualsimbands.radio;

/** Result of converting an observed NR-ARFCN. */
public final class NrChannel {
    /** -1 when absent or invalid. */
    public final int nrArfcn;
    /** -1 when unknown. */
    public final long frequencyKhz;
    /** 1 (FR1), 2 (FR2) or 0 when unknown. */
    public final int frequencyRange;
    /** 0 when unknown or ambiguous. */
    public final int band;
    /** VERIFIED when reported by the platform, DERIVED when exactly one band fits, else UNKNOWN. */
    public final Provenance bandProvenance;
    /** All bands whose downlink range contains the frequency (several when they overlap). */
    public final int[] candidateBands;
    public final int[] reportedBands;
    /** A reported band does not contain the computed frequency. */
    public final boolean bandMismatch;

    NrChannel(int nrArfcn, long frequencyKhz, int frequencyRange, int band, Provenance bandProvenance,
              int[] candidateBands, int[] reportedBands, boolean bandMismatch) {
        this.nrArfcn = nrArfcn; this.frequencyKhz = frequencyKhz; this.frequencyRange = frequencyRange;
        this.band = band; this.bandProvenance = bandProvenance; this.candidateBands = candidateBands;
        this.reportedBands = reportedBands; this.bandMismatch = bandMismatch;
    }

    public boolean hasFrequency() { return frequencyKhz >= 0; }
    public boolean isBandAmbiguous() { return band == 0 && candidateBands.length > 1; }

    @Override public String toString() {
        return "NR n" + (band == 0 ? "?" : band) + " NR-ARFCN " + nrArfcn
                + (hasFrequency() ? " " + Mhz.format(frequencyKhz) + " MHz" : "");
    }
}
