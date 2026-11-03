package io.github.fortuneoflab.dualsimbands.radio;

import io.github.fortuneoflab.dualsimbands.history.HistorySample;
import org.junit.Test;
import static org.junit.Assert.*;

/** The ca_detail column of the history: the same carriers the bands screen draws. */
public class HistoryDetailTest {
    /**
     * A snapshot offered again with a later position is judged again at that time: once the cells have gone stale,
     * the composition is no longer recorded as verified.
     */
    @Test public void aStaleCompositionIsNotRecordedAgainAsVerified() {
        RadioSnapshot s = new Snapshots().lteCell(true, PRIMARY, 100, 20000).nrCell(false, SECONDARY, 647328).build();
        assertNotNull(HistorySample.detail(io.github.fortuneoflab.dualsimbands.history.HistoryRecorder.reassess(s, Snapshots.NOW).ca));
        RadioAssessment later = io.github.fortuneoflab.dualsimbands.history.HistoryRecorder.reassess(s, Snapshots.NOW + 60_000);
        assertTrue(later.cellsStale);
        assertNull(HistorySample.detail(later.ca));
    }

    /** CellInfo connection status, as in CaAnalyzerTest. */
    private static final int PRIMARY = 1, SECONDARY = 2;

    /** Each verified carrier as JSON: band, channel, bandwidth, PCI and the attributed signal. */
    @Test public void verifiedCarriersAreDetailed() throws Exception {
        assertNull("nothing verified", HistorySample.detail(new Snapshots().reduce().ca));
        RadioAssessment a = new Snapshots().lteCell(true, PRIMARY, 100, 20000)
                .nrCell(false, SECONDARY, 647328).reduce();
        org.json.JSONArray detail = new org.json.JSONArray(HistorySample.detail(a.ca));
        assertEquals(2, detail.length());
        assertEquals("PCC", detail.getJSONObject(0).getString("cc"));
        assertEquals("B1", detail.getJSONObject(0).getString("band"));
        assertEquals(100, detail.getJSONObject(0).getInt("arfcn"));
        assertEquals("20.0", detail.getJSONObject(0).getString("bw_mhz"));
        assertEquals("SCC", detail.getJSONObject(1).getString("cc"));
        assertEquals("NR", detail.getJSONObject(1).getString("rat"));
        assertEquals(647328, detail.getJSONObject(1).getInt("arfcn"));
        assertFalse("bandwidth unknown: left out", detail.getJSONObject(1).has("bw_mhz"));
    }
}
