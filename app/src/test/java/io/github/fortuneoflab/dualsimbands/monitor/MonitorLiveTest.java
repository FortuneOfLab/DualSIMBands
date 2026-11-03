package io.github.fortuneoflab.dualsimbands.monitor;

import io.github.fortuneoflab.dualsimbands.PrivilegedProbe;
import org.junit.Test;
import static org.junit.Assert.*;

/** The monitor reads the live composition only while history is recorded and every other condition holds. */
public class MonitorLiveTest {
    @Test public void onlyWhileHistoryIsRecorded() {
        assertTrue(MonitorService.liveWanted(true, true, true, true, 2));
        assertFalse("history off", MonitorService.liveWanted(false, true, true, true, 2));
    }

    @Test public void theCompositionSwitchesAndShizukuAreNeeded() {
        assertFalse(MonitorService.liveWanted(true, false, true, true, 2));
        assertFalse(MonitorService.liveWanted(true, true, false, true, 2));
        assertFalse("Shizuku not ready", MonitorService.liveWanted(true, true, true, false, 2));
        assertFalse("no SIM", MonitorService.liveWanted(true, true, true, true, 0));
    }

    /** The notification says why the composition is missing only when the monitor wants it. */
    @Test public void blockedOnlyWhenTheCompositionIsWanted() {
        PrivilegedProbe.Block stopped = PrivilegedProbe.Block.SHIZUKU_NOT_RUNNING, none = PrivilegedProbe.Block.NONE;
        assertEquals(stopped, MonitorService.blocked(true, true, true, stopped));
        assertEquals(PrivilegedProbe.Block.SHIZUKU_NOT_GRANTED,
                MonitorService.blocked(true, true, true, PrivilegedProbe.Block.SHIZUKU_NOT_GRANTED));
        assertEquals("connected", none, MonitorService.blocked(true, true, true, none));
        assertEquals("history not Always", none, MonitorService.blocked(false, true, true, stopped));
        assertEquals("composition only with the button", none, MonitorService.blocked(true, true, false, stopped));
        assertEquals("composition off", none, MonitorService.blocked(true, false, false, stopped));
    }

    /** A last report is passed on as current only while its line's registration holds. */
    @Test public void aReportStaysCurrentOnlyWhileRegistered() {
        java.util.List<Integer> ids = java.util.Arrays.asList(2, 4);
        assertTrue(MonitorService.liveCurrent(true, ids, 2));
        assertFalse("re-registration failed", MonitorService.liveCurrent(true, new java.util.ArrayList<>(), 2));
        assertFalse("registration stopped", MonitorService.liveCurrent(false, ids, 2));
        assertFalse("a line no longer registered", MonitorService.liveCurrent(true, ids, 5));
    }

    /** Cells are read with the screen on, and with it off only while history is recorded. */
    @Test public void cellsWhileTheScreenIsOnOrHistoryIsRecorded() {
        assertTrue(MonitorService.cellsWanted(true, false));
        assertTrue("locked on a train with history on", MonitorService.cellsWanted(false, true));
        assertFalse(MonitorService.cellsWanted(false, false));
    }

    /** Channel reports stop with the screen off on battery, even with traffic; charging or NR tracking 2 keeps them. */
    @Test public void channelReportsFlowOnlyWhenAndroidKeepsThem() {
        assertTrue(MonitorService.channelReportsFlow(true, false, 0));
        assertFalse(MonitorService.channelReportsFlow(false, false, 0));
        assertFalse("unreadable setting", MonitorService.channelReportsFlow(false, false, -1));
        assertTrue(MonitorService.channelReportsFlow(false, true, 0));
        assertTrue(MonitorService.channelReportsFlow(false, false, 2));
        assertFalse("extended: only while NR is connected, which is not known here", MonitorService.channelReportsFlow(false, false, 1));
    }
}
