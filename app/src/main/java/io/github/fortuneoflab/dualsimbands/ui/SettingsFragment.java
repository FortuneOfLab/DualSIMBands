package io.github.fortuneoflab.dualsimbands.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.monitor.MonitorService;
import rikka.shizuku.Shizuku;

/** Permission status, display options, terminology and version details. */
public final class SettingsFragment extends Fragment {
    private LinearLayout status;
    /** The experimental group needs Shizuku: hidden in basic mode (Shizuku missing, stopped or not allowed). */
    private View expTitle, expGroup;
    private MaterialSwitch monitor, showIds;
    private ActivityResultLauncher<String[]> request, monitorRequest;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Shizuku.OnRequestPermissionResultListener shizukuGrant = (code, result) -> main.post(this::renderStatus);
    private final Shizuku.OnBinderReceivedListener shizukuUp = () -> main.post(this::renderStatus);
    private final Shizuku.OnBinderDeadListener shizukuDown = () -> main.post(this::renderStatus);
    // Held as a field: SharedPreferences keeps listeners only weakly.
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefsChanged = (p, key) -> {
        if (monitor != null && "monitor".equals(key)) monitor.setChecked(p.getBoolean(key, false));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // After the runtime permissions, ask for Shizuku too, as the SIM labels tab used to.
        request = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
            renderStatus();
            requestShizuku(false);
        });
        Shizuku.addRequestPermissionResultListener(shizukuGrant);
        Shizuku.addBinderReceivedListenerSticky(shizukuUp);
        Shizuku.addBinderDeadListener(shizukuDown);
        monitorRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
            Context c = requireContext();
            boolean ok = MonitorService.canRun(c);
            if (ok) ok = startMonitor(c);
            else {
                AppPrefs.setMonitorEnabled(c, false);
                android.widget.Toast.makeText(c, R.string.settings_monitor_needs, android.widget.Toast.LENGTH_LONG).show();
            }
            if (monitor != null) monitor.setChecked(ok);
            renderStatus();
        });
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_scroll, container, false);
        LinearLayout page = root.findViewById(R.id.page);
        Context c = requireContext();

        Pref.category(page, getString(R.string.settings_permissions));
        status = Ui.vertical(c);
        page.addView(status);

        Pref.category(page, getString(R.string.settings_display));
        Pref.Group display = new Pref.Group(page);
        showIds = display.toggle(getString(R.string.settings_show_ids), getString(R.string.settings_show_ids_note),
                AppPrefs.showCellIds(c), (b, checked) -> AppPrefs.setShowCellIds(c, checked));
        display.choice(getString(R.string.settings_sim_look),
                new String[]{getString(R.string.settings_sim_look_network), getString(R.string.settings_sim_look_android)},
                AppPrefs.simLookAndroid(c) ? 1 : 0, which -> AppPrefs.setSimLookAndroid(c, which == 1));
        display.choice(getString(R.string.settings_theme), new String[]{getString(R.string.settings_theme_system),
                        getString(R.string.settings_theme_light), getString(R.string.settings_theme_dark)},
                AppPrefs.theme(c), which -> AppPrefs.setTheme(c, which));
        display.close();

        Pref.category(page, getString(R.string.settings_monitoring));
        Pref.Group monitorGroup = new Pref.Group(page);
        monitor = monitorGroup.toggle(getString(R.string.settings_monitor), getString(R.string.settings_monitor_note),
                AppPrefs.monitorEnabled(c), (b, on) -> {
            if (!on) { AppPrefs.setMonitorEnabled(c, false); MonitorService.stop(c); return; }
            // Already on (also when the switch follows a start from History): keep who turned it on.
            if (AppPrefs.monitorEnabled(c)) return;
            if (MonitorService.canRun(c)) { if (!startMonitor(c)) b.setChecked(false); return; }
            java.util.List<String> need = new java.util.ArrayList<>();
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (android.os.Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS);
            monitorRequest.launch(need.toArray(new String[0]));
        });
        monitorGroup.close();

        Pref.category(page, getString(R.string.settings_traffic));
        Pref.Group traffic = new Pref.Group(page);
        String[] modeLabels = {getString(R.string.settings_traffic_off), getString(R.string.settings_traffic_light)};
        traffic.choice(getString(R.string.settings_traffic), modeLabels, AppPrefs.trafficEnabled(c) ? 1 : 0, which -> {
            AppPrefs.setTrafficEnabled(c, which == 1);
        });
        int[] intervals = AppPrefs.TRAFFIC_INTERVALS;
        String[] intervalLabels = new String[intervals.length];
        int intervalIndex = 0;
        for (int i = 0; i < intervals.length; i++) {
            intervalLabels[i] = getString(R.string.settings_traffic_interval_seconds, intervals[i], AppPrefs.trafficMbPerHour(intervals[i]));
            if (intervals[i] == AppPrefs.trafficIntervalSeconds(c)) intervalIndex = i;
        }
        traffic.choice(getString(R.string.settings_traffic_interval), intervalLabels, intervalIndex,
                which -> AppPrefs.setTrafficIntervalSeconds(c, intervals[which]));
        traffic.close();

        expTitle = Pref.category(page, getString(R.string.settings_experimental));
        Pref.Group exp = new Pref.Group(page);
        expGroup = exp.view();
        // One choice instead of a switch and its dependent: no combination can contradict another.
        exp.choice(getString(R.string.settings_probe), new String[]{getString(R.string.settings_probe_none),
                        getString(R.string.settings_probe_button), getString(R.string.settings_probe_always)},
                AppPrefs.probeMode(c), mode -> AppPrefs.setProbeMode(c, mode));
        exp.toggle(getString(R.string.settings_data_sim), getString(R.string.settings_data_sim_note),
                AppPrefs.dataSimSwitchEnabled(c), (b, on) -> AppPrefs.setDataSimSwitchEnabled(c, on));
        exp.close();

        Pref.footer(page, getString(R.string.settings_footer_location), getString(R.string.settings_footer_shizuku),
                getString(R.string.settings_footer_monitor), getString(R.string.settings_footer_traffic),
                getString(R.string.settings_footer_internal));
        return root;
    }

    /** Turns the monitor on; when Android refuses the start, it stays off and says so. */
    private boolean startMonitor(Context c) {
        AppPrefs.setMonitorEnabled(c, true);
        if (MonitorService.start(c)) return true;
        AppPrefs.setMonitorEnabled(c, false);
        android.widget.Toast.makeText(c, R.string.monitor_start_failed, android.widget.Toast.LENGTH_LONG).show();
        return false;
    }

    @Override public void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuGrant);
        Shizuku.removeBinderReceivedListener(shizukuUp);
        Shizuku.removeBinderDeadListener(shizukuDown);
        super.onDestroy();
    }

    /** Phone, location and notifications first; then Shizuku when it is running but not yet allowed. */
    private void grantAll() {
        Context c = requireContext();
        java.util.List<String> need = new java.util.ArrayList<>();
        for (String p : new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION})
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) { request.launch(need.toArray(new String[0])); return; }
        requestShizuku(true);
    }

    private void requestShizuku(boolean explain) {
        Context c = getContext();
        if (c == null) return;
        try {
            if (!Shizuku.pingBinder()) {
                if (explain) Ui.message(this, getString(R.string.labels_start_shizuku_first));
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(12);
            else if (explain) Ui.message(this, getString(R.string.labels_all_granted));
        } catch (RuntimeException e) {
            if (explain) Ui.message(this, String.valueOf(e.getMessage()));
        }
    }

    @Override public void onStart() {
        super.onStart();
        AppPrefs.listen(requireContext(), prefsChanged);
    }

    @Override public void onStop() {
        AppPrefs.unlisten(requireContext(), prefsChanged);
        super.onStop();
    }

    @Override public void onResume() {
        super.onResume();
        renderStatus();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) renderStatus();
        // The bands screen's menu changes the same setting while this screen is hidden.
        if (!hidden && showIds != null) showIds.setChecked(AppPrefs.showCellIds(requireContext()));
    }

    @Override public void onDestroyView() {
        status = null;
        monitor = null;
        showIds = null;
        expTitle = null; expGroup = null;
        super.onDestroyView();
    }

    private void renderStatus() {
        if (status == null) return;
        Context c = requireContext();
        // The notification's stop action turns monitoring off outside this screen.
        if (monitor != null && monitor.isChecked() != AppPrefs.monitorEnabled(c)) monitor.setChecked(AppPrefs.monitorEnabled(c));
        status.removeAllViews();
        int exp = io.github.fortuneoflab.dualsimbands.PrivilegedProbe.ready(c) ? View.VISIBLE : View.GONE;
        if (expTitle != null) { expTitle.setVisibility(exp); expGroup.setVisibility(exp); }
        Pref.Group state = new Pref.Group(status);
        // Runtime permissions as one row: tapping asks for the missing ones, or opens the app's settings.
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            missing.add(getString(R.string.settings_perm_phone));
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missing.add(getString(R.string.settings_perm_location));
        if (android.os.Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing.add(getString(R.string.settings_perm_notification));
        state.text(getString(R.string.settings_perm_title), missing.isEmpty() ? getString(R.string.settings_perm_all)
                : getString(R.string.settings_perm_missing, String.join(io.github.fortuneoflab.dualsimbands.Tr.t("、", ", "), missing)), v -> {
            if (missing.isEmpty()) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", c.getPackageName(), null)));
            else grantAll();
        });
        state.text("Shizuku", shizuku(), v -> {
            if (shizukuRunningUngranted()) { requestShizuku(true); return; }
            Intent i = c.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i != null) startActivity(i);
            else new com.google.android.material.dialog.MaterialAlertDialogBuilder(c).setMessage(R.string.labels_install_shizuku)
                    .setPositiveButton(android.R.string.ok, null).show();
        });
        LocationManager lm = c.getSystemService(LocationManager.class);
        state.text(getString(R.string.settings_location_service),
                getString(lm != null && lm.isLocationEnabled() ? R.string.state_on : R.string.state_off),
                v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        state.close();
    }

    private boolean shizukuRunningUngranted() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException e) { return false; }
    }

    private String granted(Context c, String permission) {
        return getString(c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                ? R.string.state_granted : R.string.state_not_granted);
    }

    private String shizuku() {
        try {
            if (!Shizuku.pingBinder()) return getString(io.github.fortuneoflab.dualsimbands.PrivilegedProbe.shizukuInstalled(requireContext())
                    ? R.string.settings_shizuku_stopped : R.string.settings_shizuku_missing);
            return getString(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? R.string.state_granted : R.string.state_not_granted);
        } catch (RuntimeException e) {
            return getString(R.string.settings_shizuku_stopped);
        }
    }
}
