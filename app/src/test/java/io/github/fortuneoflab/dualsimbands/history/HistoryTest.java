package io.github.fortuneoflab.dualsimbands.history;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import io.github.fortuneoflab.dualsimbands.radio.DisplayOverride;
import io.github.fortuneoflab.dualsimbands.radio.LteCell;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.RadioStateReducer;
import io.github.fortuneoflab.dualsimbands.radio.Rat;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryTest {
    private static final long NOW = 100_000;
    private static final TimeZone JST = TimeZone.getTimeZone("Asia/Tokyo");

    private static HistorySample sample(DisplayOverride display, int[] bandwidths, boolean includeIds) {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, bandwidths, NOW);
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, Integer.MAX_VALUE, 100, new int[]{1, 3, 42}, 105, 12345, 12345678, 20000,
                -100, -6, -70, 10, NOW - 3_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, display, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW - 3_000), null, -1);
        RadioAssessment a = RadioStateReducer.reduce(s, NOW, 30_000);
        return HistorySample.of(1_760_900_000_000L, JST, 0, "KDDI, au", s, a, NOW, includeIds);
    }

    private static String col(HistorySample s, String name) {
        for (int i = 0; i < HistorySample.COLUMNS.length; i++) if (HistorySample.COLUMNS[i].equals(name)) return s.values[i];
        throw new AssertionError("no column " + name);
    }

    @Test public void everyColumnHasAValueSlot() {
        assertEquals(HistorySample.COLUMNS.length, sample(DisplayOverride.NR_NSA, null, false).values.length);
    }

    @Test public void rowKeepsProvenanceAndUnits() {
        HistorySample s = sample(DisplayOverride.NR_NSA, new int[]{20000, 20000, 100000}, false);
        assertEquals("1", col(s, "schema_version"));
        assertEquals("2025-10-20T03:53:20+09:00", col(s, "time_local"));
        assertEquals("NR_NSA", col(s, "display_override"));
        assertEquals("LTE", col(s, "data_rat"));
        assertEquals("VERIFIED", col(s, "data_rat_source"));
        assertEquals("INDICATED_UNCONFIRMED", col(s, "nr_status"));
        assertEquals("INDICATED", col(s, "nr_source"));
        assertEquals("INDICATED", col(s, "ca_status"));
        assertNull("no verified components", col(s, "ca_components"));
        assertEquals("20.0+20.0+100.0", col(s, "reported_bandwidths_mhz"));
        assertEquals("B1", col(s, "serving_band"));
        assertEquals("VERIFIED", col(s, "serving_band_source"));
        assertEquals("2120.0", col(s, "serving_dl_mhz"));
        assertEquals("1930.0", col(s, "serving_ul_mhz"));
        assertEquals("-107", col(s, "lte_rsrp_dbm"));
        assertNull(col(s, "nr_ss_rsrp_dbm"));
        assertEquals("3", col(s, "cells_age_s"));
    }

    @Test public void cellIdentifiersNeedTheSeparateOptIn() {
        HistorySample off = sample(DisplayOverride.NONE, null, false);
        // The PCI alone does not locate a cell (it is reused nearby); it is recorded as the screen always shows it.
        assertEquals("105", col(off, "serving_pci"));
        assertEquals(HistoryCsv.NOT_RECORDED, col(off, "serving_tac"));
        assertEquals(HistoryCsv.NOT_RECORDED, col(off, "serving_cell_id"));
        HistorySample on = sample(DisplayOverride.NONE, null, true);
        assertEquals("105", col(on, "serving_pci"));
        assertEquals("12345", col(on, "serving_tac"));
        assertEquals("12345678", col(on, "serving_cell_id"));
        assertEquals("no TA reported", null, col(on, "serving_ta"));
        assertEquals(HistoryCsv.NOT_RECORDED, col(off, "serving_ta"));
    }

    /** TA is recorded with the cell IDs, as reported (LTE steps), with no distance column. */
    @Test public void timingAdvanceNeedsTheSameOptIn() {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, Integer.MAX_VALUE, 100, new int[]{1}, 105, 12345, 12345678, 20000,
                -100, -6, -70, 10, "440", "51", 9, NOW - 3_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW - 3_000), null, -1);
        RadioAssessment a = RadioStateReducer.reduce(s, NOW, 30_000);
        HistorySample on = HistorySample.of(1_760_900_000_000L, JST, 0, "KDDI, au", s, a, NOW, true);
        assertEquals("9", col(on, "serving_ta"));
        assertFalse(java.util.Arrays.asList(HistorySample.COLUMNS).contains("serving_ta_m"));
        HistorySample off = HistorySample.of(1_760_900_000_000L, JST, 0, "KDDI, au", s, a, NOW, false);
        assertEquals(HistoryCsv.NOT_RECORDED, col(off, "serving_ta"));
    }

    /** A handover to another cell (another PCI) on the same band is a state change. */
    @Test public void signatureTracksTheServingCell() {
        HistorySample a = withServingPci(105), b = withServingPci(106);
        assertNotEquals(a.signature, b.signature);
        assertEquals(a.signature, withServingPci(105).signature);
    }

    private static HistorySample withServingPci(int pci) {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, 1, 100, new int[]{1}, pci, 12345, 12345678, 20000, -100, -6, -70, 10, NOW - 3_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW - 3_000), null, -1);
        return HistorySample.of(1_760_900_000_000L, JST, 0, "KDDI, au", s, RadioStateReducer.reduce(s, NOW, 30_000), NOW, false);
    }

    @Test public void signatureIgnoresSignalButTracksDisplay() {
        assertNotEquals(sample(DisplayOverride.NR_NSA, null, false).signature,
                sample(DisplayOverride.LTE_CA, null, false).signature);
        assertEquals(sample(DisplayOverride.NR_NSA, null, false).signature,
                sample(DisplayOverride.NR_NSA, null, true).signature);
    }

    @Test public void csvEscapingAndUnknowns() {
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\",unknown,unknown",
                HistoryCsv.line(new String[]{"a", "b,c", "say \"hi\"", null, ""}));
        String row = HistoryCsv.line(sample(DisplayOverride.NR_NSA, null, false).values);
        assertTrue(row.contains("\"KDDI, au\""));
        assertFalse(row.contains("null"));
        assertTrue(HistoryCsv.header().startsWith("schema_version,time_local,time_epoch_ms,sim_slot,carrier"));
        assertEquals(0xFEFF, HistoryCsv.BOM.charAt(0));
    }

    @Test public void recordingPolicy() {
        assertTrue(HistoryPolicy.shouldRecord(null, 0, "a", 1_000));
        assertFalse("too soon even if changed", HistoryPolicy.shouldRecord("a", 1_000, "b", 5_999));
        assertTrue("changed after 5 s", HistoryPolicy.shouldRecord("a", 1_000, "b", 6_000));
        assertFalse("unchanged within a minute", HistoryPolicy.shouldRecord("a", 1_000, "a", 60_999));
        assertTrue("heartbeat", HistoryPolicy.shouldRecord("a", 1_000, "a", 61_000));
    }

    /** While the position is recorded, every 50 m moved also gives a row (still at most one per 5 s). */
    @Test public void movingFiftyMetresRecords() {
        assertTrue(HistoryPolicy.shouldRecord("a", 1_000, "a", 6_000, 50.0));
        assertFalse("not far enough", HistoryPolicy.shouldRecord("a", 1_000, "a", 6_000, 49.0));
        assertFalse("too soon", HistoryPolicy.shouldRecord("a", 1_000, "a", 5_999, 500.0));
        assertFalse("unknown distance", HistoryPolicy.shouldRecord("a", 1_000, "a", 6_000, null));
    }

    /** A move is judged only from precise fixes, and only beyond both accuracy radii together. */
    @Test public void positionNoiseIsNotAMove() {
        assertEquals(Double.valueOf(60), HistoryPolicy.usableDistance(60, 8f, 10f));
        assertNull("within both radii", HistoryPolicy.usableDistance(60, 35f, 30f));
        assertNull("too coarse", HistoryPolicy.usableDistance(300, 8f, 80f));
        assertNull("accuracy unknown", HistoryPolicy.usableDistance(300, null, 8f));
        assertTrue(HistoryPolicy.usable(50f));
        assertFalse(HistoryPolicy.usable(50.5f));
    }

    @Test public void retentionCutoff() {
        assertEquals(1_000_000_000L - 7L * 24 * 60 * 60_000, HistoryPolicy.cutoff(1_000_000_000L, 7));
    }

    /** A secondary cell listed first must not stand in for the primary: PCI, TAC, cell ID and TA come from the primary. */
    @Test public void servingCellIsThePrimaryWhateverTheOrder() {
        LteCell scc = LteCell.fromRaw(false, 2, 1850, new int[]{3}, 107, 23456, 23456789, 20000,
                -90, -9, -60, 1, "440", "10", Integer.MAX_VALUE, NOW);
        LteCell pcc = LteCell.fromRaw(true, 1, 100, new int[]{1}, 105, 12345, 12345678, 20000,
                -98, -6, -73, 13, "440", "51", 9, NOW);
        List<LteCell> sccFirst = new ArrayList<>(); sccFirst.add(scc); sccFirst.add(pcc);
        List<LteCell> pccFirst = new ArrayList<>(); pccFirst.add(pcc); pccFirst.add(scc);
        assertSame(pcc, HistorySample.servingLte(sccFirst, 100));
        assertSame(pcc, HistorySample.servingLte(pccFirst, 100));
        assertSame("channel unknown: the primary still", pcc, HistorySample.servingLte(sccFirst, null));
    }

    /**
     * NSA with the LTE cell missing and only the NR leg listed: the NR cell does not stand in, so the TA (whose unit
     * serving_rat tells) and the other cell values stay unknown.
     */
    @Test public void anNrLegNeverStandsInForTheLteCell() {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        List<io.github.fortuneoflab.dualsimbands.radio.NrCell> nrCells = new ArrayList<>();
        nrCells.add(io.github.fortuneoflab.dualsimbands.radio.NrCell.fromRaw(false, 2, 647328, null, 603, 12345,
                1234L, -98, -12, 5, "440", "51", 2, NOW - 3_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NR_NSA, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, new ArrayList<>(), nrCells, NOW - 3_000), null, -1);
        RadioAssessment a = RadioStateReducer.reduce(s, NOW, 30_000);
        HistorySample row = HistorySample.of(1_760_900_000_000L, JST, 0, "KDDI, au", s, a, NOW, true);
        assertEquals("LTE", col(row, "serving_rat"));
        assertNull(col(row, "serving_pci"));
        assertNull(col(row, "serving_ta"));
    }

    /** Without a primary status, the only registered cell on the serving channel; none when it is not unique. */
    @Test public void servingCellWithoutAPrimaryStatus() {
        LteCell a = LteCell.fromRaw(true, Integer.MAX_VALUE, 100, new int[]{1}, 105, 12345, 12345678, 20000,
                -98, -6, -73, 13, NOW);
        LteCell b = LteCell.fromRaw(true, Integer.MAX_VALUE, 1850, new int[]{3}, 107, 23456, 23456789, 20000,
                -90, -9, -60, 1, NOW);
        List<LteCell> both = new ArrayList<>(); both.add(b); both.add(a);
        assertSame(a, HistorySample.servingLte(both, 100));
        assertNull("two registered cells, channel unknown", HistorySample.servingLte(both, null));
    }

    /** Location is a separate opt-in: not recorded while off, unknown without a fix, the fix when there is one. */
    @Test public void locationNeedsItsOwnOptIn() {
        HistorySample off = sample(DisplayOverride.NONE, null, true);
        for (String c : new String[]{"lat", "lon", "location_accuracy_m", "location_age_s"})
            assertEquals(c, HistoryCsv.NOT_RECORDED, col(off, c));
        HistorySample noFix = withPosition(null);
        assertNull(col(noFix, "lat"));
        HistorySample on = withPosition(new HistorySample.Position(35.681236, 139.767125, 8.4f, 2));
        assertEquals("35.681236", col(on, "lat"));
        assertEquals("139.767125", col(on, "lon"));
        assertEquals("8", col(on, "location_accuracy_m"));
        assertEquals("2", col(on, "location_age_s"));
        assertEquals("the position does not make a state change", sample(DisplayOverride.NONE, null, false).signature, on.signature);
    }

    private static HistorySample withPosition(HistorySample.Position p) {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, Integer.MAX_VALUE, 100, new int[]{1, 3, 42}, 105, 12345, 12345678, 20000,
                -100, -6, -70, 10, NOW - 3_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW - 3_000), null, -1);
        RadioAssessment a = RadioStateReducer.reduce(s, NOW, 30_000);
        return HistorySample.of(1_760_900_000_000L, JST, 0, "KDDI, au", s, a, NOW, false, true, p);
    }

    private static HistorySample withTraffic(io.github.fortuneoflab.dualsimbands.traffic.TrafficStats.Window w) {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW), null, null, -1);
        RadioAssessment a = RadioStateReducer.reduce(s, NOW, 30_000);
        return HistorySample.of(1_760_900_000_000L, JST, 0, "docomo", s, a, NOW, false, false, null, w);
    }

    private static final String[] TRAFFIC = {"traffic_ok", "traffic_timeouts", "traffic_errors", "traffic_latency_ms_max",
            "traffic_failing_s", "net_validated"};

    /** The other SIM's rows and rows without traffic: not_recorded, never 0 requests that look like a quiet network. */
    @Test public void trafficColumnsOnlyForTheDataSim() {
        HistorySample other = withTraffic(null);
        assertEquals(HistoryCsv.NOT_RECORDED, col(other, "traffic_on"));
        for (String c : TRAFFIC) assertEquals(c, HistoryCsv.NOT_RECORDED, col(other, c));
        HistorySample off = withTraffic(new io.github.fortuneoflab.dualsimbands.traffic.TrafficStats().peek(false, NOW));
        assertEquals("false", col(off, "traffic_on"));
        for (String c : TRAFFIC) assertEquals(c, HistoryCsv.NOT_RECORDED, col(off, c));
    }

    @Test public void trafficResultsAndAFailureRun() {
        io.github.fortuneoflab.dualsimbands.traffic.TrafficStats t = new io.github.fortuneoflab.dualsimbands.traffic.TrafficStats();
        t.record(io.github.fortuneoflab.dualsimbands.traffic.TrafficStats.Outcome.OK, 120, NOW - 9_000);
        t.record(io.github.fortuneoflab.dualsimbands.traffic.TrafficStats.Outcome.OK, 480, NOW - 7_000);
        t.setValidated(true);
        HistorySample ok = withTraffic(t.peek(true, NOW));
        assertEquals("true", col(ok, "traffic_on"));
        assertEquals("2", col(ok, "traffic_ok"));
        assertEquals("0", col(ok, "traffic_timeouts"));
        assertEquals("480", col(ok, "traffic_latency_ms_max"));
        assertEquals("0", col(ok, "traffic_failing_s"));
        assertEquals("true", col(ok, "net_validated"));
        t.record(io.github.fortuneoflab.dualsimbands.traffic.TrafficStats.Outcome.TIMEOUT, 0, NOW - 4_000);
        HistorySample failing = withTraffic(t.peek(true, NOW));
        assertEquals("1", col(failing, "traffic_timeouts"));
        assertEquals("4", col(failing, "traffic_failing_s"));
        assertNotEquals("a failure run starting is a state change", ok.signature, failing.signature);
    }

    /** A stale cell list's serving cell is another time's: its PCI, TAC, cell ID and TA are left unknown. */
    @Test public void staleCellsGiveNoServingCell() {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, 1, 100, new int[]{1}, 105, 12345, 12345678, 20000, -100, -6, -70, 10,
                "440", "10", 3, NOW - 60_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW - 60_000), null, -1);
        HistorySample h = HistorySample.of(1_760_900_000_000L, JST, 0, "docomo", s, RadioStateReducer.reduce(s, NOW, 30_000),
                NOW, true);
        assertEquals("true", col(h, "cells_stale"));
        assertEquals("60", col(h, "cells_age_s"));
        assertNull(col(h, "serving_pci"));
        assertNull(col(h, "serving_tac"));
        assertNull(col(h, "serving_cell_id"));
        assertNull(col(h, "serving_ta"));
    }

    /** The list's time is its newest cell's: an old serving cell listed with a fresh neighbour is not recorded. */
    @Test public void oldServingCellWithAFreshNeighbourGivesNoServingCell() {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, null, NOW);
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, 1, 100, new int[]{1}, 105, 12345, 12345678, 20000, -100, -6, -70, 10,
                "440", "10", 3, NOW - 60_000));
        cells.add(LteCell.fromRaw(false, 0, 100, new int[]{1}, 200, Integer.MAX_VALUE, Integer.MAX_VALUE, 20000,
                -110, -12, -80, 2, NOW - 1_000));
        RadioSnapshot s = new RadioSnapshot(2, 0, true, svc, new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, NOW),
                new RadioSnapshot.Signal(-107, -8, 12, null, null, null, NOW),
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW - 1_000), null, -1);
        HistorySample h = HistorySample.of(1_760_900_000_000L, JST, 0, "docomo", s, RadioStateReducer.reduce(s, NOW, 30_000),
                NOW, true);
        assertEquals("the list counts as fresh", "false", col(h, "cells_stale"));
        assertNull(col(h, "serving_pci"));
        assertNull(col(h, "serving_ta"));
    }
}
