package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComponentSignalTest {
    private static final long NOW = Snapshots.NOW;

    private static LteCell lte(int connection, int earfcn, int pci, int rsrp) {
        return LteCell.fromRaw(connection == 1, connection, earfcn, null, pci, 1, 1, 20000, rsrp, -11, -60, 8, NOW);
    }

    /** Pixel 10a, au: B41 PCC + B41 SCC (same PCI, other EARFCN) + NR n77. */
    private static Snapshots enDcWithCa() {
        Snapshots b = new Snapshots();
        b.physical = new ArrayList<>();
        b.physical.add(PhysicalChannels.from(13, 1, 41040, 20000, 41, 249));
        b.physical.add(PhysicalChannels.from(13, 2, 40842, 20000, 41, 249));
        b.physical.add(PhysicalChannels.from(20, 2, 647328, 100000, 77, 603));
        b.signal = new RadioSnapshot.Signal(-102, -7, 7, -98, -10, 12, NOW);
        b.cellStatus = RadioSnapshot.Cells.Status.OK;
        b.lte.add(lte(1, 41040, 249, -101));
        return b;
    }

    @Test public void pccAndSingleNrUseSignalStrength() {
        Snapshots b = enDcWithCa();
        b.lte.add(lte(2, 40842, 249, -95));
        CaAssessment ca = b.reduce().ca;
        assertEquals(Integer.valueOf(-102), ca.components.get(0).signal.rsrp);
        assertEquals(Integer.valueOf(7), ca.components.get(0).signal.sinr);
        assertEquals(Integer.valueOf(-98), ca.components.get(2).signal.rsrp);
    }

    @Test public void sccUsesTheServingMarkedCellOnItsChannel() {
        Snapshots b = enDcWithCa();
        b.lte.add(lte(0, 40842, 101, -110)); // a neighbour on the SCC's frequency
        b.lte.add(lte(2, 40842, 249, -95));
        assertEquals(Integer.valueOf(-95), b.reduce().ca.components.get(1).signal.rsrp);
    }

    @Test public void unmarkedCellNeedsTheSamePci() {
        Snapshots b = enDcWithCa();
        b.lte.add(lte(0, 40842, 249, -96));
        assertEquals(Integer.valueOf(-96), b.reduce().ca.components.get(1).signal.rsrp);

        Snapshots other = enDcWithCa();
        other.lte.add(lte(0, 40842, 101, -110));
        assertNull("a different cell on the same frequency is not the SCC", other.reduce().ca.components.get(1).signal);
    }

    @Test public void staleCellsAreNotUsed() {
        Snapshots b = enDcWithCa();
        b.cellsAt = NOW - Snapshots.STALE_AFTER - 1;
        b.lte.clear();
        b.lte.add(LteCell.fromRaw(false, 2, 40842, null, 249, 1, 1, 20000, -95, -11, -60, 8, b.cellsAt));
        assertNull(b.reduce().ca.components.get(1).signal);
    }

    @Test public void severalNrComponentsAreNotGivenTheSingleNrReport() {
        Snapshots b = new Snapshots();
        b.physical = new ArrayList<>();
        b.physical.add(PhysicalChannels.from(13, 1, 41040, 20000, 41, 249));
        b.physical.add(PhysicalChannels.from(20, 2, 647328, 100000, 77, 603));
        b.physical.add(PhysicalChannels.from(20, 2, 650000, 40000, 78, 604));
        b.signal = new RadioSnapshot.Signal(-102, -7, 7, -98, -10, 12, NOW);
        CaAssessment ca = b.reduce().ca;
        assertNull(ca.components.get(1).signal);
        assertNull(ca.components.get(2).signal);
    }

    /** Observed: PhysicalChannelConfig listed the B42 SCC before the PCC. */
    @Test public void pccComesFirstThenLteSccsThenNr() {
        Snapshots b = new Snapshots();
        b.physical = new ArrayList<>();
        b.physical.add(PhysicalChannels.from(20, 2, 647328, 100000, 77));
        b.physical.add(PhysicalChannels.from(13, 2, 43090, 20000, 42));
        b.physical.add(PhysicalChannels.from(13, 1, 100, 20000, 1));
        CaAssessment ca = b.reduce().ca;
        assertTrue(ca.components.get(0).primary);
        assertEquals(100, ca.components.get(0).lte.earfcn);
        assertEquals(43090, ca.components.get(1).lte.earfcn);
        assertEquals(Rat.NR, ca.components.get(2).rat);
    }

    /** PCC RSSI comes from SignalStrength; an SCC's from its own cell; NR has none. */
    @Test public void rssiFollowsTheSameSources() {
        Snapshots b = enDcWithCa();
        b.signal = new RadioSnapshot.Signal(-102, -7, 7, -77, -98, -10, 12, NOW);
        b.lte.add(LteCell.fromRaw(false, 2, 40842, null, 249, 1, 1, 20000, -95, -11, -73, 8, NOW));
        CaAssessment ca = b.reduce().ca;
        assertEquals(Integer.valueOf(-77), ca.components.get(0).signal.rssi);
        assertEquals(Integer.valueOf(-73), ca.components.get(1).signal.rssi);
        assertNull(ca.components.get(2).signal.rssi);
    }

    /** Checked against the modem's own PCC RSSI on the Pixel 10a (2026-10-21): within 1 dB. */
    @Test public void rssiFromRsrqMatchesTheReportedValue() {
        assertEquals(Integer.valueOf(-80), ComponentSignal.lteRssi(-107, -7, 20000));  // au B1, reported -79
        assertEquals(Integer.valueOf(-64), ComponentSignal.lteRssi(-94, -10, 20000));  // docomo B3, reported -65
        assertEquals(Integer.valueOf(-59), ComponentSignal.lteRssi(-93, -15, 15000));  // 75 RBs
        assertNull(ComponentSignal.lteRssi(-93, null, 15000));
        assertNull("not an LTE bandwidth", ComponentSignal.lteRssi(-93, -15, 100000));
    }

    @Test public void sccWithoutReportedRssiGetsADerivedOne() {
        Snapshots b = enDcWithCa();
        b.lte.add(LteCell.fromRaw(false, 2, 40842, null, 249, 1, 1, 20000, -95, -11, U_RSSI, 8, NOW));
        CaAssessment ca = b.reduce().ca;
        ComponentSignal scc = ca.components.get(1).signal;
        assertEquals(Integer.valueOf(-64), scc.rssi);  // -95 + 11 + 20
        assertTrue(scc.rssiDerived);
        assertNull("NR has no RSSI", ca.components.get(2).signal.rssi);
    }

    private static final int U_RSSI = Integer.MAX_VALUE;

    /** An old cell on the SCC's channel does not lend its signal because a fresh cell elsewhere dates the list. */
    @Test public void oldMatchingCellGivesNoSignal() {
        Snapshots b = enDcWithCa();
        b.lte.add(LteCell.fromRaw(false, 2, 40842, null, 249, 1, 1, 20000, -95, -11, -60, 8, NOW - 60_000));
        assertNull(b.reduce().ca.components.get(1).signal);
    }
}
