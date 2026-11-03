package io.github.fortuneoflab.dualsimbands.monitor;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.fortuneoflab.dualsimbands.MainActivity;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.PrivilegedProbe;
import io.github.fortuneoflab.dualsimbands.history.HistoryRecorder;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioRepository;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.RadioStateReducer;
import io.github.fortuneoflab.dualsimbands.radio.SubscriptionRepository;
import io.github.fortuneoflab.dualsimbands.ui.AppPrefs;

/**
 * Opt-in ongoing notification. Foreground service of type "location" because CellInfo is location data;
 * it may only be started while the app is visible, and is not restarted after a reboot or a kill.
 * With the screen off it stops polling CellInfo and stops updating the notification; event callbacks
 * (registration, display, signal) continue so history stays meaningful.
 */
public final class MonitorService extends Service implements RadioRepository.Listener {
    /**
     * Default importance, so the icon shows in the status bar while monitoring (Pixel hides silent notifications' icons),
     * with no sound or vibration; a new id because a channel's importance cannot be raised once created.
     */
    private static final String CHANNEL = "monitor_status", OLD_CHANNEL = "monitor";
    private static final int NOTIFICATION_ID = 2;
    private static final String ACTION_STOP = "io.github.fortuneoflab.dualsimbands.monitor.STOP";
    private static final long CELL_REFRESH_MS = 10_000, NOTIFY_COALESCE_MS = 2_000, STALE_AFTER_MS = 30_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, RadioSnapshot> snapshots = new HashMap<>();
    private List<SubscriptionRepository.Sim> sims = new ArrayList<>();
    private RadioRepository repository;
    private boolean started, screenOn = true, notifyPending;
    /**
     * Live PhysicalChannelConfig through Shizuku while history is recorded, screen on or off, so rows written with
     * the bands screen closed (the lock screen) still carry the verified composition. Same switches as the bands screen.
     */
    private io.github.fortuneoflab.dualsimbands.LivePhysicalChannels live;
    private final Map<Integer, List<RadioSnapshot.PhysicalChannel>> lastLive = new HashMap<>();
    private List<Integer> liveIds = new ArrayList<>();
    /**
     * Re-checks the switches and passes the last report on as current: while registered and while the modem reports
     * changes, it is still the configuration ({@link #channelReportsFlow}).
     */
    private static final long LIVE_TICK_MS = 10_000;
    private final Runnable liveTick = new Runnable() {
        @Override public void run() {
            // The chosen time is up (checked here, with the screen off too): recording ends, and so does a monitor
            // that was on only for it.
            if (AppPrefs.historyExpired(MonitorService.this)) {
                boolean stop = AppPrefs.monitorForHistory(MonitorService.this);
                AppPrefs.endHistory(MonitorService.this);
                if (stop) {
                    AppPrefs.setMonitorEnabled(MonitorService.this, false);
                    stopSelf();
                    return;
                }
            }
            updateLive();
            ensureCellTick();
            boolean recording = AppPrefs.historyAlways(MonitorService.this);
            holdLocation(recording);
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().history(MonitorService.this, recording && AppPrefs.trafficEnabled(MonitorService.this));
            io.github.fortuneoflab.dualsimbands.NrTracking.sync(MonitorService.this, io.github.fortuneoflab.dualsimbands.NrTracking.wanted(
                    recording, AppPrefs.probeEnabled(MonitorService.this), AppPrefs.probeLive(MonitorService.this)));
            requestNotify();
            long now = SystemClock.elapsedRealtime();
            boolean running = live != null && live.isRunning();
            // The value as it is now, even while this app has set it: someone may have changed it since. Unreadable
            // (-1) counts as reports filtered, so nothing stale is kept as verified.
            int mode = nrTrackingMode();
            boolean flow = channelReportsFlow(screenOn, charging(), mode);
            if (!flow && !"off".equals(reportsState)) Log.i("DualSIMBands", "monitor live: reports filtered (screen off, not charging, nr tracking " + mode + ")");
            reportsState = flow ? "on" : "off";
            // Without reports the last one ages and goes stale, rather than standing as verified for hours.
            if (flow) for (Map.Entry<Integer, List<RadioSnapshot.PhysicalChannel>> e : new HashMap<>(lastLive).entrySet())
                if (liveCurrent(running, liveIds, e.getKey())) repository.keepPhysicalChannelsCurrent(e.getKey(), e.getValue(), now);
            handler.postDelayed(this, LIVE_TICK_MS);
        }
    };

    private String reportsState;

    /**
     * Whether the modem reports channel-configuration changes. Android filters those reports out while the screen is off
     * and the phone is not charging, unless NR tracking with the screen off is set to always on (2); generated traffic
     * does not lift the filter. Observed on the Pixel 10a in the phone process's log: 32 (reports off) with the screen
     * off on battery, also with traffic; 54 (reports on) with the setting at 2; 127 while charging.
     */
    static boolean channelReportsFlow(boolean screenOn, boolean charging, int nrTrackingMode) {
        return screenOn || charging || nrTrackingMode == 2;
    }

