package io.github.fortuneoflab.dualsimbands.traffic;

/**
 * The generated traffic shown for the data SIM in use. The generator counts one total for its run, while the amount is
 * shown under the data SIM's card: when the data SIM changes, the count starts again, so the new SIM is not given the
 * previous one's bytes. The change is seen at the next count, so at most one request lands on the wrong side.
 */
final class DataSimBytes {
    /** SubscriptionManager.INVALID_SUBSCRIPTION_ID. */
    static final int NONE = -1;

    private int sub = NONE;
    private long base, total;

    /** A new run: nothing counted yet, the data SIM taken from the first count. */
    void restart() {
        sub = NONE;
        base = 0;
        total = 0;
    }

    /** The run's total so far, and the data SIM at that time ({@link #NONE} while there is none). */
    void update(int dataSub, long runTotal) {
        if (dataSub != sub) {
            if (sub != NONE || total > 0) base = total;
            sub = dataSub;
        }
        total = runTotal;
    }

    long bytes() { return total - base; }
}
