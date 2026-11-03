package io.github.fortuneoflab.dualsimbands;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Android's own SIM screen, where SIMs are turned on, off and swapped and the data, call and SMS SIMs are chosen.
 * This app does none of that itself (decision of 2026-11-01): Android keeps those defaults and mobile data consistent.
 */
public final class SimSettings {
    private SimSettings() {}

    /** Opens the SIM list (Android 14+) or the network settings as a fallback. */
    public static void open(Context c) {
        Intent[] candidates = Build.VERSION.SDK_INT >= 34
                ? new Intent[]{new Intent(Settings.ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS), new Intent(Settings.ACTION_WIRELESS_SETTINGS)}
                : new Intent[]{new Intent(Settings.ACTION_WIRELESS_SETTINGS)};
        for (Intent i : candidates) {
            try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
            catch (ActivityNotFoundException ignored) { }
        }
    }
}
