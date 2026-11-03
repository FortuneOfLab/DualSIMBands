package io.github.fortuneoflab.dualsimbands.radio;

/** Pure conversions from reported channel numbers; no guessing from the status-bar icon. */
public final class FrequencyCalculator {
    private static final int[] NONE = new int[0];

    private FrequencyCalculator() {}

    /**
     * Converts a downlink EARFCN. The EARFCN alone fixes the band numbering and frequency,
     * because 36.101 assigns disjoint EARFCN ranges. Reported bands only confirm or contradict it.
     *
     * @param reportedBands bands from CellIdentityLte.getBands(), or null/empty when not reported
     */
    public static LteChannel lte(int earfcn, int[] reportedBands) {
        int[] reported = clean(reportedBands);
        if (earfcn < 0 || earfcn > LteBandTable.MAX_EARFCN) {
            return new LteChannel(-1, 0, Provenance.UNKNOWN, null, -1, -1, LteChannel.Uplink.UNKNOWN, false, reported);
        }
        LteBand band = LteBandTable.forDownlinkEarfcn(earfcn);
        if (band == null) {
            return new LteChannel(earfcn, 0, Provenance.UNKNOWN, null, -1, -1, LteChannel.Uplink.UNKNOWN, false, reported);
        }
        boolean mismatch = reported.length > 0 && !contains(reported, band.number);
        Provenance bandProvenance = reported.length > 0 && !mismatch ? Provenance.VERIFIED : Provenance.DERIVED;
        long dl = band.downlinkKhz(earfcn);
        long ul = -1;
        LteChannel.Uplink uplink;
        switch (band.duplex) {
            case TDD: ul = dl; uplink = LteChannel.Uplink.TDD_SHARED; break;
            case SDL: uplink = LteChannel.Uplink.NONE_SDL; break;
            default:
                if (band.hasFixedPairing()) {
                    ul = band.ulLowKhz + 100L * (earfcn - band.dlFirst);
                    uplink = LteChannel.Uplink.DERIVED_DEFAULT_SEPARATION;
                } else {
                    uplink = LteChannel.Uplink.UNKNOWN;
                }
        }
        return new LteChannel(earfcn, band.number, bandProvenance, band.duplex, dl, ul, uplink, mismatch, reported);
    }

    /**
     * Converts an NR-ARFCN with the global raster. The band is taken from the platform when reported;
     * otherwise it is filled in only if exactly one band contains the frequency.
     */
    public static NrChannel nr(int nrArfcn, int[] reportedBands) {
        int[] reported = clean(reportedBands);
        if (!NrRaster.isValid(nrArfcn)) {
            return new NrChannel(-1, -1, 0, 0, Provenance.UNKNOWN, NONE, reported, false);
        }
        long khz = NrRaster.frequencyKhz(nrArfcn);
        int[] candidates = NrBandTable.candidates(khz);
        int band = 0;
        Provenance provenance = Provenance.UNKNOWN;
        boolean mismatch = false;
        if (reported.length > 0) {
            for (int r : reported) {
                NrBandTable.Band known = NrBandTable.byNumber(r);
                if (known != null && !known.contains(khz)) mismatch = true;
            }
            if (reported.length == 1 && !mismatch) { band = reported[0]; provenance = Provenance.VERIFIED; }
        } else if (candidates.length == 1) {
            band = candidates[0];
            provenance = Provenance.DERIVED;
        }
        return new NrChannel(nrArfcn, khz, NrRaster.frequencyRange(khz), band, provenance, candidates, reported, mismatch);
    }

    private static int[] clean(int[] bands) {
        if (bands == null || bands.length == 0) return NONE;
        int count = 0;
        for (int b : bands) if (b > 0 && b != Integer.MAX_VALUE) count++;
        int[] out = new int[count];
        int i = 0;
        for (int b : bands) if (b > 0 && b != Integer.MAX_VALUE) out[i++] = b;
        return out;
    }

    private static boolean contains(int[] values, int v) {
        for (int x : values) if (x == v) return true;
        return false;
    }
}
