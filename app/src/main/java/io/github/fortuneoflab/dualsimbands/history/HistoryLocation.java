package io.github.fortuneoflab.dualsimbands.history;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.SystemClock;
import io.github.fortuneoflab.dualsimbands.ui.AppPrefs;

/**
 * The device's position for history rows, taken only while history is being recorded (the bands screen is open or
 * the monitor runs) and the location opt-in is on. Android's own fused provider (no Play services) is asked for
 * high accuracy every 5 s; its last fix is read by {@link HistoryRecorder}. Main thread only.
 */
public final class HistoryLocation {
    private static final long INTERVAL_MS = 5_000;
    /** A fix older than this is not used to tell that the device moved. */
    static final long FRESH_MS = 30_000;

    /** A failed start is tried again this often, this many times, while a recorder is active. */
    private static final long RETRY_MS = 30_000;
    private static final int RETRIES = 10;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private static int users, retries;
    private static Runnable retry;
    private static LocationListener listener;
    private static Location last;

    private HistoryLocation() {}

    /** A recorder (the bands screen, the monitor) starts; GPS runs while any is active and the setting is on. */
    public static void start(Context c) { users++; retries = 0; update(c.getApplicationContext()); }

    public static void stop(Context c) { users = Math.max(0, users - 1); update(c.getApplicationContext()); }

    /** The history or location setting changed. */
    public static void settingsChanged(Context c) { update(c.getApplicationContext()); }

    /**
     * The last fix while running, or null; also null while location services are off, so a fix from before is not
     * recorded as the current position (the history screen says no position is recorded then).
     */
    static Location latest(Context c) {
        if (listener == null) return null;
        LocationManager lm = c.getSystemService(LocationManager.class);
        return lm != null && !lm.isLocationEnabled() ? null : last;
    }

    /** Seconds since the fix was taken. */
    static long ageSeconds(Location l) {
        return Math.max(0, (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000_000L);
    }

    @android.annotation.SuppressLint("MissingPermission") // checked just before
    private static void update(Context c) {
        boolean want = users > 0 && AppPrefs.historyEnabled(c) && AppPrefs.historyIncludeLocation(c)
                && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (retry != null) { MAIN.removeCallbacks(retry); retry = null; }
        if (!want) {
            if (listener != null) lm.removeUpdates(listener);
            listener = null;
            last = null;
            HistoryRecorder.forgetPositions();
            return;
        }
        if (listener != null) return;
        String provider = lm.hasProvider(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER
                : LocationManager.GPS_PROVIDER;
        listener = location -> { last = location; HistoryRecorder.onPosition(); };
        try {
            lm.requestLocationUpdates(provider, new LocationRequest.Builder(INTERVAL_MS)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build(), c.getMainExecutor(), listener);
        } catch (RuntimeException e) {
            // Provider missing or briefly unavailable: rows get no position until a retry succeeds.
            listener = null;
            if (retries < RETRIES) {
                retries++;
                retry = () -> { retry = null; update(c); };
                MAIN.postDelayed(retry, RETRY_MS);
            }
        }
    }
}
