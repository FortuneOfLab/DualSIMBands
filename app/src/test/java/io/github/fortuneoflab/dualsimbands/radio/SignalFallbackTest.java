package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SignalFallbackTest {
    private static final long NOW = Snapshots.NOW;

    private static LteCell lte(int connection, int rsrp, long at) {
        return LteCell.fromRaw(connection == 1, connection, 1850, null, 10, 1, 1, 20000, rsrp, -8, Integer.MAX_VALUE, 12, at);
    }

    private static NrCell nr(int connection, int ssRsrp, long at) {
        return NrCell.fromRaw(connection != 0, connection, 643334, null, 20, 1, 1L, ssRsrp, -11, 5, at);
    }

    private static RadioSnapshot.Cells cells(long at, List<LteCell> lte, List<NrCell> nr) {
        return new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, lte, nr, at);
    }

    private static List<LteCell> lteList(LteCell... c) { List<LteCell> l = new ArrayList<>(); for (LteCell x : c) l.add(x); return l; }
    private static List<NrCell> nrList(NrCell... c) { List<NrCell> l = new ArrayList<>(); for (NrCell x : c) l.add(x); return l; }

    private static final RadioSnapshot.Signal OLD = new RadioSnapshot.Signal(-80, -9, 6, -53, -95, -10, 3, NOW - 60_000);

    /** Screen off: SignalStrength stopped an hour ago, CellInfo of the serving cell is fresh. */
    @Test public void oldReportGivesWayToTheServingCell() {
        RadioSnapshot.Signal s = SignalFallback.effective(OLD,
                cells(NOW - 3_000, lteList(lte(1, -97, NOW - 3_000), lte(0, -110, NOW - 3_000)), nrList()), NOW);
        assertEquals(Integer.valueOf(-97), s.lteRsrp);
        assertEquals(Integer.valueOf(-8), s.lteRsrq);
        assertEquals(Integer.valueOf(12), s.lteRssnr);
        assertNull("CellInfo reported no RSSI", s.lteRssi);
        assertNull("the old NR entry is not carried over", s.nrSsRsrp);
        assertEquals(NOW - 3_000, s.observedAtMillis);
    }

    @Test public void recentReportIsKept() {
        RadioSnapshot.Signal recent = new RadioSnapshot.Signal(-80, -9, 6, -53, null, null, null, NOW - 5_000);
        assertSame(recent, SignalFallback.effective(recent, cells(NOW, lteList(lte(1, -97, NOW)), nrList()), NOW));
    }

    @Test public void reportNewerThanTheCellsIsKept() {
        assertSame(OLD, SignalFallback.effective(OLD, cells(NOW - 70_000, lteList(lte(1, -97, NOW - 70_000)), nrList()), NOW));
    }

    @Test public void noUniqueServingCellKeepsTheReport() {
        assertSame(OLD, SignalFallback.effective(OLD, cells(NOW, lteList(lte(0, -97, NOW)), nrList()), NOW));
        assertSame(OLD, SignalFallback.effective(OLD, cells(NOW, lteList(lte(1, -97, NOW), lte(1, -99, NOW)), nrList()), NOW));
        assertSame(OLD, SignalFallback.effective(OLD, RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.LOCATION_OFF), NOW));
    }

    @Test public void nsaLegComesFromItsServingNrCell() {
        RadioSnapshot.Signal s = SignalFallback.effective(OLD,
                cells(NOW, lteList(lte(1, -97, NOW)), nrList(nr(2, -101, NOW))), NOW);
        assertEquals(Integer.valueOf(-97), s.lteRsrp);
        assertEquals(Integer.valueOf(-101), s.nrSsRsrp);
    }

    @Test public void noReportYetUsesTheCells() {
        RadioSnapshot.Signal s = SignalFallback.effective(null, cells(NOW, lteList(lte(1, -97, NOW)), nrList()), NOW);
        assertEquals(Integer.valueOf(-97), s.lteRsrp);
    }

    /** An old serving cell listed with a fresh neighbour must not get the neighbour's time. */
    @Test public void oldServingCellWithAFreshNeighbourKeepsTheReport() {
        RadioSnapshot.Signal recent = new RadioSnapshot.Signal(-80, -9, 6, null, null, null, NOW - 25_000);
        RadioSnapshot.Cells mixed = cells(NOW - 1_000, lteList(lte(1, -97, NOW - 40_000), lte(0, -110, NOW - 1_000)), nrList());
        assertSame(recent, SignalFallback.effective(recent, mixed, NOW));
        assertSame("a cell older than the report", recent, SignalFallback.effective(recent,
                cells(NOW - 1_000, lteList(lte(1, -97, NOW - 26_000), lte(0, -110, NOW - 1_000)), nrList()), NOW));
    }

    /** LTE and NR are judged apart: a fresh LTE cell does not carry an old NR leg along. */
    @Test public void eachRatNeedsItsOwnFreshCell() {
        RadioSnapshot.Signal s = SignalFallback.effective(OLD,
                cells(NOW, lteList(lte(1, -97, NOW - 2_000)), nrList(nr(2, -101, NOW - 50_000))), NOW);
        assertEquals(Integer.valueOf(-97), s.lteRsrp);
        assertNull(s.nrSsRsrp);
        assertEquals("the time of the cell used", NOW - 2_000, s.observedAtMillis);
    }
}
