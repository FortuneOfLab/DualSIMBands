package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaAnalyzerTest {
    private static final int PRIMARY = 1, SECONDARY = 2, UNKNOWN_STATUS = Integer.MAX_VALUE;

    private static CaAssessment ca(Snapshots s) {
        return CaAnalyzer.analyze(s.build(), Snapshots.NOW, Snapshots.STALE_AFTER);
    }

    /** SPEC 13: several detected B19/B21/B42 cells are not a confirmed 4CC CA. */
    @Test public void detectedCellsAreNeverConfirmedComponents() {
        Snapshots s = new Snapshots().display(DisplayOverride.LTE_CA)
                .lteCell(true, UNKNOWN_STATUS, 6525, 15000)   // B21 registered
                .lteCell(false, UNKNOWN_STATUS, 6100, 10000)  // B19
                .lteCell(false, UNKNOWN_STATUS, 42000, 20000) // B42
                .lteCell(false, UNKNOWN_STATUS, 42500, 20000); // B42
        CaAssessment a = ca(s);
        assertEquals(CaAssessment.Status.INDICATED, a.status);
        assertEquals(Provenance.INDICATED, a.provenance);
        assertTrue(a.components.isEmpty());
        assertNull("bandwidths of detected cells must not be summed", a.aggregateBandwidthKhz);
    }

    @Test public void detectedCellsWithoutAnyIndicationAreNotCa() {
        Snapshots s = new Snapshots().lteCell(true, UNKNOWN_STATUS, 6525, 15000).lteCell(false, UNKNOWN_STATUS, 42000, 20000);
        CaAssessment a = ca(s);
        assertEquals(CaAssessment.Status.NOT_INDICATED, a.status);
        assertEquals(Provenance.UNKNOWN, a.provenance);
    }

    @Test public void serviceStateBandwidthListIsAnIndicationOnly() {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 100, 20000, 20000, 20000, 20000, 100000);
        CaAssessment a = ca(s);
        assertEquals(CaAssessment.Status.INDICATED, a.status);
        assertTrue(a.evidence.contains(CaAssessment.Evidence.SERVICE_STATE_BANDWIDTHS));
        assertArrayEquals(new int[]{20000, 20000, 20000, 20000, 100000}, a.reportedBandwidthsKhz);
        assertNull(a.aggregateBandwidthKhz);
        assertTrue(a.components.isEmpty());
    }

    @Test public void primaryWithoutSecondaryIsNotProofOfNoCa() {
        CaAssessment a = ca(new Snapshots().display(DisplayOverride.LTE_CA).lteCell(true, PRIMARY, 100, 20000));
        assertEquals(CaAssessment.Status.INDICATED, a.status);
    }

    @Test public void platformMarkedPrimaryAndSecondaryAreVerified() {
        CaAssessment a = ca(new Snapshots()
                .lteCell(true, PRIMARY, 100, 20000)
                .lteCell(false, SECONDARY, 6525, 15000)
                .lteCell(false, UNKNOWN_STATUS, 42000, 20000));
        assertEquals(CaAssessment.Status.VERIFIED_CA, a.status);
        assertEquals(Provenance.VERIFIED, a.provenance);
        assertEquals(2, a.components.size());
        assertTrue(a.components.get(0).primary);
        assertEquals(1, a.components.get(0).lte.band);
        assertEquals(21, a.components.get(1).lte.band);
        assertEquals(Long.valueOf(35000), a.aggregateBandwidthKhz);
        assertTrue(a.evidence.contains(CaAssessment.Evidence.CELL_CONNECTION_STATUS));
    }

    /** Seen on the Pixel 10a while NewRadioSupporter showed n77: the NR leg must not drop out of the table. */
    @Test public void secondaryServingNrCellIsPartOfTheComposition() {
        Snapshots s = new Snapshots().display(DisplayOverride.NR_ADVANCED)
                .lteCell(true, PRIMARY, 100, 20000)
                .lteCell(false, SECONDARY, 42000, 20000)
                .lteCell(false, SECONDARY, 41802, 20000)
                .nrCell(false, SECONDARY, 647328);
        RadioAssessment r = s.reduce();
        CaAssessment a = r.ca;
        assertEquals(CaAssessment.Status.VERIFIED_CA, a.status);
        assertEquals(4, a.components.size());
        CaAssessment.Component nr = a.components.get(3);
        assertEquals(Rat.NR, nr.rat);
        assertEquals("3709.92", Mhz.format(nr.nr.frequencyKhz));
        assertEquals(-1, nr.bandwidthKhz);
        assertNull("NR bandwidth unknown, so no grand total", a.aggregateBandwidthKhz);
        assertEquals("CA：LTE 3 キャリア（合計 60 MHz） + NR 1（帯域幅不明）", io.github.fortuneoflab.dualsimbands.ui.RadioText.ca(a));
        assertEquals(RadioAssessment.NrStatus.NSA_VERIFIED, r.nrStatus);
        assertEquals("NSA · Sub-6", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(r));
        // Android reports LTE in NSA; the connection column must not hide the verified NR leg.
        assertEquals("LTE + NR（NSA）", io.github.fortuneoflab.dualsimbands.ui.RadioText.connection(r));
        assertTrue(io.github.fortuneoflab.dualsimbands.ui.RadioText.observedRat(r).startsWith("LTE（Androidの報告値"));
        assertEquals("5G+ 表示 · 通信方式 LTE + NR（NSA） · NR 接続中", io.github.fortuneoflab.dualsimbands.ui.RadioText.summary(r));
    }

    @Test public void unverifiedIconDoesNotAddNrToTheConnection() {
        RadioAssessment r = new Snapshots().display(DisplayOverride.NR_NSA).reduce();
        assertEquals("LTE", io.github.fortuneoflab.dualsimbands.ui.RadioText.connection(r));
    }

    /** n28 (777.85 MHz) is FR1 but not what Japanese users call "Sub-6". */
    @Test public void lowFr1NrIsNotLabelledSub6() {
        RadioAssessment r = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 155570).reduce();
        assertEquals("NSA · 3.3 GHz 未満", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(r));
        RadioAssessment mmw = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 2070833).reduce();
        assertEquals("NSA · ミリ波", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(mmw));
    }

    /** Japanese networks: below 3.6 GHz is 転用5G, from 3.6 GHz (n77/n78/n79) Sub-6. */
    @Test public void japaneseNetworksSay転用5G() {
        RadioAssessment n28 = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 155570).reduce();
        assertEquals("NSA · 転用5G", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(n28, "440"));
        RadioAssessment n78b42 = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 636667).reduce(); // 3550.005 MHz
        assertEquals("NSA · 転用5G", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(n78b42, "440"));
        RadioAssessment n77 = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 647328).reduce();
        assertEquals("NSA · Sub-6", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(n77, "440"));
        assertEquals("other countries keep the neutral label", "NSA · 3.3 GHz 未満",
                io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(n28, "310"));
    }

    /** Seen on the Pixel 10a (LINEMO): n3 listed before n77 made the NR column say only 転用5G. */
    @Test public void everyNrClassInUseIsShownHighestFirst() {
        RadioAssessment r = new Snapshots().lteCell(true, PRIMARY, 500, 20000)
                .nrCell(false, SECONDARY, 369870).nrCell(false, SECONDARY, 663334).reduce();
        assertEquals("NSA · Sub-6 + 転用5G", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(r, "440"));
        assertEquals("Sub-6 + 転用5G", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrShort(r, "440"));
        RadioAssessment twoSub6 = new Snapshots().lteCell(true, PRIMARY, 500, 20000)
                .nrCell(false, SECONDARY, 663334).nrCell(false, SECONDARY, 647328).reduce();
        assertEquals("one class is named once", "NSA · Sub-6", io.github.fortuneoflab.dualsimbands.ui.RadioText.nrMode(twoSub6, "440"));
    }

    /** Carrier colours follow the same classes: n3 refarmed, n77 Sub-6, FR2 mmWave; outside Japan the 3.3 GHz line. */
    @Test public void nrClassForColours() {
        RadioAssessment r = new Snapshots().lteCell(true, PRIMARY, 500, 20000)
                .nrCell(false, SECONDARY, 369870).nrCell(false, SECONDARY, 663334).nrCell(false, SECONDARY, 2070833).reduce();
        java.util.List<io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass> got = new java.util.ArrayList<>();
        for (CaAssessment.Component c : r.ca.components) if (c.nr != null) got.add(io.github.fortuneoflab.dualsimbands.ui.RadioText.nrClassOf(c.nr, "440"));
        assertTrue(got.contains(io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass.REFARMED));
        assertTrue(got.contains(io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass.SUB6));
        assertTrue(got.contains(io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass.MMWAVE));
        assertEquals(io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass.UNKNOWN, io.github.fortuneoflab.dualsimbands.ui.RadioText.nrClassOf(null, "440"));
        RadioAssessment b42 = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 636667).reduce();
        NrChannel ch = b42.ca.components.get(1).nr;
        assertEquals("3.55 GHz in Japan", io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass.REFARMED,
                io.github.fortuneoflab.dualsimbands.ui.RadioText.nrClassOf(ch, "440"));
        assertEquals("3.55 GHz elsewhere", io.github.fortuneoflab.dualsimbands.ui.RadioText.NrClass.SUB6,
                io.github.fortuneoflab.dualsimbands.ui.RadioText.nrClassOf(ch, "310"));
    }

    /**
     * Seen on the Pixel 10a with the screen off and traffic: the cell list marked B1 + one n77 while PhysicalChannelConfig
     * had named two n77 carriers. One serving cell per RAT there neither proves nor rules out CA.
     */
    @Test public void lteAnchorPlusNrLegFromTheCellListLeavesCaOpen() {
        CaAssessment a = ca(new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 643334));
        assertEquals(CaAssessment.Status.CELLS_ONLY, a.status);
        assertEquals(Provenance.INDICATED, a.provenance);
        assertEquals(2, a.components.size());
    }

    @Test public void lteAnchorPlusNrLegFromPhysicalChannelConfigIsEnDcNotCa() {
        CaAssessment a = ca(new Snapshots().physical(Rat.LTE, true, 100, 20000).physical(Rat.NR, false, 643334, 100000));
        assertEquals(CaAssessment.Status.VERIFIED_NO_CA, a.status);
        assertEquals(Provenance.VERIFIED, a.provenance);
    }

    @Test public void aggregateIsUnknownWhenAnyVerifiedBandwidthIsUnknown() {
        CaAssessment a = ca(new Snapshots().lteCell(true, PRIMARY, 100, 20000).lteCell(false, SECONDARY, 6525, 0));
        assertEquals(CaAssessment.Status.VERIFIED_CA, a.status);
        assertNull(a.aggregateBandwidthKhz);
    }

    @Test public void physicalChannelConfigNamesEveryComponent() {
        CaAssessment a = ca(new Snapshots().physical(Rat.LTE, true, 100, 20000)
                .physical(Rat.LTE, false, 1500, 20000).physical(Rat.NR, false, 643334, 100000));
        assertEquals(CaAssessment.Status.VERIFIED_CA, a.status);
        assertEquals(3, a.components.size());
        assertEquals(Long.valueOf(140000), a.aggregateBandwidthKhz);
        assertEquals(3, a.components.get(1).lte.band);
    }

    /** Observed on docomo: B1 reported twice on one channel; it is one carrier and its width counts once. */
    @Test public void sameChannelReportedTwiceIsOneComponent() {
        CaAssessment a = ca(new Snapshots().physical(Rat.LTE, false, 324, 15000).physical(Rat.LTE, true, 324, 15000)
                .physical(Rat.LTE, false, 1850, 20000));
        assertEquals(2, a.components.size());
        assertTrue(a.components.get(0).primary);
        assertEquals(1, a.components.get(0).lte.band);
        assertEquals(Long.valueOf(35000), a.aggregateBandwidthKhz);
    }

    @Test public void duplicateOnlyFillsWhatTheKeptReportLacks() {
        CaAssessment a = ca(new Snapshots().physical(Rat.LTE, true, 324, -1).physical(Rat.LTE, false, 324, 15000));
        assertEquals(1, a.components.size());
        assertEquals(CaAssessment.Status.VERIFIED_NO_CA, a.status);
        assertEquals(Long.valueOf(15000), a.aggregateBandwidthKhz);
    }

    @Test public void servingCellListedAgainAsSecondaryIsOneComponent() {
        CaAssessment a = ca(new Snapshots().lteCell(true, PRIMARY, 324, 15000).lteCell(false, SECONDARY, 324, 15000)
                .lteCell(false, SECONDARY, 1850, 20000));
        assertEquals(2, a.components.size());
        assertTrue(a.components.get(0).primary);
        assertEquals(Long.valueOf(35000), a.aggregateBandwidthKhz);
    }

    @Test public void sameBandOnDifferentChannelsStaysTwoComponents() {
        CaAssessment a = ca(new Snapshots().physical(Rat.LTE, true, 324, 15000)
                .physical(Rat.LTE, false, 42095, 20000).physical(Rat.LTE, false, 42293, 20000));
        assertEquals(3, a.components.size());
    }

    @Test public void singleVerifiedCarrierIsVerifiedNoCa() {
        assertEquals(CaAssessment.Status.VERIFIED_NO_CA,
                ca(new Snapshots().display(DisplayOverride.LTE_CA).physical(Rat.LTE, true, 100, 20000)).status);
    }

    @Test public void stalePrivilegedDataFallsBackToIndications() {
        Snapshots s = new Snapshots().display(DisplayOverride.LTE_CA).physical(Rat.LTE, true, 100, 20000)
                .physical(Rat.LTE, false, 6525, 15000);
        s.physicalAt = Snapshots.NOW - Snapshots.STALE_AFTER - 1;
        CaAssessment a = ca(s);
        assertEquals(CaAssessment.Status.INDICATED, a.status);
        assertTrue(a.components.isEmpty());
    }

    /** An old secondary cell listed with fresh ones is no part of the composition read from the cell list. */
    @Test public void oldCellsAreLeftOutOfTheCellListComposition() {
        Snapshots b = new Snapshots().lteCell(true, PRIMARY, 100, 20000);
        b.lte.add(LteCell.fromRaw(false, SECONDARY, 6525, null, 1, 1, 1, 15000, -95, -10, -60, 5, Snapshots.NOW - 60_000));
        CaAssessment a = ca(b);
        assertNotEquals(CaAssessment.Status.VERIFIED_CA, a.status);
        assertFalse(a.evidence.contains(CaAssessment.Evidence.CELL_CONNECTION_STATUS));
    }
}
