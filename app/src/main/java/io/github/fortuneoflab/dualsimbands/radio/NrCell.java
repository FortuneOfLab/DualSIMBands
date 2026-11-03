package io.github.fortuneoflab.dualsimbands.radio;

/** One CellInfoNr entry. CellIdentityNr has no bandwidth field, so none is stored. */
public final class NrCell {
    public final CellRole role;
    public final NrChannel channel;
    public final Integer pci, tac;
    /** Location-adjacent; never log without the separate opt-in. */
    public final Long nci;
    public final Integer ssRsrp, ssRsrq, ssSinr;
    /** "440-51"; null when not reported. */
    public final String plmn;
    /**
     * Timing advance as getTimingAdvanceMicros reports it (Android 14+); null when not reported. Its unit is unsettled:
     * AOSP calls it one-way microseconds (0-1282), the current API reference the raw TS 38.213 index (0-3846), and
     * on the Pixel 10a it hardly changed across 24 cells in 8 minutes on a train. Shown and recorded as reported,
     * never converted to a distance.
     */
    public final Integer ta;
    public final long observedAtMillis;

    public NrCell(CellRole role, NrChannel channel, Integer pci, Integer tac, Long nci,
                  Integer ssRsrp, Integer ssRsrq, Integer ssSinr, long observedAtMillis) {
        this(role, channel, pci, tac, nci, ssRsrp, ssRsrq, ssSinr, null, null, observedAtMillis);
    }

    public NrCell(CellRole role, NrChannel channel, Integer pci, Integer tac, Long nci,
                  Integer ssRsrp, Integer ssRsrq, Integer ssSinr, String plmn, Integer ta, long observedAtMillis) {
        this.role = role; this.channel = channel; this.pci = pci; this.tac = tac; this.nci = nci;
        this.ssRsrp = ssRsrp; this.ssRsrq = ssRsrq; this.ssSinr = ssSinr;
        this.plmn = plmn; this.ta = ta; this.observedAtMillis = observedAtMillis;
    }

    public static NrCell fromRaw(boolean registered, int connectionStatus, int nrArfcn, int[] bands, int pci, int tac,
                                 long nci, int ssRsrp, int ssRsrq, int ssSinr, long observedAtMillis) {
        return fromRaw(registered, connectionStatus, nrArfcn, bands, pci, tac, nci, ssRsrp, ssRsrq, ssSinr,
                null, null, Measure.UNAVAILABLE, observedAtMillis);
    }

    public static NrCell fromRaw(boolean registered, int connectionStatus, int nrArfcn, int[] bands, int pci, int tac,
                                 long nci, int ssRsrp, int ssRsrq, int ssSinr, String mcc, String mnc, int ta,
                                 long observedAtMillis) {
        // An EN-DC leg (secondary-serving NR) has no SIB1 of its own: Pixel reports TAC/NCI/TA as 0 there,
        // which means "not reported", not cell 0.
        boolean leg = CellRole.from(registered, connectionStatus) == CellRole.SECONDARY_SERVING;
        if (leg && tac == 0) tac = Measure.UNAVAILABLE;
        if (leg && nci == 0) nci = Measure.UNAVAILABLE_LONG;
        if (leg && ta == 0) ta = Measure.UNAVAILABLE;
        // Seen on the au n28 leg: SS-SINR 0 with RSRP/RSRQ unavailable, i.e. not measured, not 0 dB.
        if (leg && ssSinr == 0 && ssRsrp == Measure.UNAVAILABLE) ssSinr = Measure.UNAVAILABLE;
        return new NrCell(CellRole.from(registered, connectionStatus),
                FrequencyCalculator.nr(Measure.nrArfcn(nrArfcn), bands),
                Measure.valid(pci, 0, 1007), Measure.valid(tac, 0, 16777215),
                Measure.valid(nci, 0L, 68719476735L),
                Measure.valid(ssRsrp, -156, -31), Measure.valid(ssRsrq, -43, 20),
                Measure.valid(ssSinr, -23, 40), Measure.plmn(mcc, mnc), Measure.valid(ta, 0, 3846), observedAtMillis);
    }
}
