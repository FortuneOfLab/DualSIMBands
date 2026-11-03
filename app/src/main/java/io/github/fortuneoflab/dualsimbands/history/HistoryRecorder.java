package io.github.fortuneoflab.dualsimbands.history;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.ui.AppPrefs;

/**
 * Single entry point for writing history, shared by the Radio tab and the monitor service, so a sample
 * is not duplicated when both are active. Does nothing unless the user enabled history.
 */
public final class HistoryRecorder {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "history-io"));
    private static final long PRUNE_EVERY_MS = 60 * 60_000L;
    private static final Map<Integer, String> lastSignature = new HashMap<>();
    private static final Map<Integer, Long> lastAt = new HashMap<>();
    /** Position of the last recorded row, per SIM, for the 50 m rule. */
    private static final Map<Integer, android.location.Location> lastFix = new HashMap<>();
    /** The last raw radio state offered per SIM ({context, slot, carrier, snapshot}), recorded again when only the position changes. */
    private static final Map<Integer, Object[]> lastOffer = new HashMap<>();
    /** The same as the bands screen and the monitor: older cell lists and channels no longer count as verified. */
    private static final long STALE_AFTER_MS = 30_000;
    private static long lastPrune;
    /** The data SIM of the last data row offered, to cut the traffic results when it changes. */
    private static Integer lastDataSub;

    private HistoryRecorder() {}

    /** Call on the main thread. */
    public static void offer(Context context, int subscriptionId, int slot, String carrier, RadioSnapshot s,
                             RadioAssessment a, long nowElapsed) {
        Context c = context.getApplicationContext();
        if (!AppPrefs.historyEnabled(c)) return;
        lastOffer.put(subscriptionId, new Object[]{c, slot, carrier, s});
        long now = System.currentTimeMillis();
        boolean includeLocation = AppPrefs.historyIncludeLocation(c);
        android.location.Location fix = includeLocation ? HistoryLocation.latest(c) : null;
        HistorySample.Position position = fix == null ? null : new HistorySample.Position(fix.getLatitude(),
                fix.getLongitude(), fix.hasAccuracy() ? fix.getAccuracy() : null, HistoryLocation.ageSeconds(fix));
        // The requests go over the data SIM's network: their results belong to its rows only. They carry no SIM, so
        // when the data SIM changes, what was gathered (partly over the previous one) is dropped rather than given to
        // the new SIM's row.
        if (s.defaultData) {
            if (lastDataSub != null && lastDataSub != subscriptionId)
                io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().restartResults();
            lastDataSub = subscriptionId;
        }
        io.github.fortuneoflab.dualsimbands.traffic.TrafficStats.Window traffic = s.defaultData
                ? io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().window(nowElapsed) : null;
        HistorySample sample = HistorySample.of(now, TimeZone.getDefault(), slot, carrier, s, a, nowElapsed,
                AppPrefs.historyIncludeIds(c), includeLocation, position, traffic);
        String last = lastSignature.get(subscriptionId);
        Long at = lastAt.get(subscriptionId);
        // Only fresh, precise fixes tell that the device moved; a stale one repeats the same place, a coarse one wanders.
        boolean fresh = fix != null && HistoryLocation.ageSeconds(fix) * 1000 <= HistoryLocation.FRESH_MS
                && HistoryPolicy.usable(fix.hasAccuracy() ? fix.getAccuracy() : null);
        android.location.Location from = lastFix.get(subscriptionId);
        Double moved = fresh && from != null ? HistoryPolicy.usableDistance(from.distanceTo(fix),
                from.getAccuracy(), fix.getAccuracy()) : null;
        if (!HistoryPolicy.shouldRecord(last, at == null ? 0 : at, sample.signature, now, moved)) return;
        lastSignature.put(subscriptionId, sample.signature);
        if (traffic != null) io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().drain(traffic);
        lastAt.put(subscriptionId, now);
        if (fresh) lastFix.put(subscriptionId, fix);
        boolean prune = now - lastPrune >= PRUNE_EVERY_MS;
        if (prune) lastPrune = now;
        long cutoff = cutoff(c);
        IO.execute(() -> {
            HistoryStore store = HistoryStore.get(c);
            store.insert(sample);
            if (prune) store.prune(cutoff);
        });
    }

    /**
     * A new position fix: the last radio state of each SIM is offered again, so a move is recorded even when no radio
     * callback comes (the monitor with the screen off). Main thread.
     */
    static void onPosition() {
        if (lastOffer.isEmpty()) return;
        Context c = (Context) lastOffer.values().iterator().next()[0];
        java.util.Set<Integer> active = activeSubscriptions(c);
        // A SIM turned off or swapped since: its state is dropped, not recorded again with a new position.
        if (active != null) for (Integer id : new java.util.ArrayList<>(lastOffer.keySet())) if (!active.contains(id)) forget(id);
        long nowElapsed = android.os.SystemClock.elapsedRealtime();
        for (Map.Entry<Integer, Object[]> e : new HashMap<>(lastOffer).entrySet()) {
            Object[] o = e.getValue();
            RadioSnapshot s = (RadioSnapshot) o[3];
            // Judged again at this time: a composition that has gone stale is no longer recorded as verified.
            offer((Context) o[0], e.getKey(), (Integer) o[1], (String) o[2], s, reassess(s, nowElapsed), nowElapsed);
        }
    }

    /** The radio state as the bands screen would judge it at {@code nowElapsed}. */
    public static RadioAssessment reassess(RadioSnapshot s, long nowElapsed) {
        return io.github.fortuneoflab.dualsimbands.radio.RadioStateReducer.reduce(s, nowElapsed, STALE_AFTER_MS);
    }

    /** Subscription ids in use, or null when they cannot be read (the phone permission was taken back). */
    @android.annotation.SuppressLint("MissingPermission") // READ_PHONE_STATE is checked by the catch
    private static java.util.Set<Integer> activeSubscriptions(Context c) {
        try {
            java.util.List<android.telephony.SubscriptionInfo> list =
                    c.getSystemService(android.telephony.SubscriptionManager.class).getActiveSubscriptionInfoList();
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            if (list != null) for (android.telephony.SubscriptionInfo s : list) ids.add(s.getSubscriptionId());
            return ids;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static void forget(int subscriptionId) {
        lastOffer.remove(subscriptionId);
        lastFix.remove(subscriptionId);
        lastSignature.remove(subscriptionId);
        lastAt.remove(subscriptionId);
    }

    /** Positions stopped: the next recording session judges moves afresh. Main thread. */
    static void forgetPositions() {
        lastFix.clear();
        lastOffer.clear();
    }

    /** Rows older than the retention are deleted before they are shown or exported, not only while recording. */
    public static long cutoff(Context c) { return HistoryPolicy.cutoff(System.currentTimeMillis(), AppPrefs.historyRetentionDays(c)); }

    /** Runs a store operation off the main thread. */
    public static void io(Runnable task) { IO.execute(task); }
}
