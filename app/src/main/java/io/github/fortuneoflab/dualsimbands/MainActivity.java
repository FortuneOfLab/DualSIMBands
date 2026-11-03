package io.github.fortuneoflab.dualsimbands;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.fortuneoflab.dualsimbands.monitor.MonitorService;
import io.github.fortuneoflab.dualsimbands.ui.AppPrefs;
import io.github.fortuneoflab.dualsimbands.ui.HistoryFragment;
import io.github.fortuneoflab.dualsimbands.ui.RadioFragment;
import io.github.fortuneoflab.dualsimbands.ui.SettingsFragment;
import io.github.fortuneoflab.dualsimbands.ui.TermsActivity;

/**
 * Navigation host and launcher entry point.
 * The bands screen is the main one; history sits as an icon in the top app bar and the screens opened less
 * often (carrier display, settings) in its overflow menu, after Material 3. Each opens over the bands screen
 * with an up arrow. All four screens are added once and shown/hidden, so the carrier display screen keeps its
 * Shizuku listeners and unsaved edits for the activity's lifetime, as the old single screen did.
 */
public final class MainActivity extends AppCompatActivity {
    /** Intent extra selecting the initial screen; notifications about label restore use {@link #TAB_LABELS}. */
    public static final String EXTRA_TAB = "tab";
    public static final String TAB_LABELS = "labels";

    private static final String KEY_TAB = "tab";
    private static final int[] IDS = {R.id.nav_radio, R.id.nav_labels, R.id.nav_history, R.id.nav_settings};
    private static final String[] TAGS = {"radio", "labels", "history", "settings"};
    private static final int[] TITLES = {R.string.app_name, R.string.tab_labels, R.string.tab_history, R.string.tab_settings};
    private int current = R.id.nav_radio;
    private final OnBackPressedCallback backToBands = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { show(R.id.nav_radio); }
    };

    @Override protected void onCreate(Bundle state) {
        EdgeToEdge.enable(this);
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        getOnBackPressedDispatcher().addCallback(this, backToBands);

        View appBar = findViewById(R.id.app_bar), content = findViewById(R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            content.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime));
            return insets;
        });

        FragmentManager fm = getSupportFragmentManager();
        if (state == null) {
            FragmentTransaction t = fm.beginTransaction().setReorderingAllowed(true);
            Fragment[] fragments = {new RadioFragment(), new SimLabelsFragment(), new HistoryFragment(), new SettingsFragment()};
            for (int i = 0; i < fragments.length; i++) {
                t.add(R.id.content, fragments[i], TAGS[i]);
                if (i != 0) t.hide(fragments[i]);
            }
            t.commitNow();
            if (TAB_LABELS.equals(getIntent().getStringExtra(EXTRA_TAB))) current = R.id.nav_labels;
        } else {
            current = state.getInt(KEY_TAB, R.id.nav_radio);
        }
        show(current);
    }

    @Override protected void onStart() {
        super.onStart();
        // Recording's time ran out while the monitor was not running: end it here, with a monitor kept only for it.
        if (AppPrefs.historyExpired(this)) {
            AppPrefs.endHistory(this);
            // History's hold on the traffic goes with it now, as when recording is stopped on the history screen.
            io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().history(this, false);
        }
        if (AppPrefs.monitorForHistory(this) && !AppPrefs.historyAlways(this)) AppPrefs.setMonitorEnabled(this, false);
        // An undo of nr_nsa_tracking_screen_off_mode left over from a recording that ended: done now if Shizuku is there.
        if (!AppPrefs.historyAlways(this)) NrTracking.sync(this, false);
        // The location-type monitor may only start while the app is visible, so resume it here.
        if (AppPrefs.monitorEnabled(this)) MonitorService.start(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TAB_LABELS.equals(intent.getStringExtra(EXTRA_TAB))) show(R.id.nav_labels);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, current);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /** The actions belong to the bands screen; the other screens show only the up arrow. */
    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        boolean main = current == R.id.nav_radio;
        for (int i = 0; i < menu.size(); i++) menu.getItem(i).setVisible(main);
        MenuItem mask = menu.findItem(R.id.menu_mask_ids);
        if (mask != null) mask.setChecked(!AppPrefs.showCellIds(this));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { show(R.id.nav_radio); return true; }
        if (id == R.id.nav_history || id == R.id.nav_labels || id == R.id.nav_settings) { show(id); return true; }
        if (id == R.id.menu_mask_ids) {
            // The same setting as "Show cell ID, TAC and TA" in Settings, the other way round.
            AppPrefs.setShowCellIds(this, item.isChecked());
            Fragment radio = getSupportFragmentManager().findFragmentByTag(TAGS[0]);
            if (radio instanceof RadioFragment) ((RadioFragment) radio).idSettingChanged();
            return true;
        }
        if (id == R.id.menu_sim_settings) { SimSettings.open(this); return true; }
        if (id == R.id.menu_terms) { startActivity(new Intent(this, TermsActivity.class)); return true; }
        if (id == R.id.menu_about) { about(); return true; }
        return super.onOptionsItemSelected(item);
    }

    /** Used by the carrier display screen when permissions or Shizuku still need attention. */
    public void showSettings() { show(R.id.nav_settings); }

    private void about() {
        String text = getString(R.string.settings_app_id) + "\n" + getPackageName();
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            text = getString(R.string.settings_version) + "\n" + info.versionName + " (" + info.getLongVersionCode() + ")\n\n" + text;
        } catch (PackageManager.NameNotFoundException ignored) { }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.app_name).setMessage(text)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void show(int id) {
        current = id;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction t = fm.beginTransaction().setReorderingAllowed(true);
        for (int i = 0; i < IDS.length; i++) {
            Fragment f = fm.findFragmentByTag(TAGS[i]);
            if (f == null) continue;
            if (IDS[i] == id) t.show(f); else t.hide(f);
            if (IDS[i] == id && getSupportActionBar() != null) getSupportActionBar().setTitle(TITLES[i]);
        }
        t.commit();
        boolean main = id == R.id.nav_radio;
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(!main);
        backToBands.setEnabled(!main);
        invalidateOptionsMenu();
    }
}
