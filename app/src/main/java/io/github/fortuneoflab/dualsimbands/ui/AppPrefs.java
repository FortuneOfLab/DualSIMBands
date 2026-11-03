package io.github.fortuneoflab.dualsimbands.ui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App preferences, kept apart from the SIM labels' "profiles" file so label data is never touched.
 * Every feature that records, keeps running or uses Shizuku for radio data is off by default.
 */
public final class AppPrefs {
    private static final String FILE = "dualsimbands";
    private static final String SHOW_CELL_IDS = "showCellIds";
    private static final String HISTORY_IDS = "historyIds", HISTORY_DAYS = "historyDays";
    private static final String MONITOR = "monitor";
    private static final String PROBE = "privilegedProbe";
    private static final String DATA_SIM = "dataSimSwitch";
    private static final String PROBE_LIVE = "privilegedProbeLive";
    private static final String TRAFFIC = "trafficMode";
    /** SIM card colour and name: false = from the network (default), true = Android's SIM label and colour. */
    private static final String SIM_LOOK_ANDROID = "simLookAndroid";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    /** TAC / CI / NCI are location-adjacent, so they are hidden unless the user opts in. */
    public static boolean showCellIds(Context c) { return prefs(c).getBoolean(SHOW_CELL_IDS, false); }
    public static void setShowCellIds(Context c, boolean value) { prefs(c).edit().putBoolean(SHOW_CELL_IDS, value).apply(); }

    /** History is recorded (through the monitor, screen off or locked too) only during a chosen time. */
    public static boolean historyEnabled(Context c) { return historyAlways(c); }

    /** Separate opt-in for PCI / TAC / cell ID in history rows. */
    public static boolean historyIncludeIds(Context c) { return prefs(c).getBoolean(HISTORY_IDS, false); }
    public static void setHistoryIncludeIds(Context c, boolean v) { prefs(c).edit().putBoolean(HISTORY_IDS, v).apply(); }
    /** Latitude and longitude in history rows: a separate opt-in, off at first. */
    public static boolean historyIncludeLocation(Context c) { return prefs(c).getBoolean(HISTORY_LOCATION, false); }
    public static void setHistoryIncludeLocation(Context c, boolean v) { prefs(c).edit().putBoolean(HISTORY_LOCATION, v).apply(); }
    private static final String HISTORY_LOCATION = "historyLocation";

    public static int historyRetentionDays(Context c) { return prefs(c).getInt(HISTORY_DAYS, 7); }
    public static void setHistoryRetentionDays(Context c, int days) { prefs(c).edit().putInt(HISTORY_DAYS, days).apply(); }

    /** Durations offered for recording history, in minutes: it ends by itself after the chosen one. */
    public static final int[] HISTORY_MINUTES = {15, 30, 60, 120, 240, 480, 720, 1440};

    /**
     * Whether history is being recorded: until the chosen end (wall-clock time, so it holds across a restart of the
     * monitor) and only with the monitor on, which does the recording.
     */
    static boolean historyOn(long untilMillis, boolean monitor, long nowMillis) {
        return monitor && untilMillis > nowMillis;
    }
    public static boolean historyAlways(Context c) {
        return historyOn(historyUntil(c), monitorEnabled(c), System.currentTimeMillis());
    }
    /** The chosen end of recording (System.currentTimeMillis()); 0 when not recording. */
    public static long historyUntil(Context c) { return prefs(c).getLong(HISTORY_UNTIL, 0); }
    /** The time has run out but recording was not ended yet: the monitor ends it. */
    public static boolean historyExpired(Context c) {
        long until = historyUntil(c);
        return until > 0 && until <= System.currentTimeMillis();
    }
    /** Records for {@code minutes} from now; the caller starts the monitor. */
    public static void startHistory(Context c, int minutes) {
        prefs(c).edit().putLong(HISTORY_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply();
    }
    /** Ends recording; the caller stops the monitor if it was on only for that. */
    public static void endHistory(Context c) { prefs(c).edit().putLong(HISTORY_UNTIL, 0).apply(); }
    /** Minutes left of recording, rounded up; 0 when not recording. */
    public static long historyMinutesLeft(Context c) {
        if (!historyAlways(c)) return 0;
        return Math.max(0, (historyUntil(c) - System.currentTimeMillis() + 59_999) / 60_000);
    }
    private static final String HISTORY_UNTIL = "historyUntil", MONITOR_FOR_HISTORY = "monitorForHistory";

    /** User wants the ongoing notification; it is (re)started only while the app is visible. */
    public static boolean monitorEnabled(Context c) { return prefs(c).getBoolean(MONITOR, false); }
    /** Set by the user's own switch; turning it off also ends recording. */
    public static void setMonitorEnabled(Context c, boolean v) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean(MONITOR, v).putBoolean(MONITOR_FOR_HISTORY, false);
        if (!v) e.putLong(HISTORY_UNTIL, 0);
        e.apply();
    }
    /** The monitor was turned on only for recording, so the end of recording turns it off again. */
    public static void setMonitorForHistory(Context c) {
        prefs(c).edit().putBoolean(MONITOR, true).putBoolean(MONITOR_FOR_HISTORY, true).apply();
    }
    public static boolean monitorForHistory(Context c) {
        return monitorEnabled(c) && prefs(c).getBoolean(MONITOR_FOR_HISTORY, false);
    }

