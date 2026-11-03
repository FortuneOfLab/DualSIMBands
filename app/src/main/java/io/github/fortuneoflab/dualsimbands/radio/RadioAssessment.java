package io.github.fortuneoflab.dualsimbands.radio;

/** Provenance-aware view of one subscription, produced by {@link RadioStateReducer}. */
public final class RadioAssessment {
    public enum NrStatus {
        /** Data RAT is NR (standalone registration). */
        SA_VERIFIED,
        /** An authoritative source names an active NR component alongside LTE. */
        NSA_VERIFIED,
        /** The display (or NR signal entry) suggests NR, but no source confirms an active NR link. */
        INDICATED_UNCONFIRMED,
        /** Nothing indicates NR. CellInfoNr may be absent during NSA, so this is not "disconnected". */
        NOT_INDICATED,
        /** No service or no data. */
        UNKNOWN
    }

    public final int subscriptionId, slotIndex;
    public final boolean defaultData;
    public final RadioSnapshot.RegState dataReg;
    /** ServiceState data RAT: VERIFIED when received, else UNKNOWN. */
    public final Rat observedDataRat;
    public final Provenance observedDataRatProvenance;
    /** Status-bar display: always INDICATED. Null when not received. */
    public final DisplayOverride displayOverride;
    public final Rat displayNetwork;
    public final NrStatus nrStatus;
    public final Provenance nrProvenance;
    /** Any NR cell observed in CellInfo, regardless of role. Detection is not an active link. */
    public final boolean nrCellDetected;
    /** The signal held a valid NR measurement: SignalStrength, or the serving NR cell's CellInfo when that is old. */
    public final boolean nrSignalReported;
    public final CaAssessment ca;
    /** Primary serving channel from ServiceState; at most one of these is set. */
    public final LteChannel servingLte;
    public final NrChannel servingNr;
    public final RadioSnapshot.Cells.Status cellsStatus;
    public final boolean cellsStale;

    RadioAssessment(int subscriptionId, int slotIndex, boolean defaultData, RadioSnapshot.RegState dataReg,
                    Rat observedDataRat, Provenance observedDataRatProvenance, DisplayOverride displayOverride,
                    Rat displayNetwork, NrStatus nrStatus, Provenance nrProvenance, boolean nrCellDetected,
                    boolean nrSignalReported, CaAssessment ca, LteChannel servingLte, NrChannel servingNr,
                    RadioSnapshot.Cells.Status cellsStatus, boolean cellsStale) {
        this.subscriptionId = subscriptionId; this.slotIndex = slotIndex; this.defaultData = defaultData;
        this.dataReg = dataReg; this.observedDataRat = observedDataRat;
        this.observedDataRatProvenance = observedDataRatProvenance; this.displayOverride = displayOverride;
        this.displayNetwork = displayNetwork; this.nrStatus = nrStatus; this.nrProvenance = nrProvenance;
        this.nrCellDetected = nrCellDetected; this.nrSignalReported = nrSignalReported; this.ca = ca;
        this.servingLte = servingLte; this.servingNr = servingNr; this.cellsStatus = cellsStatus;
        this.cellsStale = cellsStale;
    }
}
