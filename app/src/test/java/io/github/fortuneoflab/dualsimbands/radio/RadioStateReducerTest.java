package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static io.github.fortuneoflab.dualsimbands.radio.RadioAssessment.NrStatus.*;
import static org.junit.Assert.*;

public class RadioStateReducerTest {
    private static final int PRIMARY = 1, SECONDARY = 2, NONE = 0, UNKNOWN_STATUS = Integer.MAX_VALUE;

    @Test public void fiveGIconWithLteObservedIsOnlyIndicated() {
        RadioAssessment a = new Snapshots().display(DisplayOverride.NR_NSA).reduce();
        assertEquals(Rat.LTE, a.observedDataRat);
        assertEquals(Provenance.VERIFIED, a.observedDataRatProvenance);
        assertEquals(DisplayOverride.NR_NSA, a.displayOverride);
        assertEquals(INDICATED_UNCONFIRMED, a.nrStatus);
        assertEquals(Provenance.INDICATED, a.nrProvenance);
    }

    /** SPEC 13: 5G -> 4G+ during throughput -> 5G again; NR is never confirmed by the icon alone. */
    @Test public void iconSequenceNeverConfirmsNr() {
        DisplayOverride[] sequence = {DisplayOverride.NR_NSA, DisplayOverride.LTE_CA, DisplayOverride.NR_ADVANCED};
        for (DisplayOverride o : sequence) {
            RadioAssessment a = new Snapshots().display(o).reduce();
            assertNotEquals(SA_VERIFIED, a.nrStatus);
            assertNotEquals(NSA_VERIFIED, a.nrStatus);
            assertNotEquals(Provenance.VERIFIED, a.nrProvenance);
        }
        assertEquals(NOT_INDICATED, new Snapshots().display(DisplayOverride.LTE_CA).reduce().nrStatus);
    }

    @Test public void detectedNrNeighbourIsNotAnActiveLink() {
        RadioAssessment a = new Snapshots().display(DisplayOverride.NR_NSA)
                .lteCell(true, UNKNOWN_STATUS, 100, 20000)
                .nrCell(false, NONE, 643334).reduce();
        assertTrue(a.nrCellDetected);
        assertEquals(INDICATED_UNCONFIRMED, a.nrStatus);
    }