    /** Lets a screen follow changes made elsewhere (e.g. the notification's stop action). */
    public static void listen(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs(c).registerOnSharedPreferenceChangeListener(l);
    }
    public static void unlisten(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs(c).unregisterOnSharedPreferenceChangeListener(l);
    }

    /** Experimental: PhysicalChannelConfig via Shizuku-delegated READ_PRECISE_PHONE_STATE. */
    public static boolean probeEnabled(Context c) { return prefs(c).getBoolean(PROBE, false); }
    public static void setProbeEnabled(Context c, boolean v) { prefs(c).edit().putBoolean(PROBE, v).apply(); }

    public static boolean simLookAndroid(Context c) { return prefs(c).getBoolean(SIM_LOOK_ANDROID, false); }
    public static void setSimLookAndroid(Context c, boolean value) { prefs(c).edit().putBoolean(SIM_LOOK_ANDROID, value).apply(); }

    /** Light or dark: 0 = as Android's system setting (default), 1 = light, 2 = dark. */
    public static int theme(Context c) { return prefs(c).getInt(THEME, 0); }
    public static void setTheme(Context c, int value) {
        prefs(c).edit().putInt(THEME, value).apply();
        applyTheme(c);
    }
    /** Applies the chosen theme to every activity (open ones are recreated); called at start-up too. */
    public static void applyTheme(Context c) {
        int t = theme(c);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                t == 1 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                : t == 2 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    private static final String THEME = "theme";

    /**
     * The one choice shown for both switches: 0 none, 1 the Read button only, 2 always (the bands screen and history).
     * Kept as the two existing switches, so nothing is migrated.
     */
    public static int probeMode(Context c) { return !probeEnabled(c) ? 0 : probeLive(c) ? 2 : 1; }
    public static void setProbeMode(Context c, int mode) {
        setProbeEnabled(c, mode > 0);
        setProbeLive(c, mode == 2);
    }
    /** With the probe enabled: keep a Shizuku PhysicalChannelConfig listener on the bands screen and while history is recorded. */
    public static boolean probeLive(Context c) { return prefs(c).getBoolean(PROBE_LIVE, false); }
    public static void setProbeLive(Context c, boolean v) { prefs(c).edit().putBoolean(PROBE_LIVE, v).apply(); }

    /**
     * Opt-in light traffic while the bands screen is visible; off by default. (Traffic while history is recorded is
     * started on the history screen for a chosen time, not by this setting.) Stored as "LIGHT" or "OFF"; "BULK" from the former download choice now means on.
     */
    public static boolean trafficEnabled(Context c) { return !"OFF".equals(prefs(c).getString(TRAFFIC, "OFF")); }
    public static void setTrafficEnabled(Context c, boolean v) { prefs(c).edit().putString(TRAFFIC, v ? "LIGHT" : "OFF").apply(); }

    /**
     * Seconds between requests. Shorter catches a short stall but keeps the modem from resting (observed on the Pixel 10a
     * at 2 s: the cellular radio active 74% of 17 h on battery); longer saves battery and data.
     */
    public static final int[] TRAFFIC_INTERVALS = {2, 10, 30, 60};

    /**
     * Mobile data an hour at this interval, for the label: about 3 KB a request (1 KB downloaded, plus the connection
     * set-up and headers), measured on the Pixel 10a on 2026-11-01 (164 KB in 2 minutes at 2 s). "5" for 2 s, "0.4" for
     * 30 s: whole MB from 1 MB, one decimal below.
     */
    public static String trafficMbPerHour(int seconds) {
        double mb = 3.0 * 3600 / seconds / 1000;
        return mb >= 1 ? String.valueOf(Math.round(mb)) : String.valueOf(Math.round(mb * 10) / 10.0);
    }
    public static int trafficIntervalSeconds(Context c) {
        int v = prefs(c).getInt(TRAFFIC_INTERVAL, 2);
        for (int s : TRAFFIC_INTERVALS) if (s == v) return v;
        return 2;
    }
    public static void setTrafficIntervalSeconds(Context c, int s) { prefs(c).edit().putInt(TRAFFIC_INTERVAL, s).apply(); }
    private static final String TRAFFIC_INTERVAL = "trafficInterval";

    /** Experimental: switch the data SIM in-app via Shizuku; otherwise the button opens Android's SIM settings. */
    public static boolean dataSimSwitchEnabled(Context c) { return prefs(c).getBoolean(DATA_SIM, false); }
    public static void setDataSimSwitchEnabled(Context c, boolean v) { prefs(c).edit().putBoolean(DATA_SIM, v).apply(); }
}
