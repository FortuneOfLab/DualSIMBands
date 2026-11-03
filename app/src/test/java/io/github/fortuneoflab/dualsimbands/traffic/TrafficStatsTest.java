package io.github.fortuneoflab.dualsimbands.traffic;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrafficStatsTest {
    private static final long NOW = 1_000_000;

    @Test public void offRecordsNothing() {
        TrafficStats t = new TrafficStats();
        t.record(TrafficStats.Outcome.OK, 100, NOW);
        TrafficStats.Window w = t.peek(false, NOW);
        assertFalse(w.on);
        assertEquals(0, w.ok);
        assertNull(w.latencyMaxMs);
    }

    @Test public void drainStartsTheCountsAgainButKeepsAFailureRun() {
        TrafficStats t = new TrafficStats();
        t.record(TrafficStats.Outcome.OK, 300, NOW - 10_000);
        t.record(TrafficStats.Outcome.TIMEOUT, 0, NOW - 6_000);
        t.record(TrafficStats.Outcome.ERROR, 0, NOW - 2_000);
        TrafficStats.Window w = t.peek(true, NOW);
        assertEquals(1, w.ok);
        assertEquals(1, w.timeouts);
        assertEquals(1, w.errors);
        assertEquals(Long.valueOf(300), w.latencyMaxMs);
        assertEquals("failing since the timeout", Long.valueOf(6), w.failingForSeconds);
        t.drain(w);
        TrafficStats.Window next = t.peek(true, NOW + 4_000);
        assertEquals(0, next.ok);
        assertEquals(0, next.timeouts);
        assertNull("no answer in this window", next.latencyMaxMs);
        assertEquals(Long.valueOf(10), next.failingForSeconds);
    }

    /** A request finishing between the row's read and its write is kept for the next row. */
    @Test public void drainSubtractsOnlyWhatTheRowRecorded() {
        TrafficStats t = new TrafficStats();
        t.record(TrafficStats.Outcome.OK, 100, NOW);
        TrafficStats.Window w = t.peek(true, NOW);
        t.record(TrafficStats.Outcome.OK, 200, NOW + 1_000);
        t.drain(w);
        TrafficStats.Window next = t.peek(true, NOW + 2_000);
        assertEquals(1, next.ok);
        assertEquals("its time stays with it", Long.valueOf(200), next.latencyMaxMs);
    }

    @Test public void anAnswerEndsTheFailureRun() {
        TrafficStats t = new TrafficStats();
        t.record(TrafficStats.Outcome.TIMEOUT, 0, NOW - 20_000);
        t.record(TrafficStats.Outcome.OK, 900, NOW - 1_000);
        assertNull(t.peek(true, NOW).failingForSeconds);
        assertFalse(t.peek(true, NOW).failing());
    }

    /** A failure written in the same second as it happened still reads as failing, never as 0 (not failing). */
    @Test public void aFreshFailureIsAtLeastOneSecond() {
        TrafficStats t = new TrafficStats();
        t.record(TrafficStats.Outcome.TIMEOUT, 0, NOW);
        assertEquals(Long.valueOf(1), t.peek(true, NOW).failingForSeconds);
        assertEquals("rounded up", Long.valueOf(3), t.peek(true, NOW + 2_100).failingForSeconds);
    }

    /** A request sent before the data SIM changed and finishing after it is not counted for the new SIM. */
    @Test public void requestsSentBeforeARestartAreLeftOut() {
        TrafficStats t = new TrafficStats();
        t.restart(NOW);
        t.record(TrafficStats.Outcome.TIMEOUT, 0, NOW + 5_000, NOW - 1_000);
        t.record(TrafficStats.Outcome.OK, 300, NOW + 6_000, NOW + 1_000);
        TrafficStats.Window w = t.peek(true, NOW + 7_000);
        assertEquals(0, w.timeouts);
        assertEquals(1, w.ok);
        assertNull(w.failingForSeconds);
    }

    /** With no row taking them (the bands screen alone), the kept times stop at the cap while the count stays right. */
    @Test public void keptTimesAreCappedButCounted() {
        TrafficStats t = new TrafficStats();
        for (int k = 0; k < TrafficStats.MAX_KEPT + 5; k++) t.record(TrafficStats.Outcome.OK, k, NOW);
        TrafficStats.Window w = t.peek(true, NOW);
        assertEquals(TrafficStats.MAX_KEPT + 5, w.ok);
        assertEquals(Long.valueOf(TrafficStats.MAX_KEPT + 4), w.latencyMaxMs);
        t.record(TrafficStats.Outcome.OK, 7, NOW + 1);
        t.drain(w);
        TrafficStats.Window next = t.peek(true, NOW + 2);
        assertEquals(1, next.ok);
        assertEquals(Long.valueOf(7), next.latencyMaxMs);
    }

    /** Recording starts afresh: a slow answer let go beyond the cap no longer leaves the count and the slowest apart. */
    @Test public void restartLinesTheCountAndTheSlowestUpAgain() {
        TrafficStats t = new TrafficStats();
        t.record(TrafficStats.Outcome.OK, 9_000, NOW);
        for (int k = 0; k < TrafficStats.MAX_KEPT + 10; k++) t.record(TrafficStats.Outcome.OK, 100, NOW);
        t.restart(NOW + 1);
        t.record(TrafficStats.Outcome.OK, 200, NOW + 2, NOW + 2);
        TrafficStats.Window w = t.peek(true, NOW + 3);
        assertEquals(1, w.ok);
        assertEquals(Long.valueOf(200), w.latencyMaxMs);
    }
}
