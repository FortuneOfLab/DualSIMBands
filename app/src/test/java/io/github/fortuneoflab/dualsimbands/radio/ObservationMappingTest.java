package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class ObservationMappingTest {
    private static final int U = Integer.MAX_VALUE;

    @Test public void unavailableLteMeasurementsBecomeUnknown() {
        LteCell c = LteCell.fromRaw(false, U, U, null, U, U, U, U, U, U, U, U, 5);
        assertEquals(CellRole.UNREGISTERED, c.role);
        assertEquals(-1, c.channel.earfcn);
        assertNull(c.pci); assertNull(c.tac); assertNull(c.ci); assertNull(c.bandwidthKhz);
        assertNull(c.rsrp); assertNull(c.rsrq); assertNull(c.rssi); assertNull(c.rssnr);
    }

    @Test public void outOfRangeValuesAreRejected() {
        LteCell c = LteCell.fromRaw(true, 0, 100, null, 504, -1, -1, 0, -141, 4, -50, 31, 5);
        assertNull(c.pci); assertNull(c.tac); assertNull(c.ci); assertNull(c.bandwidthKhz);
        assertNull(c.rsrp); assertNull(c.rsrq); assertNull(c.rssi); assertNull(c.rssnr);
        LteCell ok = LteCell.fromRaw(true, 0, 100, null, 503, 65535, 268435455, 20000, -140, -34, -113, -20, 5);
        assertEquals(Integer.valueOf(503), ok.pci);
        assertEquals(Integer.valueOf(20000), ok.bandwidthKhz);
        assertEquals(Integer.valueOf(-140), ok.rsrp);
    }

    @Test public void unavailableNrMeasurementsBecomeUnknown() {
        NrCell c = NrCell.fromRaw(false, U, U, null, U, U, Long.MAX_VALUE, U, U, U, 5);
        assertEquals(-1, c.channel.nrArfcn);
        assertNull(c.pci); assertNull(c.tac); assertNull(c.nci); assertNull(c.ssRsrp); assertNull(c.ssRsrq); assertNull(c.ssSinr);
    }

    @Test public void cellRoles() {
        assertEquals(CellRole.PRIMARY_SERVING, CellRole.from(true, 1));
        assertEquals(CellRole.SECONDARY_SERVING, CellRole.from(false, 2));
        assertEquals(CellRole.REGISTERED, CellRole.from(true, 0));
        assertEquals(CellRole.REGISTERED, CellRole.from(true, U));
        assertEquals(CellRole.UNREGISTERED, CellRole.from(false, 0));
        assertEquals(CellRole.UNREGISTERED, CellRole.from(false, U));
    }

    @Test public void networkTypes() {
        assertEquals(Rat.LTE, Rat.fromNetworkType(13));
        assertEquals(Rat.LTE, Rat.fromNetworkType(19));
        assertEquals(Rat.NR, Rat.fromNetworkType(20));
        assertEquals(Rat.IWLAN, Rat.fromNetworkType(18));
        assertEquals(Rat.UNKNOWN, Rat.fromNetworkType(0));
        assertEquals(Rat.UNKNOWN, Rat.fromNetworkType(99));
    }

    @Test public void displayOverrides() {
        assertEquals(DisplayOverride.NONE, DisplayOverride.fromPlatform(0));
        assertEquals(DisplayOverride.LTE_CA, DisplayOverride.fromPlatform(1));
        assertEquals(DisplayOverride.NR_NSA, DisplayOverride.fromPlatform(3));
        assertEquals(DisplayOverride.NR_ADVANCED, DisplayOverride.fromPlatform(5));
        assertEquals(DisplayOverride.UNKNOWN, DisplayOverride.fromPlatform(42));
        assertTrue(DisplayOverride.NR_NSA.indicatesNr());
        assertFalse(DisplayOverride.LTE_CA.indicatesNr());
    }

    @Test public void plmnAndTimingAdvance() {
        LteCell c = LteCell.fromRaw(true, 1, 100, null, 105, 12345, 1, 20000, -90, -10, -60, 10, "440", "51", 9, 5);
        assertEquals("440-51", c.plmn);
        assertEquals(Integer.valueOf(9), c.ta);
        LteCell neighbour = LteCell.fromRaw(false, 0, 100, null, 326, U, U, U, -110, -12, U, U, null, null, U, 5);
        assertNull("a neighbour's TA 0 is not reported",
                LteCell.fromRaw(false, 0, 100, null, 326, U, U, U, -110, -12, U, U, null, null, 0, 5).ta);
        assertNull("an SCC reports no TA of its own (docomo B19/B42 on the Pixel 10a)",
                LteCell.fromRaw(false, 2, 6075, null, 152, U, U, 15000, -93, -7, U, U, null, null, 0, 5).ta);
        assertEquals("a serving cell's TA 0 is real (very close)", Integer.valueOf(0),
                LteCell.fromRaw(true, 1, 100, null, 105, 1, 1, 20000, -80, -8, -50, 20, "440", "51", 0, 5).ta);
        assertNull(neighbour.plmn);
        assertNull(neighbour.ta);
        assertNull("out of range", LteCell.fromRaw(true, 1, 100, null, 1, 1, 1, 20000, -90, -10, -60, 10, "440", "51", 1283, 5).ta);
        assertNull(Measure.plmn("440", ""));
        assertEquals("310-260", Measure.plmn("310", "260"));
    }

    /** Pixel 10a, au EN-DC: the n28 leg reports TAC 0, NCI 0, TA 0. */
    @Test public void enDcLegZeroIdsAreUnknown() {
        NrCell leg = NrCell.fromRaw(false, 2, 155570, null, 51, 0, 0L, -102, -11, U, null, null, 0, 5);
        assertNull(leg.tac);
        assertNull(leg.nci);
        assertNull(leg.ta);
        assertEquals(Integer.valueOf(51), leg.pci);
        assertNull("SINR 0 without RSRP is a placeholder",
                NrCell.fromRaw(false, 2, 155570, null, 51, 0, 0L, U, U, 0, null, null, 0, 5).ssSinr);
        assertEquals("a measured leg keeps SINR 0 dB", Integer.valueOf(0),
                NrCell.fromRaw(false, 2, 155570, null, 51, 0, 0L, -100, -11, 0, null, null, 0, 5).ssSinr);
        NrCell sa = NrCell.fromRaw(true, 1, 643334, null, 1, 0, 0L, -95, -11, 5, "440", "51", 0, 5);
        assertEquals("a primary cell keeps its reported values", Integer.valueOf(0), sa.tac);
        // getTimingAdvanceMicros: kept as reported up to the raw TS 38.213 index range, whatever its unit.
        assertEquals(Integer.valueOf(3846), NrCell.fromRaw(true, 1, 643334, null, 1, 1, 1L, -95, -11, 5, null, null, 3846, 5).ta);
        assertNull(NrCell.fromRaw(true, 1, 643334, null, 1, 1, 1L, -95, -11, 5, null, null, 3847, 5).ta);
    }
}
