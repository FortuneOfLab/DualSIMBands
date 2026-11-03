package io.github.fortuneoflab.dualsimbands.ui;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.history.HistoryRecorder;
import io.github.fortuneoflab.dualsimbands.history.HistoryStore;
import io.github.fortuneoflab.dualsimbands.monitor.MonitorService;

/** Opt-in local history: recording switches, retention, CSV export through the document picker, delete-all. */
public final class HistoryFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    /** The count tile's summary; the recent records' list. */
    private android.widget.TextView stats;
    private LinearLayout recent;
    private ActivityResultLauncher<String> exportLauncher;

    /** Recording: off, or the time left (and the traffic generated meanwhile); refreshed while shown. */
    private android.widget.TextView recording;
    private ActivityResultLauncher<String[]> monitorRequest;
    /** The duration picked while the monitor's permissions are being asked for; kept across a rotation. */
    private int pendingMinutes;
    private static final String PENDING_MINUTES = "pendingMinutes";

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(PENDING_MINUTES, pendingMinutes);
    }
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefsChanged = (p, key) -> {
        renderRecording();
        renderBanner();
    };
    private final Runnable trafficChanged = this::renderRecording;
    private final Runnable recordingTick = new Runnable() {
        @Override public void run() { renderRecording(); main.postDelayed(this, 30_000); }
    };

    private void renderRecording() {
        if (recording == null) return;
        Context c = requireContext();
        if (!AppPrefs.historyAlways(c)) { recording.setText(R.string.history_mode_off); return; }
        String text = getString(R.string.history_recording_left, (int) AppPrefs.historyMinutesLeft(c));
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession t = io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get();
        if (t.forHistory()) text += getString(R.string.history_recording_traffic, io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.amount(t.bytes()));
        recording.setText(text);
    }

    /** Off: picks a duration and starts. Recording: offers to stop. */
    private void pickRecording() {
        Context c = requireContext();
        if (AppPrefs.historyAlways(c)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(c).setTitle(R.string.history_stop_title)
                    .setPositiveButton(R.string.monitor_stop, (d, w) -> stopRecording(c))
                    .setNegativeButton(R.string.action_cancel, null).show();
            return;
        }
        int[] minutes = AppPrefs.HISTORY_MINUTES;
        String[] labels = new String[minutes.length];
        for (int i = 0; i < minutes.length; i++) labels[i] = minutes[i] % 60 == 0
                ? getString(R.string.history_hours, minutes[i] / 60) : getString(R.string.history_minutes, minutes[i]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c).setTitle(R.string.history_enable)
                .setItems(labels, (d, which) -> {
                    pendingMinutes = minutes[which];
                    if (MonitorService.canRun(c)) { recordFor(c, pendingMinutes); return; }
                    java.util.List<String> need = new java.util.ArrayList<>();
                    need.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
                    need.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (android.os.Build.VERSION.SDK_INT >= 33) need.add(android.Manifest.permission.POST_NOTIFICATIONS);
                    monitorRequest.launch(need.toArray(new String[0]));
                })
                .setNegativeButton(R.string.action_cancel, null).show();
    }
    /** Warns while recording always with location services off: the rows then lack the cells and the position. */
    private LinearLayout banner;
    private final android.content.BroadcastReceiver locationMode = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, android.content.Intent i) { renderBanner(); }
    };

    private void renderBanner() {
        if (banner == null) return;
        Context c = requireContext();
        banner.removeAllViews();
        android.location.LocationManager lm = c.getSystemService(android.location.LocationManager.class);
        if (!AppPrefs.historyAlways(c) || lm == null || lm.isLocationEnabled()) return;
        LinearLayout b = Ui.card(banner);
        Ui.title(b, getString(R.string.radio_location_off_title));
        Ui.body(b, getString(R.string.history_location_off_body));
        Ui.button(b, getString(R.string.action_location_settings),
                v -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // The permission dialog may outlive this fragment (a rotation): the duration picked comes back with it.
        if (state != null) pendingMinutes = state.getInt(PENDING_MINUTES, 0);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), this::export);
        monitorRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
            Context c = requireContext();
            // No duration known (should not happen): nothing starts, rather than a recording that ends at once.
            if (pendingMinutes <= 0) return;
            if (MonitorService.canRun(c)) { recordFor(c, pendingMinutes); return; }
            android.widget.Toast.makeText(c, R.string.settings_monitor_needs, android.widget.Toast.LENGTH_LONG).show();
        });
    }

    /** Recording ends now, and so does a monitor that was on only for it. */
    private void stopRecording(Context c) {
        boolean stop = AppPrefs.monitorForHistory(c);
        AppPrefs.endHistory(c);
        // Released here, not on the monitor's next tick: a recording restarted within those 10 s would otherwise find
        // the traffic still held and not start its results afresh. The bands screen keeps it going if it holds it too.
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().history(c, false);
        if (stop) { AppPrefs.setMonitorEnabled(c, false); MonitorService.stop(c); }
        io.github.fortuneoflab.dualsimbands.history.HistoryLocation.settingsChanged(c);
        renderRecording();
    }

    /** Records through the monitor for {@code minutes}, starting it if needed. */
    private void recordFor(Context c, int minutes) {
        boolean wasOn = AppPrefs.monitorEnabled(c);
        if (!wasOn) AppPrefs.setMonitorForHistory(c);
        AppPrefs.startHistory(c, minutes);
        if (!MonitorService.start(c)) {
            // Refused: never show recording without a monitor to record with.
            if (!wasOn) AppPrefs.setMonitorEnabled(c, false);
            AppPrefs.endHistory(c);
            android.widget.Toast.makeText(c, R.string.monitor_start_failed, android.widget.Toast.LENGTH_LONG).show();
        } else {
            // Here, not on the monitor's next tick (up to 10 s on when it already runs): traffic results start afresh
            // before the first row, which is also written on this main thread. Released first, so a hold left from a
            // recording that just ended (its time ran out, and the tick has not come yet) still counts afresh.
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession t = io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get();
            t.history(c, false);
            t.history(c, AppPrefs.trafficEnabled(c));
        }
        io.github.fortuneoflab.dualsimbands.history.HistoryLocation.settingsChanged(c);
    }

    @Override public void onStart() {
        super.onStart();
        AppPrefs.listen(requireContext(), prefsChanged);
        // Changes while stopped were not heard (the monitor stopped from its notification, the time ran out).
        renderRecording();
        requireContext().registerReceiver(locationMode,
                new android.content.IntentFilter(android.location.LocationManager.MODE_CHANGED_ACTION));
        renderBanner();
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().addListener(trafficChanged);
        main.post(recordingTick);
    }

    @Override public void onStop() {
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().removeListener(trafficChanged);
        main.removeCallbacks(recordingTick);
        AppPrefs.unlisten(requireContext(), prefsChanged);
        requireContext().unregisterReceiver(locationMode);
        super.onStop();
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_scroll, container, false);
        LinearLayout page = root.findViewById(R.id.page);
        Context c = requireContext();

        banner = Ui.vertical(c);
        page.addView(banner);
        // Laid out like Android's Settings app: settings and actions as tiles, the records themselves as a list.
        Pref.category(page, getString(R.string.history_group_record));
        Pref.Group record = new Pref.Group(page);
        recording = record.text(getString(R.string.history_enable), getString(R.string.history_mode_off), v -> pickRecording());
        record.toggle(getString(R.string.history_ids), getString(R.string.history_ids_summary),
                AppPrefs.historyIncludeIds(c), (b, on) -> AppPrefs.setHistoryIncludeIds(c, on));
        record.toggle(getString(R.string.history_location), getString(R.string.history_location_summary),
                AppPrefs.historyIncludeLocation(c), (b, on) -> {
                    AppPrefs.setHistoryIncludeLocation(c, on);
                    io.github.fortuneoflab.dualsimbands.history.HistoryLocation.settingsChanged(c);
                });
        int[] options = {1, 7, 30};
        String[] labels = new String[options.length];
        int selected = 0;
        for (int i = 0; i < options.length; i++) {
            labels[i] = getResources().getQuantityString(R.plurals.history_days, options[i], options[i]);
            if (options[i] == AppPrefs.historyRetentionDays(c)) selected = i;
        }
        record.choice(getString(R.string.history_retention), labels, selected, which -> {
            int d = options[which];
            AppPrefs.setHistoryRetentionDays(c, d);
            long cutoff = System.currentTimeMillis() - d * 24L * 60 * 60_000L;
            HistoryRecorder.io(() -> { HistoryStore.get(c).prune(cutoff); main.post(this::refresh); });
        });
        record.close();

        Pref.category(page, getString(R.string.history_group_saved));
        Pref.Group saved = new Pref.Group(page);
        stats = saved.text(getString(R.string.history_count), "", null);
        saved.text(getString(R.string.history_export), getString(R.string.history_export_summary), v -> exportLauncher.launch(
                "dualsimbands-history-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".csv"));
        saved.text(getString(R.string.history_delete_all), getString(R.string.history_delete_summary), v -> new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.history_delete_title).setMessage(R.string.history_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.history_delete_confirm, (d, w) -> HistoryRecorder.io(() -> {
                    HistoryStore.get(c).deleteAll();
                    main.post(this::refresh);
                })).show());
        saved.close();

        // The records are content, not settings: a plain list, so nothing looks tappable that is not.
        Pref.category(page, getString(R.string.history_recent));
        recent = Ui.vertical(c);
        recent.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        page.addView(recent);

        Pref.footer(page, getString(R.string.history_note_record), getString(R.string.history_note_composition),
                getString(R.string.history_note_traffic), getString(R.string.history_note_privacy),
                getString(R.string.history_ids_note), getString(R.string.history_retention_note),
                getString(R.string.history_location_note));
        return root;
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refresh();
    }

    @Override public void onDestroyView() { stats = null; recent = null; recording = null; banner = null; super.onDestroyView(); }

    private void refresh() {
        if (stats == null) return;
        Context c = requireContext().getApplicationContext();
        HistoryRecorder.io(() -> {
            HistoryStore.get(c).prune(HistoryRecorder.cutoff(c));
            long[] s = HistoryStore.get(c).stats();
            List<String[]> rows = HistoryStore.get(c).latest(30);
            main.post(() -> show(s, rows));
        });
    }

    private void show(long[] s, List<String[]> rows) {
        if (stats == null) return;
        recent.removeAllViews();
        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        String count = getResources().getQuantityString(R.plurals.history_rows, (int) Math.min(Integer.MAX_VALUE, s[0]), s[0]);
        stats.setText(s[0] > 0 ? count + " · " + f.format(new Date(s[1])) + t(" 〜 ", " – ") + f.format(new Date(s[2])) : count);
        if (rows.isEmpty()) { Ui.caption(recent, getString(R.string.history_empty)); return; }
        DateFormat time = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        for (String[] r : rows) Ui.caption(recent, time.format(new Date(Long.parseLong(r[0]))) + "  " + r[1]);
    }

    private void export(Uri uri) {
        if (uri == null) return;
        Context c = requireContext().getApplicationContext();
        HistoryRecorder.io(() -> {
            String message;
            try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException(t("出力先を開けません", "Cannot open the destination"));
                HistoryStore.get(c).prune(HistoryRecorder.cutoff(c));
                int rows = HistoryStore.get(c).export(out);
                message = c.getResources().getQuantityString(R.plurals.history_exported, rows, rows);
            } catch (Exception e) {
                message = c.getString(R.string.history_export_failed, String.valueOf(e.getMessage()));
            }
            String m = message;
            main.post(() -> Ui.message(this, m));
        });
    }
}
