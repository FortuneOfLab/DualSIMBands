package io.github.fortuneoflab.dualsimbands.ui;

import android.Manifest;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.history.HistoryRecorder;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioRepository;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.RadioStateReducer;
import io.github.fortuneoflab.dualsimbands.radio.SubscriptionRepository;

/**
 * Radio tab. Observes only while visible and started: callbacks are registered in begin() and
 * removed in end(), so nothing keeps running when the tab is hidden or the app is in the background.
 */
public final class RadioFragment extends Fragment implements RadioRepository.Listener {
    /** Ages tick every second; callback bursts are coalesced so the cards redraw at most twice a second. */
    private static final long CELL_REFRESH_MS = 5_000, RENDER_MS = 1_000, COALESCE_MS = 500, STALE_AFTER_MS = 30_000;

    private RadioRepository repository;
    private final Map<Integer, RadioSnapshot> snapshots = new HashMap<>();
    /** When each SIM's snapshot arrived (elapsedRealtime): an old one is drawn but not handed to history. */
    private final Map<Integer, Long> receivedAt = new HashMap<>();
    private List<SubscriptionRepository.Sim> sims = new ArrayList<>();
    private final Set<Integer> expanded = new HashSet<>();
    /** Recent signal per subscription while this tab is visible (in memory only). */
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** inactiveBox: with no SIM in use, the button to Android's SIM settings. */
    private LinearLayout banner, cards, inactiveBox;
    private com.google.android.material.button.MaterialButton probeButton;
    private final Map<Integer, RadioCard> cardViews = new HashMap<>();
    private List<Integer> cardOrder = new ArrayList<>();
    private boolean observing, wifiDefault, locationDenied;
    private ConnectivityManager.NetworkCallback network;
    private ActivityResultLauncher<String[]> permissionRequest;

