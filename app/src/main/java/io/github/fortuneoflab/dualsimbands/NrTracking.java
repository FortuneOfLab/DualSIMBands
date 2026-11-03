package io.github.fortuneoflab.dualsimbands;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.UUID;

/**
 * Keeps nr_nsa_tracking_screen_off_mode at 2 while history is recorded with the composition read automatically, and
 * puts it back afterwards ({@link NrTrackingInstrumentation}). Needs Shizuku: without it nothing is written. The latest
 * wish is kept and matched again after every attempt, so a stop asked for while a write runs, or while another task
 * holds the lock, is not lost; a failed undo is retried every minute while the process lives, and when the app opens
 * or Shizuku connects (until then the 2 stays, and uses battery).
 */
public final class NrTracking {
    private static final long RETRY_MS = 60_000;
    /** The latest wish; null until asked. */
    private static volatile Boolean desired;
    private static volatile boolean busy, retryPosted;
    /** Only setting is limited to one attempt a minute (the monitor asks every 10 s); undoing is not. */
    private static volatile long lastSet = -RETRY_MS;

    private NrTracking() {}

    /** True while this app's 2 is (or may be) in place: the undo is still to do. */
    public static boolean active(Context c) {
        return prefs(c).getBoolean(NrTrackingInstrumentation.SET, false);
    }

    /** Whether the setting already is as wished: 2 read back in place, or nothing left to undo. */
    static boolean done(Context c, boolean want) {
        return want ? prefs(c).getBoolean(NrTrackingInstrumentation.APPLIED, false) : !active(c);
    }

    /** The write wanted: 2 while recording with the composition read automatically, otherwise the earlier value. */
    public static boolean wanted(boolean recording, boolean probe, boolean probeLive) {
        return recording && probe && probeLive;
    }

    /** Brings the setting in line with {@code want}, off the calling thread. */
    public static void sync(Context context, boolean want) {
        desired = want;
        kick(context.getApplicationContext());
    }

    private static synchronized void kick(Context app) {
        Boolean want = desired;
        // A marker without a confirmed 2 (the write failed, or the process ended before it) is not done: retried.
        if (want == null || done(app, want) || busy) return;
        if (!Coordinator.ready(app)) { retryLater(app); return; }
        long now = SystemClock.elapsedRealtime();
        if (want && now - lastSet < RETRY_MS) return;
        if (want) lastSet = now;
        busy = true;
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = attempt(app, want);
            } finally {
                busy = false;
            }
            // The wish may have changed meanwhile: match it now, or after a pause when this attempt failed.
            if (ok) kick(app); else retryLater(app);
        }, "nr-tracking").start();
    }

    /** Retries an undo still to do in a minute (a set is retried by the monitor's tick). */
    private static void retryLater(Context app) {
        if (retryPosted || !Boolean.FALSE.equals(desired) || !active(app)) return;
        retryPosted = true;
        // Made here, not at class load: the class is also used where no main looper exists (unit tests).
        new Handler(Looper.getMainLooper()).postDelayed(() -> { retryPosted = false; kick(app); }, RETRY_MS);
    }

    @android.annotation.SuppressLint("ApplySharedPref") // the instrumentation reads "pending" right away
    private static boolean attempt(Context app, boolean want) {
        try {
            if (!Coordinator.tryAcquire(app)) return false;
            try {
                SharedPreferences prefs = prefs(app);
                String token = UUID.randomUUID().toString();
                // Not on disk: the instrumentation might not see this token, so nothing is started.
                if (!prefs.edit().putString("pending", token).putString("finished", "").remove("result").remove("detail").commit())
                    return false;
                Bundle args = new Bundle();
                args.putString("token", token);
                args.putBoolean("set", want);
                HiddenBridge.launch(app, NrTrackingInstrumentation.class, args);
                TaskEnd.await(prefs, token, 30_000, () -> { }, "nr tracking did not respond");
                if ("ok".equals(prefs.getString("result", ""))) return true;
                android.util.Log.w("DualSIMBands", "nr tracking " + (want ? "set" : "undo") + ": " + prefs.getString("detail", ""));
                return false;
            } finally {
                Coordinator.releaseLock();
            }
        } catch (Throwable e) {
            android.util.Log.w("DualSIMBands", "nr tracking: " + HiddenBridge.error(e));
            return false;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(NrTrackingInstrumentation.PREFS, Context.MODE_PRIVATE);
    }
}
