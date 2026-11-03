package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhysicalChannelsTest {
    @Test public void rolesAndRats() {
        RadioSnapshot.PhysicalChannel pcc = PhysicalChannels.from(13, 1, 100, 20000);
        assertEquals(Rat.LTE, pcc.rat);
        assertTrue(pcc.primary);
        assertEquals(100, pcc.channel);
        assertEquals(20000, pcc.bandwidthKhz);
        RadioSnapshot.PhysicalChannel nr = PhysicalChannels.from(20, 2, 643334, 100000);
        assertEquals(Rat.NR, nr.rat);
        assertFalse(nr.primary);
    }

    @Test public void unknownRoleOrRatIsDropped() {
        assertNull(PhysicalChannels.from(13, -1, 100, 20000));
        assertNull(PhysicalChannels.from(13, 0, 100, 20000));
        assertNull(PhysicalChannels.from(3, 1, 10700, 5000)); // UMTS
    }

    @Test public void sentinelsBecomeUnknown() {
        RadioSnapshot.PhysicalChannel p = PhysicalChannels.from(13, 2, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(-1, p.channel);
        assertEquals(-1, p.bandwidthKhz);
    }

    /** Observed on the Pixel 10a (docomo): an extra SCC with EARFCN 0, 1.4 MHz, PCI 0, band 1. */
    @Test public void emptySlotIsDropped() {
        assertNull(PhysicalChannels.from(13, 2, 0, 1400, 1, 0));
        assertNull(PhysicalChannels.from(13, 2, 0, 0, 0, Integer.MAX_VALUE));
        // EARFCN 0 itself is a valid B1 channel; with a real width or PCI it is kept.
        assertEquals(0, PhysicalChannels.from(13, 2, 0, 5000, 1, 0).channel);
        assertEquals(0, PhysicalChannels.from(13, 2, 0, 1400, 1, 208).channel);
    }

    /** Observed on the Pixel 10a (au, mobile data active): LTE B41 PCC + NR n77 100 MHz. */
    @Test public void observedEnDcUsesTheReportedNrBand() {
        List<RadioSnapshot.PhysicalChannel> list = new ArrayList<>();
        list.add(PhysicalChannels.from(13, 1, 41040, 20000, 41));
        list.add(PhysicalChannels.from(20, 2, 647328, 100000, 77));
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 41040, new int[]{20000, 100000}, 1000);
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc,
                new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NR_ADVANCED, 1000), null, null, list, 1000);
        RadioAssessment a = RadioStateReducer.reduce(s, 2000, 30_000);
        assertEquals(RadioAssessment.NrStatus.NSA_VERIFIED, a.nrStatus);
        assertEquals("EN-DC is not LTE CA", CaAssessment.Status.VERIFIED_NO_CA, a.ca.status);
        NrChannel nr = a.ca.components.get(1).nr;
        assertEquals("3709.92", Mhz.format(nr.frequencyKhz));
        assertArrayEquals("frequency alone is ambiguous", new int[]{77, 78}, nr.candidateBands);
        assertEquals(77, nr.band);
        assertEquals(Provenance.VERIFIED, nr.bandProvenance);
        assertEquals(41, a.ca.components.get(0).lte.band);
        assertEquals("2635.0", Mhz.format(a.ca.components.get(0).lte.downlinkKhz));
        assertEquals(Long.valueOf(120000), a.ca.aggregateBandwidthKhz);
        assertEquals("LTE 1 + NR 1（EN-DC）・LTE の CA なし",
                io.github.fortuneoflab.dualsimbands.ui.RadioText.ca(a.ca));
    }

    /** End to end: a probe result turns an indication into a verified composition with a total. */
    @Test public void probeResultVerifiesCa() {
        List<RadioSnapshot.PhysicalChannel> list = new ArrayList<>();
        list.add(PhysicalChannels.from(13, 1, 100, 20000));
        list.add(PhysicalChannels.from(13, 2, 1500, 20000));
        list.add(PhysicalChannels.from(13, 2, 41040, 20000));
        list.add(PhysicalChannels.from(20, 2, 643334, 100000));
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, new int[]{20000, 20000, 20000, 100000}, 1000);
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NR_NSA, 1000),
                null, null, list, 1000);
        RadioAssessment a = RadioStateReducer.reduce(s, 2000, 30_000);
        assertEquals(CaAssessment.Status.VERIFIED_CA, a.ca.status);
        assertEquals(4, a.ca.components.size());
        assertEquals(Long.valueOf(160000), a.ca.aggregateBandwidthKhz);
        assertEquals(41, a.ca.components.get(2).lte.band);
        assertEquals(RadioAssessment.NrStatus.NSA_VERIFIED, a.nrStatus);
        // 31 s later the same data no longer counts.
        RadioAssessment old = RadioStateReducer.reduce(s, 1000 + 30_001, 30_000);
        assertEquals(CaAssessment.Status.INDICATED, old.ca.status);
        assertEquals(RadioAssessment.NrStatus.INDICATED_UNCONFIRMED, old.nrStatus);
    }
}
