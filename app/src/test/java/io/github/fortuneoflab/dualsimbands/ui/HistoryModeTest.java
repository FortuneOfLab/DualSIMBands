package io.github.fortuneoflab.dualsimbands.ui;

import org.junit.Test;
import static org.junit.Assert.*;

/** History is recorded only until the chosen end, and only with the monitor on, which does the recording. */
public class HistoryModeTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test public void recordsUntilTheChosenEnd() {
        assertTrue(AppPrefs.historyOn(NOW + 60_000, true, NOW));
        assertFalse("the time is up", AppPrefs.historyOn(NOW, true, NOW));
        assertFalse("off", AppPrefs.historyOn(0, true, NOW));
    }

    @Test public void needsTheMonitor() {
        assertFalse(AppPrefs.historyOn(NOW + 60_000, false, NOW));
    }

    @Test public void durationsRunFrom15MinutesTo24Hours() {
        assertEquals(15, AppPrefs.HISTORY_MINUTES[0]);
        assertEquals(24 * 60, AppPrefs.HISTORY_MINUTES[AppPrefs.HISTORY_MINUTES.length - 1]);
    }

    /** nr_nsa_tracking_screen_off_mode is set only while recording with the composition read automatically. */
    @Test public void nrTrackingOnlyWhileRecordingTheComposition() {
        assertTrue(io.github.fortuneoflab.dualsimbands.NrTracking.wanted(true, true, true));
        assertFalse("composition only on the button", io.github.fortuneoflab.dualsimbands.NrTracking.wanted(true, true, false));
        assertFalse("composition off", io.github.fortuneoflab.dualsimbands.NrTracking.wanted(true, false, false));
        assertFalse("not recording", io.github.fortuneoflab.dualsimbands.NrTracking.wanted(false, true, true));
    }
}
