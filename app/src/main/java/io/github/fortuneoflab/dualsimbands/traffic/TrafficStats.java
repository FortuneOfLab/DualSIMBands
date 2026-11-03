package io.github.fortuneoflab.dualsimbands.traffic;

/**
 * Results of the generated requests since the last history row of the data SIM. Only measured facts: how many requests
 * answered, timed out or failed, the slowest time to the first byte, and since when requests have kept failing. A
 * slow or missing answer may come from the server or DNS as well as the radio, so nothing here is called a stall.
 * Times are elapsedRealtime milliseconds. Thread-safe: requests finish on the traffic thread, rows are read on main.
 */
public final class TrafficStats {
    public enum Outcome { OK, TIMEOUT, ERROR }

    /** What one history row records. {@code failingForSeconds} is null unless the last request failed. */
    public static final class Window {
        public final boolean on;
        public final int ok, timeouts, errors;
        /** Slowest answered request in the window (request to first byte), ms; null when none answered. */
        public final Long latencyMaxMs;
        public final Long failingForSeconds;
        /** Whether Android has validated internet access on the mobile network; null when not known. */
        public final Boolean validated;
        Window(boolean on, int ok, int timeouts, int errors, Long latencyMaxMs, Long failingForSeconds, Boolean validated) {
            this.on = on; this.ok = ok; this.timeouts = timeouts; this.errors = errors;
            this.latencyMaxMs = latencyMaxMs; this.failingForSeconds = failingForSeconds; this.validated = validated;
        }
        public boolean failing() { return failingForSeconds != null; }
    }

    /** Answered requests' times to the first byte, oldest first: a row takes its own, later ones stay for the next. */
    private final java.util.ArrayDeque<Long> answered = new java.util.ArrayDeque<>();
    /**
     * At most this many times are kept: with no history row taking them (the bands screen alone) they would pile up.
     * Recording takes them at least once a minute, far below this at any interval.
     */
    static final int MAX_KEPT = 1000;
    /** Answered requests whose times were let go beyond {@link #MAX_KEPT}: still counted, the oldest first. */
    private int answeredDropped;
    private int timeouts, errors;
    /** When the current run of failed requests began; -1 while the last request answered. */
    private long failSince = -1;
    private Boolean validated;
    /** Requests sent before this (elapsedRealtime) are not counted: they may have gone over the previous data SIM. */
    private long since = Long.MIN_VALUE;

    public synchronized void record(Outcome outcome, long latencyMs, long nowMillis) {
        record(outcome, latencyMs, nowMillis, nowMillis);
    }

    /** {@code beganAt}: when the request was sent; one sent before the last {@link #restart} is left out. */
    public synchronized void record(Outcome outcome, long latencyMs, long nowMillis, long beganAt) {
        if (beganAt < since) return;
        if (outcome == Outcome.OK) {
            answered.addLast(latencyMs);
            if (answered.size() > MAX_KEPT) { answered.removeFirst(); answeredDropped++; }
            failSince = -1;
            return;
        }
        if (outcome == Outcome.TIMEOUT) timeouts++; else errors++;
        if (failSince < 0) failSince = nowMillis;
    }

    public synchronized void setValidated(Boolean v) { validated = v; }

    /** Starts afresh, also leaving out requests sent before {@code sinceElapsed} that finish later. */
    public synchronized void restart(long sinceElapsed) {
        reset();
        since = sinceElapsed;
    }

    /** Starts afresh, for a new traffic session. */
    public synchronized void reset() {
        answered.clear();
        answeredDropped = 0;
        timeouts = errors = 0;
        failSince = -1;
        validated = null;
    }

    public synchronized Window peek(boolean on, long nowMillis) {
        if (!on) return new Window(false, 0, 0, 0, null, null, null);
        Long max = null;
        for (Long l : answered) if (max == null || l > max) max = l;
        return new Window(true, answeredDropped + answered.size(), timeouts, errors, max,
                failSince < 0 ? null : failingSeconds(failSince, nowMillis), validated);
    }

    /** Seconds since the failures began, rounded up and at least 1: 0 is kept for "not failing" in the CSV. */
    static long failingSeconds(long failSince, long nowMillis) {
        return Math.max(1, (nowMillis - failSince + 999) / 1000);
    }

    /**
     * The window was written to a row: what it held is taken away, and requests finished since stay, with their
     * times, for the next row; a failure run still going keeps its start.
     */
    public synchronized void drain(Window w) {
        if (!w.on) return;
        int take = w.ok, dropped = Math.min(take, answeredDropped);
        answeredDropped -= dropped;
        for (int k = dropped; k < take && !answered.isEmpty(); k++) answered.removeFirst();
        timeouts = Math.max(0, timeouts - w.timeouts);
        errors = Math.max(0, errors - w.errors);
    }
}
