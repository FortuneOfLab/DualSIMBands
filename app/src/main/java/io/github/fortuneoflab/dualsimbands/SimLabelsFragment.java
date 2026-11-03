package io.github.fortuneoflab.dualsimbands;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;
import io.github.fortuneoflab.dualsimbands.ui.Pref;
import io.github.fortuneoflab.dualsimbands.ui.Ui;
import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;

/**
 * Carrier display, laid out like Android's own Settings app (as the settings screen): switches and tiles that take
 * effect at once, names edited in a dialog per line, explanations in the footer. The fragment lives for the whole
 * activity (screens are shown/hidden), so its Shizuku listeners and the onResume auto-restore check keep working.
 */
public final class SimLabelsFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, notReadyBody, result;
    private LinearLayout notReady, controls, lines;
    private MaterialSwitch auto;
    /** The not-ready card's button: names the next step for the state (permission, open, install, allow). */
    private com.google.android.material.button.MaterialButton next;

    private final Shizuku.OnRequestPermissionResultListener grant = (code, granted) -> main.post(() -> {
        refreshStatus();
        Context c = getContext();
        if (c != null && granted == PackageManager.PERMISSION_GRANTED && Store.prefs(c).getBoolean("auto", false))
            RestoreJob.schedule(c, 2000);
    });
    private final Shizuku.OnBinderReceivedListener binder = () -> main.post(this::refreshStatus);
    // Without this the status kept saying "ready" after the Shizuku server stopped (seen on device).
    private final Shizuku.OnBinderDeadListener dead = () -> main.post(this::refreshStatus);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Shizuku.addRequestPermissionResultListener(grant);
        Shizuku.addBinderReceivedListenerSticky(binder);
        Shizuku.addBinderDeadListener(dead);
    }

    @Override public void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(grant);
        Shizuku.removeBinderReceivedListener(binder);
        Shizuku.removeBinderDeadListener(dead);
        super.onDestroy();
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_scroll, container, false);
        build(root.findViewById(R.id.page));
        return root;
    }

    @Override public void onDestroyView() {
        Coordinator.setOnWaitingApplied(null);
        status = null; notReadyBody = null; result = null; notReady = null; controls = null; lines = null; auto = null; next = null;
        super.onDestroyView();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // Permissions may have been granted in the settings screen meanwhile; SIMs may have changed.
        if (!hidden) { loadSims(); refreshStatus(); }
    }

    /**
     * The SIMs in use changed while this screen is shown (a SIM switched in Android's settings and back here): the
     * name rows are read again, or they kept the SIMs from when the screen was opened (seen on the Pixel 10a).
     */
    private final android.telephony.SubscriptionManager.OnSubscriptionsChangedListener simsChanged =
            new android.telephony.SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override public void onSubscriptionsChanged() { main.post(() -> { if (lines != null) loadSims(); }); }
            };

    @Override public void onPause() {
        requireContext().getSystemService(SubscriptionManager.class).removeOnSubscriptionsChangedListener(simsChanged);
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        // Also reads the rows once now: the SIMs may have changed while another app was in front.
        requireContext().getSystemService(SubscriptionManager.class)
                .addOnSubscriptionsChangedListener(requireContext().getMainExecutor(), simsChanged);
        if (lines != null) loadSims();
        if (status != null) {
            refreshStatus();
            if (Coordinator.ready(requireContext()) && Store.prefs(requireContext()).getBoolean("auto", false))
                RestoreJob.schedule(requireContext(), 2000);
        }
    }

    private void build(LinearLayout page) {
        Context c = requireContext();
        // Shown while something is missing: what it is, and a button for the next step.
        notReady = Ui.card(page);
        status = Ui.title(notReady, getString(R.string.labels_status_checking));
        notReadyBody = Ui.body(notReady, "");
        next = Ui.button(notReady, "", v -> nextStep());
        // Basic mode: without Shizuku nothing here can be changed, so the controls stay hidden.
        controls = Ui.vertical(c);
        page.addView(controls);

        // Whole-device switches: the status bar follows the data SIM, so they are applied to every active SIM.
        Pref.category(controls, getString(R.string.labels_global_title));
        Pref.Group all = new Pref.Group(controls);
        all.toggle(getString(R.string.labels_show_name), getString(R.string.labels_show_name_note), Store.showName(c),
                (b, on) -> setSwitch(Store.SHOW_NAME, on));
        all.toggle(getString(R.string.labels_always_rat), getString(R.string.labels_always_rat_note), Store.alwaysRat(c),
                (b, on) -> setSwitch(Store.ALWAYS_RAT, on));
        all.toggle(getString(R.string.labels_lte_plus), getString(R.string.labels_lte_plus_note), Store.showLtePlus(c),
                (b, on) -> setSwitch(Store.SHOW_LTE_PLUS, on));
        all.close();

        Pref.category(controls, getString(R.string.labels_shizuku_title));
        Pref.Group shizuku = new Pref.Group(controls);
        auto = shizuku.toggle(getString(R.string.labels_auto), getString(R.string.labels_auto_summary),
                Store.prefs(c).getBoolean("auto", false), (b, on) -> {
            Store.prefs(c).edit().putBoolean("auto", on).apply();
            if (on) RestoreJob.schedule(c, 3000); else RestoreJob.cancel(c);
        });
        shizuku.close();

        // A name belongs to its line (subscription), wherever the SIM sits; rebuilt when SIMs change.
        lines = Ui.vertical(c);
        controls.addView(lines);

        Pref.category(controls, getString(R.string.labels_apply_title));
        Pref.Group apply = new Pref.Group(controls);
        result = apply.text(getString(R.string.labels_result_title), "", v -> showReport());
        apply.text(getString(R.string.labels_reapply), getString(R.string.labels_reapply_summary), v -> applyNow());
        apply.text(getString(R.string.labels_restore_original), getString(R.string.labels_restore_summary), v -> confirmRestore());
        apply.close();

        Pref.footer(controls, getString(R.string.labels_footer_all), getString(R.string.labels_per_sim_note1), getString(R.string.labels_per_sim_note2),
                getString(R.string.labels_footer_5g), getString(R.string.labels_auto_note), getString(R.string.labels_footer));
        loadSims(); refreshStatus();
    }

    /** A whole-device switch takes effect at once, as in Android's settings. */
    private void setSwitch(String key, boolean on) {
        Context c = requireContext();
        try { Store.save(c, key, on); } catch (IllegalStateException e) { toast(e.getMessage()); return; }
        applyNow();
    }

    private void loadSims() {
        if (lines == null) return;
        Context c = requireContext();
        lines.removeAllViews();
        Pref.category(lines, getString(R.string.labels_names_title));
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Ui.body(lines, getString(R.string.labels_need_phone)); return;
        }
        try {
            List<SubscriptionInfo> sims = c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            JSONArray saved = Store.profiles(c);
            if (sims != null && Store.noteKinds(saved, sims)) Store.save(c, "profiles", saved.toString());
            Pref.Group names = new Pref.Group(lines);
            if (sims == null || sims.isEmpty()) names.text(getString(R.string.labels_no_sim), null, null);
            else for (SubscriptionInfo sim : sims) {
                String label = savedLabel(saved, sim);
                String title = getString(R.string.labels_sim_title, sim.getSimSlotIndex() + 1, originalName(sim));
                TextView summary = names.text(title, label != null ? getString(R.string.labels_name_set, label)
                        : getString(R.string.labels_name_unchanged), v -> editName(title, sim, label));
                // The tile's title is the first view next to its summary: give it the SIM-kind icon, as on the bands screen.
                Ui.simIcon((TextView) ((ViewGroup) summary.getParent()).getChildAt(0), sim.isEmbedded());
            }
            names.close();
            inactiveLines(saved, sims == null ? java.util.Collections.emptyList() : sims);
        } catch (Exception e) { Ui.body(lines, HiddenBridge.error(e)); }
    }

    /**
     * The line's own name for the row title: Android's SIM label (Settings > SIM), which this app never changes.
     * The carrier name would show this app's custom name, already given in the row's summary.
     */
    private static String originalName(SubscriptionInfo sim) { return Store.ownName(sim); }

    /**
     * The text the name field starts with when no custom name is set: the name Android shows now, which is then the
     * carrier's own (povo: "KDDI - povo", not the SIM label "povo"); the SIM label when it cannot be read.
     */
    private static String carrierDefault(SubscriptionInfo sim) {
        CharSequence shown = sim.getCarrierName();
        String name = shown == null ? "" : shown.toString().trim();
        return name.isEmpty() ? originalName(sim) : name;
    }

    /** The custom name saved for this active SIM, or null. */
    private static String savedLabel(JSONArray saved, SubscriptionInfo sim) throws Exception {
        for (int j = 0; j < saved.length(); j++) {
            JSONObject p = saved.getJSONObject(j);
            if (Store.matches(p, sim) && p.optBoolean("rename", false)) return p.optString("label", "");
        }
        return null;
    }

    /**
     * Saved names of lines that are not active now (another eSIM in use, SIM removed, or the subscription ID changed
     * after an eSIM was reissued). They can be edited or deleted; they are applied when the line is active again.
     */
    private void inactiveLines(JSONArray saved, List<SubscriptionInfo> sims) throws Exception {
        Context c = requireContext();
        Pref.Group group = null;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject p = saved.getJSONObject(i);
            if (!p.optBoolean("rename", false)) continue;
            boolean matched = false;
            for (SubscriptionInfo s : sims) if (Store.matches(p, s)) matched = true;
            if (matched) continue;
            if (group == null) { Pref.category(lines, getString(R.string.labels_inactive_title)); group = new Pref.Group(lines); }
            int index = i;
            String label = p.optString("label", "");
            // Laid out as the active rows: the SIM's own name, then the custom name and the line ID.
            int id = p.optInt("id", -1);
            String own = p.has("name") ? p.optString("name") : Store.knownName(c, id);
            TextView summary = group.text(own != null ? own : getString(R.string.labels_confirm_line, id),
                    getString(R.string.labels_inactive_card, label, id), v -> editInactive(index, p));
            // Saved with the entry, or seen in the bands screen's list of lines not in use; otherwise no icon.
            Boolean embedded = p.has("embedded") ? Boolean.valueOf(p.optBoolean("embedded")) : Store.knownKind(c, id);
            if (embedded != null) Ui.simIcon((TextView) ((ViewGroup) summary.getParent()).getChildAt(0), embedded);
            for (SubscriptionInfo s : sims) {
                if (!Store.sameCarrier(p, s)) continue;
                boolean taken = false;
                for (int j = 0; j < saved.length(); j++) if (Store.matches(saved.getJSONObject(j), s)) taken = true;
                if (taken) continue;
                String target = getString(R.string.labels_sim_title, s.getSimSlotIndex() + 1, String.valueOf(s.getCarrierName()));
                group.text(getString(R.string.labels_reassign, target, s.getSubscriptionId()), null, v ->
                        new MaterialAlertDialogBuilder(c).setTitle(R.string.labels_reassign_title)
                                .setMessage(getString(R.string.labels_reassign_message, target))
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.labels_reassign_confirm, (d, w) -> {
                                    try { Store.reassign(c, index, s); loadSims(); applyNow(); }
                                    catch (Exception e) { toast(HiddenBridge.error(e)); }
                                }).show());
            }
        }
        if (group != null) group.close();
    }

    /** The name dialog: a switch to change the name and the name itself, like Android's own text settings. */
    private static final class NameDialog { MaterialSwitch on; TextInputEditText name; View view; }

    private NameDialog nameDialog(String current, String hint) {
        Context c = requireContext();
        NameDialog d = new NameDialog();
        LinearLayout box = Ui.vertical(c);
        int pad = Ui.dp(c, 24);
        box.setPadding(pad, Ui.dp(c, 8), pad, 0);
        d.on = new MaterialSwitch(c);
        d.on.setText(R.string.labels_rename);
        d.on.setChecked(current != null);
        box.addView(d.on);
        TextInputLayout field = new TextInputLayout(c);
        field.setHint(getString(R.string.labels_name_hint));
        d.name = new TextInputEditText(field.getContext());
        d.name.setSingleLine(true);
        d.name.setInputType(InputType.TYPE_CLASS_TEXT);
        d.name.setText(current != null ? current : hint);
        field.addView(d.name);
        field.setEnabled(current != null);
        box.addView(field);
        d.on.setOnCheckedChangeListener((b, checked) -> field.setEnabled(checked));
        Ui.caption(box, getString(R.string.labels_rename_off_note));
        d.view = box;
        return d;
    }

    private void editName(String title, SubscriptionInfo sim, String current) {
        Context c = requireContext();
        NameDialog d = nameDialog(current, carrierDefault(sim));
        new MaterialAlertDialogBuilder(c).setTitle(title).setView(d.view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.labels_name_save, (x, w) -> {
                    String label = d.name.getText() == null ? "" : d.name.getText().toString().trim();
                    // An empty name means "no custom name": the same as turning it off.
                    boolean rename = d.on.isChecked() && !label.isEmpty();
                    try {
                        JSONArray chosen = new JSONArray();
                        if (rename) { JSONObject p = Store.profile(sim, label, true, false); Store.validate(p); chosen.put(p); }
                        java.util.Set<Integer> replaced = new java.util.HashSet<>();
                        replaced.add(sim.getSubscriptionId());
                        Store.save(c, "profiles", Store.withInactive(chosen, Store.profiles(c), replaced).toString());
                        if (rename && !Store.showName(c)) toast(getString(R.string.labels_confirm_hidden));
                        loadSims(); applyNow();
                    } catch (Exception e) { toast(HiddenBridge.error(e)); }
                }).show();
    }

    private void editInactive(int index, JSONObject p) {
        Context c = requireContext();
        NameDialog d = nameDialog(p.optString("label", ""), "");
        new MaterialAlertDialogBuilder(c).setTitle(getString(R.string.labels_confirm_line, p.optInt("id", -1))).setView(d.view)
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.labels_name_delete, (x, w) -> replaceInactive(index, null))
                .setPositiveButton(R.string.labels_name_save, (x, w) -> {
                    String label = d.name.getText() == null ? "" : d.name.getText().toString().trim();
                    replaceInactive(index, d.on.isChecked() && !label.isEmpty() ? label : null);
                }).show();
    }

    /** @param label the new name, or null to delete the line's saved name; nothing is written to Android now */
    private void replaceInactive(int index, String label) {
        Context c = requireContext();
        try {
            JSONArray old = Store.profiles(c), out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject p = old.getJSONObject(i);
                if (i != index) { out.put(p); continue; }
                if (label == null) continue;
                JSONObject q = new JSONObject(p.toString()).put("label", label);
                Store.validate(q); out.put(q);
            }
            Store.save(c, "profiles", out.toString());
            loadSims();
        } catch (Exception e) { toast(HiddenBridge.error(e)); }
    }

    private void confirmRestore() {
        Context c = requireContext();
        new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.labels_restore_title)
                .setMessage(R.string.labels_restore_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.labels_restore_confirm, (d, w) -> {
                    if (run(true, () -> {
                        Store.save(c, "auto", false);
                        RestoreJob.cancel(c);
                    }) && auto != null) auto.setChecked(false);
                }).show();
    }

    private void showReport() {
        Context c = requireContext();
        TextView body = new TextView(c);
        body.setText(Store.prefs(c).getString("report", getString(R.string.labels_no_result)));
        body.setTextIsSelectable(true);
        int pad = Ui.dp(c, 24);
        body.setPadding(pad, Ui.dp(c, 8), pad, 0);
        new MaterialAlertDialogBuilder(c).setTitle(R.string.labels_result_title).setView(body)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    /** Applies the saved settings now; a change made while another task runs is applied right after it. */
    private void applyNow() {
        Context c = requireContext();
        if (!Coordinator.ready(c)) { toast(getString(R.string.labels_check_shizuku)); return; }
        if (result != null) result.setText(R.string.labels_applying);
        Coordinator.setOnWaitingApplied(this::showApplied);
        Coordinator.applyWhenFree(c);
    }

    private void showApplied() {
        if (status != null) { refreshStatus(); loadSims(); }
    }

    /**
     * @param prepare settings saved only if the request is accepted (see {@link Coordinator#apply})
     * @return whether the request was accepted
     */
    private boolean run(boolean restore, Coordinator.Step prepare) {
        Context c = requireContext();
        if (!Coordinator.ready(c)) { toast(getString(R.string.labels_check_shizuku)); return true; }
        try {
            if (!Coordinator.apply(c, restore, prepare, () -> main.post(this::showApplied))) return false;
        } catch (Exception e) { toast(HiddenBridge.error(e)); return true; }
        if (result != null) result.setText(R.string.labels_applying);
        return true;
    }

    /** The not-ready card's button: grant the phone permission, open or get Shizuku, or allow this app in it. */
    private void nextStep() {
        Context c = requireContext();
        Coordinator.Readiness r = Coordinator.readiness(c);
        if (r == Coordinator.Readiness.NO_PHONE_PERMISSION) {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).showSettings();
        } else if (r == Coordinator.Readiness.SHIZUKU_NOT_RUNNING) {
            android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i != null) startActivity(i);
            else new MaterialAlertDialogBuilder(c).setMessage(R.string.labels_install_shizuku)
                    .setPositiveButton(android.R.string.ok, null).show();
        } else {
            try { Shizuku.requestPermission(12); } catch (RuntimeException e) { refreshStatus(); }
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        Context c = requireContext();
        Coordinator.Readiness r = Coordinator.readiness(c);
        boolean ready = r == Coordinator.Readiness.READY;
        // Ui.card returns the card's body: hide the card itself, or its frame and margins stay as a gap.
        if (notReady != null) ((View) notReady.getParent()).setVisibility(ready ? View.GONE : View.VISIBLE);
        if (controls != null) controls.setVisibility(ready ? View.VISIBLE : View.GONE);
        int title, body, button;
        switch (r) {
            case READY: title = R.string.labels_ready; body = R.string.labels_ready; button = R.string.labels_go_settings; break;
            case NO_PHONE_PERMISSION:
                title = R.string.labels_not_ready_phone; body = R.string.labels_need_phone; button = R.string.labels_go_settings; break;
            case SHIZUKU_NOT_RUNNING:
                boolean installed = Coordinator.shizukuInstalled(c);
                title = installed ? R.string.labels_not_ready_shizuku : R.string.labels_not_ready_missing;
                body = installed ? R.string.labels_basic_mode : R.string.labels_basic_mode_missing;
                button = installed ? R.string.labels_open_shizuku : R.string.labels_get_shizuku; break;
            default: title = R.string.labels_not_ready_grant; body = R.string.labels_need_grant; button = R.string.labels_allow_shizuku;
        }
        status.setText(title);
        if (notReadyBody != null) notReadyBody.setText(body);
        if (next != null) next.setText(button);
        if (result != null && !Coordinator.isRunning()) result.setText(reportSummary(c));
    }

    /** The time and the last line of the last result (for example "Applied."). */
    private String reportSummary(Context c) {
        String report = Store.prefs(c).getString("report", null);
        if (report == null || report.trim().isEmpty()) return getString(R.string.labels_no_result);
        String[] l = report.trim().split("\n");
        return l.length == 1 ? l[0] : l[0] + " · " + l[l.length - 1];
    }

    private void toast(String text) { Ui.message(this, text); }
}
