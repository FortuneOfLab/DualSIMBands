package io.github.fortuneoflab.dualsimbands;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaitingApplyTest {
    /** A lock like Coordinator's, counting the applies started; a started apply keeps the lock until finish(). */
    private static class Gate implements WaitingApply.Gate {
        final AtomicBoolean lock;
        boolean auto = true;
        int starts, releases;
        /** Runs inside tryAcquire, before the lock is taken: another round, or the switch turned off, meanwhile. */
        Runnable beforeAcquire;

        Gate(AtomicBoolean lock) { this.lock = lock; }
        @Override public boolean tryAcquire() {
            Runnable r = beforeAcquire;
            beforeAcquire = null;
            if (r != null) r.run();
            return lock.compareAndSet(false, true);
        }
        @Override public void release() { releases++; lock.set(false); }
        @Override public boolean autoOn() { return auto; }
        @Override public void start() { starts++; }
        void finish() { lock.set(false); }
    }

    @Test public void aScreenChangeStartsOneApply() throws Exception {
        WaitingApply w = new WaitingApply();
        Gate g = new Gate(new AtomicBoolean());
        w.request(false);
        assertTrue(w.runIfWanted(g));
        g.finish();
        assertFalse(w.runIfWanted(g));
        assertEquals(1, g.starts);
    }

    /** Unsettled task, restore waits, automatic restore turned off, the old task ends: no restore runs. */
    @Test public void restoreAloneIsDroppedOnceAutomaticRestoreIsOff() throws Exception {
        WaitingApply w = new WaitingApply();
        Gate g = new Gate(new AtomicBoolean());
        w.request(true);
        g.auto = false;
        assertFalse(w.runIfWanted(g));
        assertEquals(0, g.starts);
        assertEquals(1, g.releases);
        assertFalse(w.pending());
    }

    /** Automatic restore was on when the request was checked, and turned off before the lock was taken. */
    @Test public void automaticRestoreTurnedOffBeforeTheLockStartsNothing() throws Exception {
        WaitingApply w = new WaitingApply();
        Gate g = new Gate(new AtomicBoolean());
        w.request(true);
        g.beforeAcquire = () -> g.auto = false;
        assertFalse(w.runIfWanted(g));
        assertEquals(0, g.starts);
        assertFalse(g.lock.get());
    }

    @Test public void aScreenChangeStartsWithAutomaticRestoreOff() throws Exception {
        WaitingApply w = new WaitingApply();
        Gate g = new Gate(new AtomicBoolean());
        w.request(true);
        w.request(false);
        g.auto = false;
        assertTrue(w.runIfWanted(g));
        assertEquals(1, g.starts);
    }

    /** A passed the check; B took the same request, applied and let go; then A gets the lock: no second apply. */
    @Test public void aRequestAnotherRoundTookStartsNothing() throws Exception {
        WaitingApply w = new WaitingApply();
        AtomicBoolean lock = new AtomicBoolean();
        Gate a = new Gate(lock), b = new Gate(lock);
        w.request(false);
        a.beforeAcquire = () -> {
            try { assertTrue(w.runIfWanted(b)); } catch (Exception e) { throw new AssertionError(e); }
            b.finish();
        };
        assertFalse(w.runIfWanted(a));
        assertEquals(1, b.starts);
        assertEquals(0, a.starts);
        assertFalse(lock.get());
    }

    /** Held while another task has the lock, then started once it is free; one saved during the apply waits. */
    @Test public void aRequestWaitsForTheLockAndOneMadeMeanwhileIsKept() throws Exception {
        WaitingApply w = new WaitingApply();
        AtomicBoolean lock = new AtomicBoolean(true); // an eSIM change holds it
        Gate g = new Gate(lock);
        w.request(false);
        assertFalse(w.runIfWanted(g));
        assertTrue(w.pending());
        lock.set(false);
        assertTrue(w.runIfWanted(g));
        w.request(false);                              // saved while that apply runs
        assertFalse(w.runIfWanted(g));                 // refused: the apply still holds the lock
        g.finish();
        assertTrue(w.runIfWanted(g));
        assertEquals(2, g.starts);
    }

    /** A start that fails gives the lock back. */
    @Test public void aFailedStartGivesTheLockBack() {
        WaitingApply w = new WaitingApply();
        Gate g = new Gate(new AtomicBoolean()) {
            @Override public void start() { throw new IllegalStateException("thread refused"); }
        };
        w.request(false);
        try { w.runIfWanted(g); fail(); } catch (Exception expected) { }
        assertFalse(g.lock.get());
    }
}