    private boolean charging() {
        android.os.BatteryManager b = getSystemService(android.os.BatteryManager.class);
        return b != null && b.isCharging();
    }

    /** Settings.Global nr_nsa_tracking_screen_off_mode (not in the SDK; read only); -1 when it cannot be read. */
    private int nrTrackingMode() {
        try { return android.provider.Settings.Global.getInt(getContentResolver(), "nr_nsa_tracking_screen_off_mode", 0); }
        catch (RuntimeException e) { return -1; }
    }

    /** Why the composition the monitor wants cannot be read, for the notification; NONE when it is not wanted. */
    static PrivilegedProbe.Block blocked(boolean history, boolean probe, boolean probeLive, PrivilegedProbe.Block block) {
        return history && probe && probeLive ? block : PrivilegedProbe.Block.NONE;
    }

    /** Whether the monitor registers for live channels: history recorded always, the composition switches on, Shizuku ready, a SIM. */
    static boolean liveWanted(boolean history, boolean probe, boolean probeLive, boolean shizukuReady, int sims) {
        return history && probe && probeLive && shizukuReady && sims > 0;
    }

    /**
     * A line's last report counts as current only while its registration holds: after a failed re-registration it would
     * otherwise be re-stamped every tick and recorded as verified. Also drops reports from a registration already removed.
     */
    static boolean liveCurrent(boolean running, List<Integer> liveIds, int subId) {
        return running && liveIds.contains(subId);
    }

    private String liveState;
    /** The composition is wanted but cannot be read: the notification says why. */
    private PrivilegedProbe.Block blocked = PrivilegedProbe.Block.NONE;

    /** Re-reads why the composition cannot be read; true when that changed. Also run when the screen turns on. */
    private boolean refreshBlocked(boolean history, boolean probe, boolean probeLive, PrivilegedProbe.Block block) {
        PrivilegedProbe.Block now = blocked(history, probe, probeLive, block);
        if (now == blocked) return false;
        blocked = now;
        return true;
    }

    private void updateLive() {
        boolean history = AppPrefs.historyAlways(this), probe = AppPrefs.probeEnabled(this), probeLive = AppPrefs.probeLive(this);
        PrivilegedProbe.Block block = PrivilegedProbe.block(this);
        boolean ready = block == PrivilegedProbe.Block.NONE;
        boolean want = liveWanted(history, probe, probeLive, ready, sims.size());
        // Logged once per change: which condition keeps the monitor from reading the composition.
        String state = "history=" + history + " probe=" + probe + " live=" + probeLive + " shizuku=" + ready + " sims=" + sims.size();
        if (!state.equals(liveState)) { liveState = state; android.util.Log.i("DualSIMBands", "monitor live: " + state); }
        if (refreshBlocked(history, probe, probeLive, block)) requestNotify();
        List<Integer> ids = new ArrayList<>();
        for (SubscriptionRepository.Sim sim : sims) ids.add(sim.subscriptionId);
        if (!want) {
            if (live != null && live.isRunning()) live.stop();
            lastLive.clear();
            liveIds = new ArrayList<>();
            return;
        }
        if (live == null) live = new io.github.fortuneoflab.dualsimbands.LivePhysicalChannels((subId, channels, at) -> handler.post(() -> {
            if (!started || !liveCurrent(live.isRunning(), liveIds, subId)) return;
            lastLive.put(subId, channels);
            repository.setPhysicalChannels(subId, channels, at);
        }));
        // The SIMs changed: register afresh, so a line no longer in use is not kept.
        if (!ids.equals(liveIds) && live.isRunning()) {
            live.stop();
            lastLive.keySet().retainAll(ids);
        }
        String error = live.start(ids);
        if (error != null) {
            android.util.Log.w("DualSIMBands", "monitor live PhysicalChannelConfig: " + error);
            lastLive.clear();
        }
        liveIds = error == null ? ids : new ArrayList<>();
    }

    /** GPS for history rows only while this records them (history set to always), not for the notification alone. */
    private boolean locationHeld;
    private void holdLocation(boolean want) {
        if (want == locationHeld) return;
        locationHeld = want;
        if (want) io.github.fortuneoflab.dualsimbands.history.HistoryLocation.start(this);
        else io.github.fortuneoflab.dualsimbands.history.HistoryLocation.stop(this);
    }

    private boolean cellTicking;
    private final Runnable cellTick = new Runnable() {
        @Override public void run() {
            if (!cellsWanted(screenOn, AppPrefs.historyAlways(MonitorService.this))) { cellTicking = false; return; }
            repository.refreshCells();
            handler.postDelayed(this, CELL_REFRESH_MS);
        }
    };

    /**
     * The cell list (serving cell's PCI, TAC, cell ID and TA, the neighbours) is read while the screen is on, and with
     * the screen off too while history is recorded: otherwise a ride with the phone locked would keep the first cell.
     */
    static boolean cellsWanted(boolean screenOn, boolean history) { return screenOn || history; }

