package io.github.fortuneoflab.dualsimbands.radio;

/**
 * Combines display, ServiceState, CellInfo and optional privileged data into one assessment.
 * ServiceState, display info and SignalStrength are pushed on change, so the last value stays current
 * while callbacks are registered (a steady signal produces no callbacks). SignalStrength reports stop while
 * the screen is off; the repository then stands in fresh CellInfo measurements ({@link SignalFallback}).
 * Only polled CellInfo and the privileged physical-channel data are judged against {@code staleAfterMillis}.
 */
public final class RadioStateReducer {
    private RadioStateReducer() {}

    public static RadioAssessment reduce(RadioSnapshot s, long nowMillis, long staleAfterMillis) {
        RadioSnapshot.Service svc = s.service;
        boolean inService = svc != null && svc.inService();
        Rat dataRat = svc == null ? Rat.UNKNOWN : svc.dataRat;
        Provenance dataRatProvenance = svc == null || dataRat == Rat.UNKNOWN ? Provenance.UNKNOWN : Provenance.VERIFIED;

        boolean cellsFresh = s.cells.status == RadioSnapshot.Cells.Status.OK
                && CaAnalyzer.fresh(s.cells.observedAtMillis, nowMillis, staleAfterMillis);

        boolean nrCellDetected = false, nrServingCell = false;
        if (cellsFresh) for (NrCell c : s.cells.nr) {
            // Each cell by its own time: a fresh LTE neighbour must not make an old NR leg count as verified NSA.
            if (!CaAnalyzer.fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
            nrCellDetected = true;
            // NSA: the NR leg is a secondary cell. A primary NR cell while data is LTE is contradictory.
            if (c.role == CellRole.SECONDARY_SERVING) nrServingCell = true;
        }
        boolean nrPhysical = false;
        if (s.physicalChannels != null && CaAnalyzer.fresh(s.physicalChannelsAtMillis, nowMillis, staleAfterMillis)) {
            for (RadioSnapshot.PhysicalChannel p : s.physicalChannels) if (p.rat == Rat.NR) nrPhysical = true;
        }
        boolean nrSignal = s.signal != null && s.signal.nrSsRsrp != null;

        RadioAssessment.NrStatus nr;
        Provenance nrProvenance;
        if (!inService) {
            nr = RadioAssessment.NrStatus.UNKNOWN; nrProvenance = Provenance.UNKNOWN;
        } else if (dataRat == Rat.NR) {
            nr = RadioAssessment.NrStatus.SA_VERIFIED; nrProvenance = Provenance.VERIFIED;
        } else if (dataRat == Rat.LTE && (nrPhysical || nrServingCell)) {
            nr = RadioAssessment.NrStatus.NSA_VERIFIED; nrProvenance = Provenance.VERIFIED;
        } else if ((s.display != null && s.display.override.indicatesNr()) || nrSignal) {
            // The display API's NR state (e.g. NOT_RESTRICTED) is availability, not a connected NR leg.
            nr = RadioAssessment.NrStatus.INDICATED_UNCONFIRMED; nrProvenance = Provenance.INDICATED;
        } else {
            nr = RadioAssessment.NrStatus.NOT_INDICATED; nrProvenance = Provenance.UNKNOWN;
        }

        LteChannel servingLte = null;
        NrChannel servingNr = null;
        if (svc != null && svc.channelNumber >= 0) {
            if (dataRat == Rat.LTE || (dataRat == Rat.UNKNOWN && svc.voiceRat == Rat.LTE)) {
                RadioSnapshot.PhysicalChannel pcc = newerPrimaryLte(s, cellsFresh, nowMillis, staleAfterMillis);
                int earfcn = pcc != null ? pcc.channel
                        : cellsFresh ? currentLteEarfcn(s, svc.channelNumber, nowMillis, staleAfterMillis) : svc.channelNumber;
                int[] bands = pcc != null && pcc.band > 0 ? new int[]{pcc.band} : registeredLteBands(s, earfcn, nowMillis, staleAfterMillis);
                servingLte = FrequencyCalculator.lte(Measure.earfcn(earfcn), bands);
            } else if (dataRat == Rat.NR) {
                servingNr = FrequencyCalculator.nr(Measure.nrArfcn(svc.channelNumber),
                        registeredNrBands(s, svc.channelNumber, nowMillis, staleAfterMillis));
            }
        }

        return new RadioAssessment(s.subscriptionId, s.slotIndex, s.defaultData,
                svc == null ? RadioSnapshot.RegState.UNKNOWN : svc.dataReg, dataRat, dataRatProvenance,
                s.display == null ? null : s.display.override, s.display == null ? null : s.display.network,
                nr, nrProvenance, nrCellDetected, nrSignal, CaAnalyzer.analyze(s, nowMillis, staleAfterMillis),
                servingLte, servingNr, s.cells.status,
                s.cells.status == RadioSnapshot.Cells.Status.OK && !cellsFresh);
    }

    /**
     * ServiceState's channel can lag behind idle-mode reselection (observed: an idle SIM's ServiceState kept
     * EARFCN 6525 / B21 while its only registered cell was B1). When the fresh cell list has exactly one
     * registered LTE cell and none on the ServiceState channel, that cell is the current one. Each cell is judged by
     * its own time (the list's is its newest cell's), so an old registered cell does not replace the channel.
     */
    static int currentLteEarfcn(RadioSnapshot s, int serviceStateEarfcn, long nowMillis, long staleAfterMillis) {
        LteCell only = null;
        int registered = 0;
        for (LteCell c : s.cells.lte) {
            if (c.role != CellRole.PRIMARY_SERVING && c.role != CellRole.REGISTERED) continue;
            if (!CaAnalyzer.fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
            if (c.channel == null || c.channel.earfcn < 0) continue;
            if (c.channel.earfcn == serviceStateEarfcn) return serviceStateEarfcn;
            only = c;
            registered++;
        }
        return registered == 1 ? only.channel.earfcn : serviceStateEarfcn;
    }

    /**
     * The primary LTE carrier of the physical channel config when it was reported after the cell list (or, without a
     * fresh list, after ServiceState); null otherwise, or when there is not exactly one. The config is pushed on a
     * change, while the cell list is polled: on the Pixel 10a (2026-11-01, screen off) an idle docomo SIM's config
     * named the new band (B19) 5 to 15 s before the cell list left the old one (B21). When the list is the newer
     * report, it decides as before. Judged by the time the config was reported, not the time a live listener's last
     * report was last kept current.
     */
    static RadioSnapshot.PhysicalChannel newerPrimaryLte(RadioSnapshot s, boolean cellsFresh, long nowMillis, long staleAfterMillis) {
        if (s.physicalChannels == null || !CaAnalyzer.fresh(s.physicalChannelsAtMillis, nowMillis, staleAfterMillis)) return null;
        long at = s.physicalChannelsReportedAtMillis;
        if (cellsFresh ? at <= s.cells.observedAtMillis : at < s.service.observedAtMillis) return null;
        RadioSnapshot.PhysicalChannel found = null;
        for (RadioSnapshot.PhysicalChannel p : s.physicalChannels) {
            if (p.rat != Rat.LTE || !p.primary || p.channel < 0) continue;
            if (found != null) return null;
            found = p;
        }
        return found;
    }

    /** Band list of a fresh registered/serving LTE cell on the same EARFCN, so its report confirms the band. */
    private static int[] registeredLteBands(RadioSnapshot s, int earfcn, long nowMillis, long staleAfterMillis) {
        for (LteCell c : s.cells.lte) {
            if (!CaAnalyzer.fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
            if (c.role != CellRole.UNREGISTERED && c.channel.earfcn == earfcn) return c.channel.reportedBands;
        }
        return null;
    }

    private static int[] registeredNrBands(RadioSnapshot s, int arfcn, long nowMillis, long staleAfterMillis) {
        for (NrCell c : s.cells.nr) {
            if (!CaAnalyzer.fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
            if (c.role != CellRole.UNREGISTERED && c.channel.nrArfcn == arfcn) return c.channel.reportedBands;
        }
        return null;
    }
}
