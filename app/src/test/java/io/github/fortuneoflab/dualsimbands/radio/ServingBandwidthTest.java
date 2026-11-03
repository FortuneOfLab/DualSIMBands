package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServingBandwidthTest {
    private static final int UNKNOWN_STATUS = Integer.MAX_VALUE;

    private static ServingBandwidth.Result of(Snapshots s) {
        RadioSnapshot snap = s.build();
        return ServingBandwidth.of(snap, RadioStateReducer.reduce(snap, Snapshots.NOW, Snapshots.STALE_AFTER));
    }

    @Test public void registeredCellOnTheSameEarfcnIsVerified() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 6525, 15000, 15000);  // two entries: list alone would not do
        s.lteCell(true, UNKNOWN_STATUS, 6525, 15000);
        ServingBandwidth.Result r = of(s);
        assertEquals(15000, r.khz);
        assertEquals(Provenance.VERIFIED, r.provenance);
    }

    /** The only registered cell replaces a lagging ServiceState channel, so its own bandwidth is the serving one. */
    @Test public void registeredCellOnAnotherEarfcnBecomesTheServingCell() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 6525, 15000, 15000);
        s.lteCell(true, UNKNOWN_STATUS, 100, 20000);
        assertEquals(100, s.reduce().servingLte.earfcn);
        ServingBandwidth.Result r = of(s);
        assertEquals(20000, r.khz);
        assertEquals(Provenance.VERIFIED, r.provenance);
    }

    /** Two registered cells elsewhere: ambiguous, so ServiceState's channel stays and nothing is attributed. */
    @Test public void ambiguousRegisteredCellsDoNotCount() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 6525, 15000, 15000);
        s.lteCell(true, UNKNOWN_STATUS, 100, 20000);
        s.lteCell(true, UNKNOWN_STATUS, 300, 15000);
        assertEquals(6525, s.reduce().servingLte.earfcn);
        assertNull(of(s));
    }

    /** docomo on the Pixel 10a: one reported bandwidth, no CA indication. */
    @Test public void singleReportedBandwidthWithoutCaHintIsTheServingCell() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 6525, 15000);
        ServingBandwidth.Result r = of(s);
        assertEquals(15000, r.khz);
        assertEquals(Provenance.INDICATED, r.provenance);
    }

    @Test public void caHintOrSeveralValuesStayUnattributed() {
        Snapshots ca = new Snapshots().display(DisplayOverride.LTE_CA);
        ca.service = Snapshots.service(Rat.LTE, 6525, 15000);
        assertNull("4G+ shown: the one value may not be the PCC", of(ca));
        Snapshots two = new Snapshots();
        two.service = Snapshots.service(Rat.LTE, 6525, 15000, 20000);
        assertNull(of(two));
        Snapshots none = new Snapshots();
        none.service = Snapshots.service(Rat.LTE, 6525);
        assertNull(of(none));
    }

    @Test public void knownCompositionTakesPrecedence() {
        Snapshots s = new Snapshots().lteCell(true, 1, 100, 20000).lteCell(false, 2, 6525, 15000);
        assertNull(of(s));
    }

    @Test public void idleSimDoesNotUseTheLeftoverList() {
        Snapshots s = new Snapshots();
        s.defaultData = false;
        s.service = Snapshots.service(Rat.LTE, 6525, 15000);
        assertNull(of(s));
        Snapshots ca = new Snapshots();
        ca.defaultData = false;
        ca.service = Snapshots.service(Rat.LTE, 6525, 15000, 15000, 10000, 20000);
        assertEquals("no CA on an idle SIM", CaAssessment.Status.NOT_INDICATED, ca.reduce().ca.status);
        ca.display(DisplayOverride.LTE_CA);
        assertEquals("a leftover 4G+ icon is not a CA hint", CaAssessment.Status.NOT_INDICATED, ca.reduce().ca.status);
    }

    @Test public void matchIndexNeedsExactlyOneEqualValue() {
        assertEquals(2, ServingBandwidth.matchIndex(new int[]{15000, 10000, 20000}, 20000));
        assertEquals(-1, ServingBandwidth.matchIndex(new int[]{15000, 15000, 20000}, 15000));
        assertEquals(-1, ServingBandwidth.matchIndex(new int[]{15000, 10000}, 5000));
    }

    /** Observed on au: data SIM, no traffic, PhysicalChannelConfig empty, list 20 x4 + 100 MHz left over. */
    @Test public void dataSimWithAnEmptyPhysicalChannelReportIsIdle() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 100, 20000, 20000, 20000, 20000, 100000);
        s.physical = new java.util.ArrayList<>();
        s.lteCell(true, 1, 100, 20000);
        CaAssessment ca = s.reduce().ca;
        assertTrue(ca.idle);
        assertEquals(CaAssessment.Status.NOT_INDICATED, ca.status);
        assertEquals(Provenance.VERIFIED, of(s).provenance); // the serving cell's own width still shows
    }

    @Test public void withoutAPhysicalChannelReportTheDataSimIsNotIdle() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 100, 20000, 20000);
        assertFalse(s.reduce().ca.idle);
        assertEquals(CaAssessment.Status.INDICATED, s.reduce().ca.status);
    }
}
