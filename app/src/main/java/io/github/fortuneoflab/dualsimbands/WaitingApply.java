package io.github.fortuneoflab.dualsimbands;

/**
 * A request to apply the saved settings once the lock is free, kept apart by who made it: a change on the screen, or
 * automatic restore. An automatic one alone is dropped once automatic restore has been turned off; a change on the
 * screen is applied either way. Whether to run is decided only once the lock is held, so neither a setting changed
 * meanwhile nor a request another round already took can start an apply.
 */
final class WaitingApply {
    /** The lock, the setting and the start of an apply, as Coordinator provides them. */
    interface Gate {
        boolean tryAcquire();
        /** Gives the lock back when no apply starts. */
        void release();
        boolean autoOn();
        /** Starts the apply under the lock just taken; it gives the lock back when done. */
        void start() throws Exception;
    }

    private boolean manual, restore;

    synchronized void request(boolean byRestore) {
        if (byRestore) restore = true; else manual = true;
    }

    synchronized boolean pending() { return manual || restore; }

    synchronized void clear() { manual = false; restore = false; }

    /**
     * Takes every request held: whether an apply should run for them, with automatic restore {@code autoOn} or off.
     * An automatic request that is no longer wanted is dropped; one made after this waits for the next round.
     */
    synchronized boolean take(boolean autoOn) {
        boolean run = manual || (restore && autoOn);
        manual = false; restore = false;
        return run;
    }

    /** Starts an apply for the requests held, if any is still wanted once the lock is taken. @return whether it started */
    boolean runIfWanted(Gate gate) throws Exception {
        if (!pending() || !gate.tryAcquire()) return false;
        boolean started = false;
        try {
            if (take(gate.autoOn())) { gate.start(); started = true; }
        } finally {
            if (!started) gate.release();
        }
        return started;
    }
}