    @Test public void secondaryServingNrCellConfirmsNsa() {
        RadioAssessment a = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 643334).reduce();
        assertEquals(NSA_VERIFIED, a.nrStatus);
        assertEquals(Provenance.VERIFIED, a.nrProvenance);
    }

    @Test public void primaryNrCellWhileDataIsLteIsNotTreatedAsNsa() {
        assertEquals(NOT_INDICATED, new Snapshots().nrCell(true, PRIMARY, 643334).reduce().nrStatus);
    }

    @Test public void staleSecondaryNrCellDoesNotConfirmNsa() {
        Snapshots s = new Snapshots().display(DisplayOverride.NR_NSA);
        s.cellsAt = Snapshots.NOW - Snapshots.STALE_AFTER - 1;
        RadioAssessment a = s.nrCell(false, SECONDARY, 643334).reduce();
        assertEquals(INDICATED_UNCONFIRMED, a.nrStatus);
        assertTrue(a.cellsStale);
        assertFalse(a.nrCellDetected);
    }

    @Test public void privilegedPhysicalChannelConfirmsNsa() {
        RadioAssessment a = new Snapshots().physical(Rat.LTE, true, 100, 20000).physical(Rat.NR, false, 643334, 100000).reduce();
        assertEquals(NSA_VERIFIED, a.nrStatus);
    }

    @Test public void nrDataRatIsStandalone() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.NR, 643334);
        RadioAssessment a = s.reduce();
        assertEquals(SA_VERIFIED, a.nrStatus);
        assertNotNull(a.servingNr);
        assertNull(a.servingLte);
        assertEquals("3650.01", Mhz.format(a.servingNr.frequencyKhz));
    }

    @Test public void noServiceAndAirplaneModeAreUnknownNotDisconnected() {
        for (RadioSnapshot.RegState st : new RadioSnapshot.RegState[]{
                RadioSnapshot.RegState.OUT_OF_SERVICE, RadioSnapshot.RegState.POWER_OFF}) {
            Snapshots s = new Snapshots().display(DisplayOverride.NR_NSA);
            s.service = Snapshots.noService(st);
            RadioAssessment a = s.reduce();
            assertEquals(UNKNOWN, a.nrStatus);
            assertEquals(CaAssessment.Status.UNKNOWN, a.ca.status);
            assertNull(a.servingLte);
        }
        Snapshots none = new Snapshots();
        none.service = null;
        RadioAssessment a = none.reduce();
        assertEquals(UNKNOWN, a.nrStatus);
        assertEquals(Provenance.UNKNOWN, a.observedDataRatProvenance);
        assertEquals(RadioSnapshot.RegState.UNKNOWN, a.dataReg);
    }

    @Test public void absenceOfNrIndicationIsNotProof() {
        RadioAssessment a = new Snapshots().reduce();
        assertEquals(NOT_INDICATED, a.nrStatus);
        assertEquals(Provenance.UNKNOWN, a.nrProvenance);
    }

    @Test public void nrSignalEntryIsOnlyAnIndication() {
        Snapshots s = new Snapshots();
        s.signal = new RadioSnapshot.Signal(-90, -10, 10, -95, -11, 5, Snapshots.NOW);
        RadioAssessment a = s.reduce();
        assertTrue(a.nrSignalReported);
        assertEquals(INDICATED_UNCONFIRMED, a.nrStatus);
        // Signal is pushed only on change: an old but still-registered value remains current.
        Snapshots steady = new Snapshots();
        steady.signal = new RadioSnapshot.Signal(-90, -10, 10, -95, -11, 5, Snapshots.NOW - Snapshots.STALE_AFTER - 1);
        assertEquals(INDICATED_UNCONFIRMED, steady.reduce().nrStatus);
    }

    @Test public void servingChannelFromServiceStateIsDerivedUnlessARegisteredCellReportsTheBand() {
        RadioAssessment derived = new Snapshots().reduce();
        assertEquals(1, derived.servingLte.band);
        assertEquals("2120.0", Mhz.format(derived.servingLte.downlinkKhz));
        assertEquals(Provenance.DERIVED, derived.servingLte.bandProvenance);

        Snapshots s = new Snapshots();
        s.cellStatus = RadioSnapshot.Cells.Status.OK;
        s.lte.add(LteCell.fromRaw(true, UNKNOWN_STATUS, 100, new int[]{1}, 1, 1, 1, 20000, -90, -10, -60, 10, Snapshots.NOW));
        assertEquals(Provenance.VERIFIED, s.reduce().servingLte.bandProvenance);
    }

    @Test public void missingLocationPermissionIsReportedNotStale() {
        Snapshots s = new Snapshots();
        s.cellStatus = RadioSnapshot.Cells.Status.NO_PERMISSION;
        RadioAssessment a = s.reduce();
        assertEquals(RadioSnapshot.Cells.Status.NO_PERMISSION, a.cellsStatus);
        assertFalse(a.cellsStale);
        assertFalse(a.nrCellDetected);
    }

    /** Observed on an idle docomo SIM: ServiceState kept B21 (6525) while the only registered cell was B1. */
    @Test public void idleReselectionPrefersTheRegisteredCell() {
        Snapshots b = new Snapshots();
        b.service = Snapshots.service(Rat.LTE, 6525, 15000);
        b.lteCell(true, 1, 300, 15000);
        b.lteCell(false, 0, 6525, 15000); // the old cell, now only a neighbour
        RadioAssessment a = b.reduce();
        assertEquals(300, a.servingLte.earfcn);
        assertEquals(1, a.servingLte.band);
    }

    @Test public void serviceStateChannelKeptWhenARegisteredCellMatches() {
        Snapshots b = new Snapshots();
        b.service = Snapshots.service(Rat.LTE, 100, 20000);
        b.lteCell(true, 1, 100, 20000);
        assertEquals(100, b.reduce().servingLte.earfcn);
    }

    @Test public void staleCellListDoesNotOverrideServiceState() {
        Snapshots b = new Snapshots();
        b.cellsAt = Snapshots.NOW - Snapshots.STALE_AFTER - 1;
        b.service = Snapshots.service(Rat.LTE, 6525, 15000);
        b.lteCell(true, 1, 300, 15000);
        assertEquals(6525, b.reduce().servingLte.earfcn);
    }

    /** Seen on an idle docomo SIM with the screen off: the config named B19 seconds before the cell list left B21. */
    @Test public void newerPhysicalChannelConfigDecidesTheServingCarrier() {
        Snapshots b = new Snapshots();
        b.cellsAt = Snapshots.NOW - 5_000;
        b.service = Snapshots.service(Rat.LTE, 6525, 15000);
        b.lteCell(true, 1, 6525, 15000);
        b.physical(Rat.LTE, true, 6075, 15000, 19);
        b.physicalAt = Snapshots.NOW - 2_000;
        RadioAssessment a = b.reduce();
        assertEquals(6075, a.servingLte.earfcn);
        assertEquals(19, a.servingLte.band);
        assertEquals(Provenance.VERIFIED, a.servingLte.bandProvenance);
    }

    /** The cell list reported after the config decides, as before. */
    @Test public void olderPhysicalChannelConfigLeavesTheCellList() {
        Snapshots b = new Snapshots();
        b.cellsAt = Snapshots.NOW - 2_000;
        b.service = Snapshots.service(Rat.LTE, 6525, 15000);
        b.lteCell(true, 1, 6525, 15000);
        b.physical(Rat.LTE, true, 6075, 15000, 19);
        b.physicalAt = Snapshots.NOW - 5_000;
        assertEquals(6525, b.reduce().servingLte.earfcn);
        b.physicalAt = b.cellsAt;
        assertEquals(6525, b.reduce().servingLte.earfcn);
    }

    /**
     * A live listener's last report kept current by the periodic refresh is still as old as when it was reported: a
     * cell list reported after it decides.
     */
    @Test public void refreshedOldConfigDoesNotOverrideANewerCellList() {
        Snapshots b = new Snapshots();
        b.cellsAt = Snapshots.NOW - 2_000;
        b.service = Snapshots.service(Rat.LTE, 6525, 15000);
        b.lteCell(true, 1, 6525, 15000);
        b.physical(Rat.LTE, true, 6075, 15000, 19);
        b.physicalAt = Snapshots.NOW;
        b.physicalReportedAt = Snapshots.NOW - 20_000;
        RadioAssessment a = b.reduce();
        assertEquals(6525, a.servingLte.earfcn);
        // Still current for the composition.
        assertEquals(CaAssessment.Status.VERIFIED_NO_CA, a.ca.status);
    }

    /** An old config, or one without a single primary LTE carrier, is not used. */
    @Test public void staleOrAmbiguousPhysicalChannelConfigIsNotUsed() {
        Snapshots old = new Snapshots();
        old.cellsAt = Snapshots.NOW - Snapshots.STALE_AFTER - 10_000;
        old.service = Snapshots.service(Rat.LTE, 6525, 15000);
        old.physical(Rat.LTE, true, 6075, 15000, 19);
        old.physicalAt = Snapshots.NOW - Snapshots.STALE_AFTER - 1;
        assertEquals(6525, old.reduce().servingLte.earfcn);

        Snapshots none = new Snapshots();
        none.service = Snapshots.service(Rat.LTE, 6525, 15000);
        none.physical(Rat.LTE, false, 6075, 15000, 19).physical(Rat.NR, true, 643334, 100000);
        assertEquals(6525, none.reduce().servingLte.earfcn);

        Snapshots two = new Snapshots();
        two.service = Snapshots.service(Rat.LTE, 6525, 15000);
        two.physical(Rat.LTE, true, 6075, 15000, 19).physical(Rat.LTE, true, 300, 15000, 1);
        assertEquals(6525, two.reduce().servingLte.earfcn);
    }

    /** Without a fresh cell list, a config reported since ServiceState names the carrier. */
    @Test public void physicalChannelConfigNewerThanServiceStateWithoutCells() {
        Snapshots b = new Snapshots();
        b.service = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE, RadioSnapshot.RegState.IN_SERVICE,
                Rat.LTE, Rat.LTE, 6525, new int[]{15000}, Snapshots.NOW - 3_000);
        b.physical(Rat.LTE, true, 6075, 15000);
        b.physicalAt = Snapshots.NOW - 1_000;
        RadioAssessment a = b.reduce();
        assertEquals(6075, a.servingLte.earfcn);
        assertEquals(Provenance.DERIVED, a.servingLte.bandProvenance);
        b.physicalAt = Snapshots.NOW - 4_000;
        assertEquals(6525, b.reduce().servingLte.earfcn);
    }

    /** An old registered cell listed with a fresh neighbour does not replace ServiceState's channel. */
    @Test public void oldRegisteredCellDoesNotReplaceTheChannel() {
        Snapshots b = new Snapshots();
        b.service = Snapshots.service(Rat.LTE, 6525);
        b.cellStatus = RadioSnapshot.Cells.Status.OK;
        b.lte.add(LteCell.fromRaw(true, Integer.MAX_VALUE, 100, new int[]{1}, 1, 1, 1, 20000, -90, -10, -60, 10, Snapshots.NOW - 60_000));
        b.lte.add(LteCell.fromRaw(false, 0, 1850, null, 2, 1, 1, 20000, -100, -12, -70, 5, Snapshots.NOW));
        assertEquals(6525, b.reduce().servingLte.earfcn);
    }
}
