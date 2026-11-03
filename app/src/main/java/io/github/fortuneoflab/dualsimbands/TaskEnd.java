package io.github.fortuneoflab.dualsimbands;

import android.content.SharedPreferences;

/**
 * Waiting for an instrumentation's end, and remembering one whose end could not be confirmed. The instrumentation
 * writes its token to "finished" when done, or to "abandoned" when it stopped at its token check without doing
 * anything. It runs in this app's process, so a new process starts with nothing unsettled.
 */
final class TaskEnd {
    /** An instrumentation's end could not be confirmed: it may still run, so nothing may follow it. */
    static final class Unfinished extends IllegalStateException {
        Unfinished(String message) { super(message); }
    }

    interface Clock { long now(); }
    interface Sleeper { void sleep(long ms) throws InterruptedException; }
    /** Replaced by tests. */
    static Clock clock = android.os.SystemClock::elapsedRealtime;
    static Sleeper sleeper = Thread::sleep;

    /** How long a task that stopped answering keeps the lock, at most. */
    static final long LATE_LIMIT_MS = 10 * 60_000;
    static final String FINISHED = "finished", ABANDONED = "abandoned";

    private static SharedPreferences unsettledPrefs;
    private static String unsettledToken;
    /** Held here: SharedPreferences keeps its listeners only weakly. */
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    /** Told once an unsettled task ends after all (Coordinator applies a change saved meanwhile). */
    private static volatile Runnable onSettled;

    private TaskEnd() {}

    static void setOnSettled(Runnable r) { onSettled = r; }

    static boolean ended(SharedPreferences prefs, String token) {
        return token.equals(prefs.getString(FINISHED, "")) || token.equals(prefs.getString(ABANDONED, ""));
    }

    /**
     * Waits until the instrumentation started with {@code token} ends. Past {@code patienceMs} {@code onLate} runs once
     * (the caller says the result is not known yet) and the wait goes on, up to {@link #LATE_LIMIT_MS}: one that has not
     * ended may still hold borrowed permissions. Past that its end stays unknown: its "pending" token is cleared, so a
     * late start stops at its token check (and writes "abandoned"), and {@link #unsettled} holds until it ends.
     * @throws Unfinished with {@code message} when it did not end
     */
    @android.annotation.SuppressLint("ApplySharedPref") // a late start reads "pending" right away
    static void await(SharedPreferences prefs, String token, long patienceMs, Runnable onLate, String message)
            throws InterruptedException {
        long start = clock.now();
        boolean late = false;
        while (true) {
            sleeper.sleep(250);
            if (ended(prefs, token)) return;
            long waited = clock.now() - start;
            if (!late && waited >= patienceMs) { late = true; if (onLate != null) onLate.run(); }
            if (waited >= LATE_LIMIT_MS) break;
        }
        if (token.equals(prefs.getString("pending", ""))) prefs.edit().remove("pending").commit();
        markUnsettled(prefs, token);
        throw new Unfinished(message);
    }

    private static synchronized void markUnsettled(SharedPreferences prefs, String token) {
        clear();
        unsettledPrefs = prefs;
        unsettledToken = token;
        listener = (p, key) -> {
            if ((FINISHED.equals(key) || ABANDONED.equals(key)) && !unsettled()) {
                Runnable r = onSettled;
                if (r != null) r.run();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    /** Whether an earlier instrumentation's end is still unconfirmed; settles once it has ended after all. */
    static synchronized boolean unsettled() {
        if (unsettledPrefs == null) return false;
        if (!ended(unsettledPrefs, unsettledToken)) return true;
        clear();
        return false;
    }

    private static void clear() {
        if (unsettledPrefs != null && listener != null) unsettledPrefs.unregisterOnSharedPreferenceChangeListener(listener);
        unsettledPrefs = null; unsettledToken = null; listener = null;
    }

    /** For tests: forget any unsettled task. */
    static synchronized void reset() { clear(); }
}
