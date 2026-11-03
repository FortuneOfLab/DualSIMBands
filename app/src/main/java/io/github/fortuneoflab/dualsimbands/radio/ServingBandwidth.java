package io.github.fortuneoflab.dualsimbands.radio;

/**
 * Bandwidth of the primary serving cell when no component composition is known. Two sources, in order:
 * 1. a registered/serving CellInfoLte on the same EARFCN that reports its bandwidth (VERIFIED);
 * 2. a ServiceState bandwidth list with exactly one entry and no CA indication: one carrier, so the
 *    value is the serving cell's (INDICATED, shown as "報告値"). Not while idle (see CaAssessment.idle): the
 *    list is then left over from the last connection.
 * Anything else stays unattributed.
 */
public final class ServingBandwidth {
    public static final class Result {
        public final int khz;
        public final Provenance provenance;
        Result(int khz, Provenance provenance) { this.khz = khz; this.provenance = provenance; }
    }

    private ServingBandwidth() {}

    /** @return null when the bandwidth cannot be attributed to the serving cell */
    public static Result of(RadioSnapshot s, RadioAssessment a) {
        if (s == null || a == null || !a.ca.components.isEmpty()) return null;
        if (a.servingLte != null && a.servingLte.earfcn >= 0 && s.cells.status == RadioSnapshot.Cells.Status.OK && !a.cellsStale) {
            for (LteCell c : s.cells.lte) {
                if (c.role != CellRole.UNREGISTERED && c.channel.earfcn == a.servingLte.earfcn && c.bandwidthKhz != null)
                    return new Result(c.bandwidthKhz, Provenance.VERIFIED);
            }
        }
        if (a.servingLte == null && a.servingNr == null) return null;
        int[] list = a.ca.reportedBandwidthsKhz;
        boolean caHint = a.displayOverride == DisplayOverride.LTE_CA || a.displayOverride == DisplayOverride.LTE_ADVANCED_PRO;
        if (!a.ca.idle && list.length == 1 && list[0] > 0 && !caHint) return new Result(list[0], Provenance.INDICATED);
        return null;
    }

    /** Index of the only entry equal to {@code khz}; -1 when there is none or more than one. */
    public static int matchIndex(int[] list, int khz) {
        int found = -1;
        for (int i = 0; i < list.length; i++) {
            if (list[i] != khz) continue;
            if (found >= 0) return -1;
            found = i;
        }
        return found;
    }
}