    /** Starts the cell reads when they are wanted and not running (also after history is turned on with the screen off). */
    private void ensureCellTick() {
        if (cellTicking || !cellsWanted(screenOn, AppPrefs.historyAlways(this))) return;
        cellTicking = true;
        handler.post(cellTick);
    }
    private final Runnable notifyNow = () -> { notifyPending = false; postNotification(); };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
            ensureCellTick();
            // The notification was not updated while the screen was off: show why the composition is missing as of now.
            if (screenOn) refreshBlocked(AppPrefs.historyAlways(MonitorService.this), AppPrefs.probeEnabled(MonitorService.this),
                    AppPrefs.probeLive(MonitorService.this), PrivilegedProbe.block(MonitorService.this));
            // Also on screen-off: one final update says that updates are paused, so it never looks live.
            postNotification();
        }
    };

    /** Location permission plus (Android 13+) notification permission. */
    public static boolean canRun(Context c) {
        boolean location = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean notify = Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return location && notify;
    }

    /**
     * Call only while the app is in the foreground (location FGS may not start from the background).
     * False when the start was refused; the caller then shows that the monitor is not running.
     */
    public static boolean start(Context c) {
        if (!canRun(c)) return false;
        try { c.startForegroundService(new Intent(c, MonitorService.class)); return true; }
        catch (RuntimeException e) { return false; }
    }

    public static void stop(Context c) { c.stopService(new Intent(c, MonitorService.class)); }


    private final Runnable trafficChanged = this::requestNotify;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel(OLD_CHANNEL);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.monitor_channel), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
        repository = new RadioRepository(this, this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AppPrefs.setMonitorEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } catch (RuntimeException e) {
            // Missing permission or background start restriction: never run without the notification, and turn the
            // setting off (history goes back to while the app is open) so no screen shows a monitor that is not running.
            android.util.Log.w("DualSIMBands", "monitor could not start: " + e);
            AppPrefs.setMonitorEnabled(this, false);
            // Recording may already hold the traffic (the history screen starts it at once): released with it.
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().history(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().addListener(trafficChanged);
            screenOn = getSystemService(PowerManager.class).isInteractive();
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screen, f);
            repository.start();
            handler.post(liveTick);
            ensureCellTick();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (started) {
            started = false;
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().removeListener(trafficChanged);
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().history(this, false);
            // Recording ended with the monitor: the setting goes back (retried later if Shizuku is not there now).
            io.github.fortuneoflab.dualsimbands.NrTracking.sync(this, false);
            unregisterReceiver(screen);
            if (live != null) live.stop();
            lastLive.clear();
            repository.stop();
            holdLocation(false);
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onSnapshot(RadioSnapshot s) {
        if (!started) return;
        snapshots.put(s.subscriptionId, s);
        long now = SystemClock.elapsedRealtime();
        boolean record = AppPrefs.historyAlways(this);
        for (SubscriptionRepository.Sim sim : sims) {
            if (!record || sim.subscriptionId != s.subscriptionId) continue;
            HistoryRecorder.offer(this, sim.subscriptionId, sim.slotIndex, sim.carrierName, s,
                    RadioStateReducer.reduce(s, now, STALE_AFTER_MS), now);
        }
        requestNotify();
    }

    @Override public void onSubscriptions(List<SubscriptionRepository.Sim> current) {
        if (!started) return;
        sims = current;
        updateLive();
        requestNotify();
    }

    private void requestNotify() {
        if (!started || !screenOn || notifyPending) return;
        notifyPending = true;
        handler.postDelayed(notifyNow, NOTIFY_COALESCE_MS);
    }

    private void postNotification() {
        if (!started) return;
        try { getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, build()); }
        catch (SecurityException ignored) { /* Notification permission revoked; the FGS notice stays as is. */ }
    }

    private Notification build() {
        long now = SystemClock.elapsedRealtime();
        List<MonitorText.Line> lines = new ArrayList<>();
        for (SubscriptionRepository.Sim sim : sims) {
            RadioSnapshot s = snapshots.get(sim.subscriptionId);
            RadioAssessment a = s == null ? null : RadioStateReducer.reduce(s, now, STALE_AFTER_MS);
            lines.add(new MonitorText.Line(sim.slotIndex, sim.carrierName, sim.mcc, s, a));
        }
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession traffic = io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get();
        String recordingLine = null;
        if (AppPrefs.historyAlways(this)) {
            recordingLine = getString(R.string.monitor_recording, (int) AppPrefs.historyMinutesLeft(this));
            if (traffic.forHistory()) recordingLine += getString(traffic.state() == io.github.fortuneoflab.dualsimbands.traffic.TrafficGenerator.State.ERROR
                    ? R.string.monitor_traffic_error : R.string.monitor_traffic, io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.amount(traffic.bytes()));
        }
        MonitorText.Content t = MonitorText.content(lines, System.currentTimeMillis(), screenOn, blocked, recordingLine);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MonitorService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(t.title)
                .setContentText(t.text)
                .setStyle(new Notification.BigTextStyle().bigText(t.expanded))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, getString(R.string.monitor_stop), stop).build())
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }
}
