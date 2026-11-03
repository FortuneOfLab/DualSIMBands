package io.github.fortuneoflab.dualsimbands.radio;

/** Result of converting an observed LTE downlink EARFCN. Unknown numbers are -1 / null. */
public final class LteChannel {
    public enum Uplink {
        /** FDD uplink assuming the band's default TX–RX separation; the network may signal another. */
        DERIVED_DEFAULT_SEPARATION,
        /** TDD: uplink and downlink share the same carrier frequency. */
        TDD_SHARED,
        /** Supplemental-downlink band: there is no uplink carrier. */
        NONE_SDL,
        /** FDD band with variable separation or no pair for this channel. */
        UNKNOWN
    }

    /** -1 when absent or invalid. */
    public final int earfcn;
    /** Band number derived from the EARFCN range; 0 when unknown. */
    public final int band;
    public final Provenance bandProvenance;
    public final Duplex duplex;
    public final long downlinkKhz;
    public final long uplinkKhz;
    public final Uplink uplink;
    /** The platform reported band(s) that do not contain this EARFCN's band. */
    public final boolean bandMismatch;
    public final int[] reportedBands;

    LteChannel(int earfcn, int band, Provenance bandProvenance, Duplex duplex, long downlinkKhz,
               long uplinkKhz, Uplink uplink, boolean bandMismatch, int[] reportedBands) {
        this.earfcn = earfcn; this.band = band; this.bandProvenance = bandProvenance; this.duplex = duplex;
        this.downlinkKhz = downlinkKhz; this.uplinkKhz = uplinkKhz; this.uplink = uplink;
        this.bandMismatch = bandMismatch; this.reportedBands = reportedBands;
    }

    public boolean hasFrequency() { return downlinkKhz >= 0; }

    @Override public String toString() {
        return "LTE B" + (band == 0 ? "?" : band) + " EARFCN " + earfcn
                + (hasFrequency() ? " DL " + Mhz.format(downlinkKhz) + " MHz" : "");
    }
}