    private final Runnable cellTick = new Runnable() {
        @Override public void run() {
            repository.refreshCells();
            startLive();
            // Pushed only on change: while the listener is registered the last report is still current.
            long now = SystemClock.elapsedRealtime();
            // Only while that line's registration stands: after a failed or dropped one, the last report ages and
            // goes stale instead of standing as verified (as in MonitorService).
            for (Map.Entry<Integer, List<RadioSnapshot.PhysicalChannel>> e : lastLive.entrySet())
                if (liveCurrent(e.getKey())) repository.keepPhysicalChannelsCurrent(e.getKey(), e.getValue(), now);
            handler.postDelayed(this, CELL_REFRESH_MS);
        }
    };
    private final Runnable renderTick = new Runnable() {
        @Override public void run() { requestRender(); handler.postDelayed(this, RENDER_MS); }
    };
    private boolean renderPending;
    /** Experimental live PhysicalChannelConfig (Shizuku), registered only between begin() and end(). */
    private io.github.fortuneoflab.dualsimbands.LivePhysicalChannels live;
    private final Map<Integer, List<RadioSnapshot.PhysicalChannel>> lastLive = new HashMap<>();
    /** The lines the live listener was registered for by the last successful start; empty otherwise. */
    private List<Integer> liveIds = new ArrayList<>();
    private String liveError, shownProblem;
    /** Opt-in traffic, held between begin() and end() (history may hold it longer). */
    private final Runnable trafficChanged = this::requestRender;
    private final Runnable renderNow = () -> { renderPending = false; render(); };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new RadioRepository(requireContext(), this);
        permissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            locationDenied = !repository.hasFineLocation();
            if (observing) { end(); begin(); }
            renderBanner();
        });
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_scroll, container, false);
        LinearLayout page = root.findViewById(R.id.page);
        probeButton = Ui.outlined(page, getString(R.string.radio_probe), v -> runProbe());
        banner = Ui.vertical(requireContext());
        page.addView(banner);
        cards = Ui.vertical(requireContext());
        page.addView(cards);
        inactiveBox = Ui.vertical(requireContext());
        page.addView(inactiveBox);
        return root;
    }

    @Override public void onStart() {
        super.onStart();
        if (!isHidden()) begin();
    }

    @Override public void onStop() {
        end();
        super.onStop();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) end();
        else if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) begin();
    }

    @Override public void onDestroyView() {
        end();
        banner = null; cards = null; probeButton = null; inactiveBox = null;
        cardViews.clear();
        cardOrder = new ArrayList<>();
        super.onDestroyView();
    }

    private void begin() {
        if (observing || cards == null) return;
        observing = true;
        if (Demo.source != null) {
            // Samples only: nothing of the device is read, recorded or generated.
            probeButton.setVisibility(View.GONE);
            handler.postDelayed(renderTick, RENDER_MS);
            render();
            return;
        }
        repository.start();
        io.github.fortuneoflab.dualsimbands.history.HistoryLocation.start(requireContext());
        renderInactive();
        ConnectivityManager cm = requireContext().getSystemService(ConnectivityManager.class);
        network = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities caps) {
                boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                handler.post(() -> { wifiDefault = wifi; renderBanner(); });
            }
            @Override public void onLost(@NonNull Network n) { handler.post(() -> { wifiDefault = false; renderBanner(); }); }
        };
        cm.registerDefaultNetworkCallback(network);
        handler.postDelayed(cellTick, CELL_REFRESH_MS);
        handler.postDelayed(renderTick, RENDER_MS);
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().addListener(trafficChanged);
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().screenShown(requireContext());
        renderBanner();
        render();
    }

    private boolean wantedBySettings() {
        android.content.Context c = requireContext();
        return AppPrefs.probeEnabled(c) && AppPrefs.probeLive(c);
    }

    private boolean liveWanted() {
        return wantedBySettings() && io.github.fortuneoflab.dualsimbands.PrivilegedProbe.ready(requireContext());
    }

    /** Also retried from cellTick, so Shizuku starting while the tab is open is picked up. */
    private void startLive() {
        if (!observing) return;
        if (!liveWanted() || sims.isEmpty()) {
            // A switch turned off, or an unregister that could not run while Shizuku was down: retry it.
            if (live != null && live.isRunning() && !wantedBySettings()) live.stop();
            // Once nothing is registered, nothing keeps the last reports current.
            if (live == null || !live.isRunning()) { lastLive.clear(); liveIds = new ArrayList<>(); }
            refreshProblem();
            return;
        }
        if (live == null) live = new io.github.fortuneoflab.dualsimbands.LivePhysicalChannels((subId, channels, at) -> handler.post(() -> {
            if (!observing || !liveCurrent(subId)) return;
            lastLive.put(subId, channels);
            repository.setPhysicalChannels(subId, channels, at);
        }));
        List<Integer> ids = new ArrayList<>();
        for (SubscriptionRepository.Sim s : sims) ids.add(s.subscriptionId);
        // The SIMs changed: register afresh, so a line no longer in use is not kept.
        if (!ids.equals(liveIds) && live.isRunning()) {
            live.stop();
            lastLive.keySet().retainAll(ids);
        }
        String error = live.start(ids);
        if (error != null) {
            android.util.Log.w("DualSIMBands", "live PhysicalChannelConfig: " + error);
            lastLive.clear();
        }
        liveIds = error == null ? ids : new ArrayList<>();
        liveError = error;
        refreshProblem();
    }

    /** A live report for this line counts: the listener is registered, and was registered for it. */
    private boolean liveCurrent(int subId) {
        return live != null && live.isRunning() && liveIds.contains(subId);
    }

    private void refreshProblem() {
        String p = liveProblem();
        if (java.util.Objects.equals(p, shownProblem)) return;
        shownProblem = p;
        renderBanner();
    }

    /** The one place that says the composition could not be read through Shizuku; null when it can. */
    private String liveProblem() {
        android.content.Context c = requireContext();
        if (!wantedBySettings()) return null;
        // Registrations made earlier keep delivering even if Shizuku has stopped since.
        if (live != null && live.isRunning() && liveError == null) return null;
        // Basic mode: without Shizuku the composition comes from the cell list; nothing to warn about.
        if (!io.github.fortuneoflab.dualsimbands.PrivilegedProbe.ready(c)) return null;
        return liveError == null ? null : getString(R.string.radio_live_unavailable, liveError);
    }

    /** Generated traffic for the data SIM's header: the amount, "waiting" or "no answer" beside the download icon. */
    private RadioCard.Traffic traffic() {
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession t = io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get();
        io.github.fortuneoflab.dualsimbands.traffic.TrafficGenerator.State state = t.state();
        if (state == null) return null;
        String amount = io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.amount(t.bytes());
        switch (state) {
            case WAITING_FOR_MOBILE: return new RadioCard.Traffic(getString(R.string.radio_traffic_waiting),
                    getString(R.string.radio_traffic_waiting_spoken), false);
            case ERROR: return new RadioCard.Traffic(getString(R.string.radio_traffic_error),
                    getString(R.string.radio_traffic_error_spoken, amount), true);
            default: return new RadioCard.Traffic(amount, getString(R.string.radio_traffic_spoken, amount), false);
        }
    }

    private void end() {
        if (!observing) return;
        observing = false;
        if (Demo.source != null) {
            handler.removeCallbacks(renderTick);
            handler.removeCallbacks(renderNow);
            renderPending = false;
            return;
        }
        // Not kept for the next visit: drawn again on opening, before fresh ones arrive, they were also handed to
        // history as current (seen on the Pixel 10a: rows with a band and composition from up to an hour before).
        snapshots.clear();
        receivedAt.clear();
        io.github.fortuneoflab.dualsimbands.history.HistoryLocation.stop(requireContext());
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().removeListener(trafficChanged);
        io.github.fortuneoflab.dualsimbands.traffic.TrafficSession.get().screenHidden();
        if (live != null) live.stop();
        lastLive.clear();
        liveIds = new ArrayList<>();
        repository.stop();
        handler.removeCallbacks(cellTick);
        handler.removeCallbacks(renderTick);
        handler.removeCallbacks(renderNow);
        renderPending = false;
        if (network != null) {
            requireContext().getSystemService(ConnectivityManager.class).unregisterNetworkCallback(network);
            network = null;
        }
    }

    @Override public void onSnapshot(RadioSnapshot snapshot) {
        // Posted before end(), or sent while the repository was still stopping: not kept, or it would be drawn on
        // the next opening as the state found then.
        if (!observing) return;
        snapshots.put(snapshot.subscriptionId, snapshot);
        receivedAt.put(snapshot.subscriptionId, SystemClock.elapsedRealtime());
        requestRender();
    }

    @Override public void onSubscriptions(List<SubscriptionRepository.Sim> current) {
        // The repository reads the SIMs again when it starts.
        if (!observing) return;
        boolean changed = !ids(current).equals(ids(sims));
        sims = current;
        if (changed) renderInactive();
        startLive();
        Set<Integer> ids = new HashSet<>();
        for (SubscriptionRepository.Sim s : current) ids.add(s.subscriptionId);
        snapshots.keySet().retainAll(ids);
        receivedAt.keySet().retainAll(ids);
        requestRender();
    }

    /** Signal and ServiceState callbacks arrive in bursts; rebuilding the cards for each one wastes power. */
    /** Cell ID, TAC and TA shown or masked (the ⋮ menu): drawn again at once, not at the next tick. */
    public void idSettingChanged() { render(); }

    private void requestRender() {
        if (renderPending || cards == null) return;
        renderPending = true;
        handler.postDelayed(renderNow, COALESCE_MS);
    }

    private static Set<Integer> ids(List<SubscriptionRepository.Sim> list) {
        Set<Integer> out = new HashSet<>();
        for (SubscriptionRepository.Sim x : list) out.add(x.subscriptionId);
        return out;
    }

    /**
     * With no SIM in use, a button to Android's SIM settings. SIMs are turned on, off and swapped there, and the data,
     * call and SMS SIMs chosen (decision of 2026-11-01: Android keeps those defaults and mobile data consistent).
     */
    private void renderInactive() {
        if (inactiveBox == null || getContext() == null) return;
        inactiveBox.removeAllViews();
        if (sims.isEmpty()) Ui.outlined(inactiveBox, getString(R.string.radio_menu_settings),
                v -> io.github.fortuneoflab.dualsimbands.SimSettings.open(requireContext()));
    }

    private boolean inAppDataSwitch() {
        return AppPrefs.dataSimSwitchEnabled(requireContext()) && io.github.fortuneoflab.dualsimbands.DataSimSwitch.shizukuReady(requireContext());
    }

    /**
     * The card's button, on a SIM not used for data (Android's SIM settings for everything else are in the app bar's
     * menu). Supported path: Android's SIM settings. Experimental path: confirmed in-app switch via Shizuku.
     */
    private void useForData(SubscriptionRepository.Sim sim) {
        android.content.Context c = requireContext();
        if (!inAppDataSwitch()) { io.github.fortuneoflab.dualsimbands.SimSettings.open(c); return; }
        String name = getString(R.string.radio_sim_title, sim.slotIndex + 1, SimLook.name(sim));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle(getString(R.string.radio_use_for_data_title, name))
                .setMessage(R.string.radio_use_for_data_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.radio_use_for_data_settings, (d, w) -> io.github.fortuneoflab.dualsimbands.SimSettings.open(c))
                .setPositiveButton(R.string.radio_use_for_data_confirm, (d, w) ->
                        io.github.fortuneoflab.dualsimbands.DataSimSwitch.switchTo(c, sim.subscriptionId, (ok, message) -> {
                            Ui.message(this, message);
                            render();
                        })).show();
    }

    /** The latest info for this SIM: its carrier name can change without a rebuild. */
    private SubscriptionRepository.Sim current(SubscriptionRepository.Sim sim) {
        for (SubscriptionRepository.Sim x : sims) if (x.subscriptionId == sim.subscriptionId) return x;
        return sim;
    }

    /** Experimental: one PhysicalChannelConfig read through Shizuku, only when the user asks. */
    private void runProbe() {
        probeButton.setEnabled(false);
        io.github.fortuneoflab.dualsimbands.PrivilegedProbe.run(requireContext(), (bySub, at, message) -> {
            if (probeButton != null) probeButton.setEnabled(true);
            for (Map.Entry<Integer, List<io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot.PhysicalChannel>> e : bySub.entrySet())
                repository.setPhysicalChannels(e.getKey(), e.getValue(), at);
            Ui.message(this, message);
        });
    }

    private void renderBanner() {
        if (banner == null) return;
        // The one-shot button is not needed while the live listener runs.
        if (probeButton != null) probeButton.setVisibility(AppPrefs.probeEnabled(requireContext())
                && !AppPrefs.probeLive(requireContext())
                && io.github.fortuneoflab.dualsimbands.PrivilegedProbe.ready(requireContext()) ? View.VISIBLE : View.GONE);
        banner.removeAllViews();
        if (!repository.hasFineLocation()) {
            LinearLayout b = Ui.card(banner);
            Ui.title(b, getString(R.string.radio_location_title));
            Ui.body(b, getString(R.string.radio_location_body));
            Ui.button(b, getString(R.string.radio_location_grant), v -> permissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}));
            if (locationDenied) Ui.outlined(b, getString(R.string.action_app_settings), v -> startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", requireContext().getPackageName(), null))));
        } else if (!repository.isLocationEnabled()) {
            LinearLayout b = Ui.card(banner);
            Ui.title(b, getString(R.string.radio_location_off_title));
            Ui.body(b, getString(R.string.radio_location_off_body));
            Ui.button(b, getString(R.string.action_location_settings),
                    v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        }
        shownProblem = liveProblem();
        if (shownProblem != null) Ui.caption(banner, shownProblem);
    }

    private void render() {
        if (cards == null) return;
        Demo.Source demo = Demo.source;
        if (demo != null) {
            sims = demo.sims();
            snapshots.clear();
            snapshots.putAll(demo.snapshots(SystemClock.elapsedRealtime()));
        }
        boolean phone = demo != null || requireContext().checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        String message = !phone ? getString(R.string.radio_phone_permission)
                : sims.isEmpty() ? getString(R.string.radio_no_sim) : null;
        List<Integer> order = new ArrayList<>();
        for (SubscriptionRepository.Sim sim : sims) order.add(sim.subscriptionId);
        if (message != null || !order.equals(cardOrder)) {
            // The set of SIMs changed: rebuild the frames once.
            cards.removeAllViews();
            cardViews.clear();
            cardOrder = message != null ? new ArrayList<>() : order;
            if (message != null) { Ui.body(Ui.card(cards), message); return; }
            for (SubscriptionRepository.Sim sim : sims) {
                int id = sim.subscriptionId;
                cardViews.put(id, new RadioCard(cards, v -> {
                    if (!expanded.remove(id)) expanded.add(id);
                    render();
                }, v -> useForData(current(sim))));
            }
        }
        long now = SystemClock.elapsedRealtime();
        boolean showIds = AppPrefs.showCellIds(requireContext());
        RadioCard.Traffic traffic = demo != null ? demo.traffic() : traffic();
        boolean inAppData = demo != null || inAppDataSwitch();
        for (SubscriptionRepository.Sim sim : sims) {
            RadioSnapshot s = snapshots.get(sim.subscriptionId);
            RadioAssessment a = s == null ? null : RadioStateReducer.reduce(s, now, STALE_AFTER_MS);
            cardViews.get(sim.subscriptionId).bind(sim, s, a, s != null && s.defaultData ? traffic : null,
                    expanded.contains(sim.subscriptionId), showIds, inAppData);
            // Only a snapshot received lately counts as current for history (the cell reads bring one every few
            // seconds); an older one would be another time's state written as now. Hidden, the screen hands nothing.
            Long at = receivedAt.get(sim.subscriptionId);
            if (observing && demo == null && a != null && at != null && now - at <= STALE_AFTER_MS)
                HistoryRecorder.offer(requireContext(), sim.subscriptionId, sim.slotIndex, sim.carrierName, s, a, now);
        }
    }
}
