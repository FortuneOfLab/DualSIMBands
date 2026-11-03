package io.github.fortuneoflab.dualsimbands;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Sets Settings.Global nr_nsa_tracking_screen_off_mode to 2 while history is recorded with the composition read
 * automatically, and puts the earlier value back afterwards, with shell's WRITE_SECURE_SETTINGS delegated via Shizuku
 * for the duration of the write. The key is not in the SDK (Android's DeviceStateMonitor reads it): with 2 the modem
 * keeps reporting channel-configuration changes while the screen is off. A value someone else changed meanwhile is
 * left alone.
 */
public final class NrTrackingInstrumentation extends Instrumentation {
    static final String PREFS = "nrtracking";
    static final String KEY = "nr_nsa_tracking_screen_off_mode";
    /**
     * Prefs: "set" from just before this app writes its 2 until it is undone (the undo may be needed);
     * "applied" once the 2 was read back in place; "hasPrevious"/"previous" the value it replaced.
     */
    static final String SET = "set", APPLIED = "applied", HAS_PREVIOUS = "hasPrevious", PREVIOUS = "previous";
    /** When the marker was last saved; changing it makes every save reach the file. */
    static final String MARKED_AT = "markedAt";
    static final String[] PERMISSIONS = {"android.permission.WRITE_SECURE_SETTINGS"};
    private Bundle args;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }

    @android.annotation.SuppressLint("ApplySharedPref") // the caller polls the result right away
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = args == null ? "" : args.getString("token", "");
        if (token.isEmpty() || !token.equals(prefs.getString("pending", ""))) {
            if (!token.isEmpty()) prefs.edit().putString(TaskEnd.ABANDONED, token).commit();
            finish(1, new Bundle());
            return;
        }
        boolean set = args.getBoolean("set");
        String result, detail = "";
        boolean delegated = false;
        try {
            HiddenBridge.delegate(PERMISSIONS); delegated = true;
            ContentResolver cr = c.getContentResolver();
            if (set && !prefs.getBoolean(APPLIED, false)) {
                // Marked, with the value it replaces, before the write and only once that is on disk: a crash after it
                // still leaves the undo to do. The undo acts only on a 2 still there, so a write that never happened is
                // just cleared. Saved on every attempt, not only the first: a commit that failed still leaves the
                // marker in memory, so its presence proves nothing about the disk. The time stamp changes each attempt,
                // as a commit with no change returns true without writing the file.
                SharedPreferences.Editor marker = prefs.edit().putBoolean(SET, true).putLong(MARKED_AT, System.currentTimeMillis());
                if (!prefs.getBoolean(SET, false)) {
                    // Not readable: nothing is written, so no value is lost.
                    String previous = read(cr);
                    marker.putBoolean(HAS_PREVIOUS, previous != null).putString(PREVIOUS, previous);
                }
                // A retry keeps the earlier value as first saved.
                if (!marker.commit()) throw new IllegalStateException("save " + PREFS);
                // Also after a write that failed or never happened: tried again, the earlier value kept as saved.
                if (!Settings.Global.putString(cr, KEY, "2")) throw new IllegalStateException("put " + KEY);
                if (!"2".equals(read(cr))) throw new IllegalStateException("2 for " + KEY + " not confirmed");
                if (!prefs.edit().putBoolean(APPLIED, true).commit()) throw new IllegalStateException("save " + PREFS);
            } else if (!set && prefs.getBoolean(SET, false)) {
                // Not readable: the marker stays and the undo is tried again. Unset or another value: changed by
                // someone else since, so left alone.
                String now = read(cr);
                if ("2".equals(now)) {
                    String previous = prefs.getBoolean(HAS_PREVIOUS, false) ? prefs.getString(PREVIOUS, null) : null;
                    if (previous != null) Settings.Global.putString(cr, KEY, previous);
                    else cr.call(Settings.Global.CONTENT_URI, "DELETE_global", KEY, null);
                    // Read back: only an undo seen to have taken clears the marker; otherwise it is tried again.
                    if (!java.util.Objects.equals(read(cr), previous)) throw new IllegalStateException("undo of " + KEY + " not confirmed");
                }
                if (!prefs.edit().putBoolean(SET, false).putBoolean(APPLIED, false).remove(HAS_PREVIOUS).remove(PREVIOUS)
                        .remove(MARKED_AT).commit())
                    throw new IllegalStateException("save " + PREFS);
            }
            result = "ok";
        } catch (Throwable e) {
            result = "error";
            detail = HiddenBridge.error(e);
        } finally {
            if (delegated) try {
                HiddenBridge.release(c, PERMISSIONS);
            } catch (Throwable e) {
                result = "error";
                detail = detail + " / release: " + HiddenBridge.error(e);
            }
        }
        prefs.edit().putString("result", result).putString("detail", detail).putString("finished", token).commit();
        finish(0, new Bundle());
    }

    /** The current value, null when unset; throws when it cannot be read, which is never taken as unset. */
    static String read(ContentResolver cr) {
        try { return Settings.Global.getString(cr, KEY); }
        catch (RuntimeException e) { throw new IllegalStateException("read " + KEY + ": " + e, e); }
    }
}
