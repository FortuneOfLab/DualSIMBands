package io.github.fortuneoflab.dualsimbands.radio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Per-subscription, read-only radio observation. Callbacks are registered on a TelephonyManager
 * created with createForSubscriptionId, so every observation is attributed to its own SIM; cells
 * from different subscriptions are never merged. Nothing runs after {@link #stop()}.
 */
public final class RadioRepository {
    public interface Listener {
        /** Called on the main thread. */
        void onSnapshot(RadioSnapshot snapshot);
        /** Called on the main thread with the current active SIMs. */
        void onSubscriptions(List<SubscriptionRepository.Sim> sims);
    }

    private final Context context;
    private final TelephonyManager base;
    private final SubscriptionRepository subscriptions;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, Monitor> monitors = new HashMap<>();
    private final ActiveDataCallback activeData = new ActiveDataCallback();
    /** One callback thread for the repository's lifetime; it exits when idle. */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), r -> new Thread(r, "radio-observer"));
    /** Written on the main thread; late callbacks after stop() see false and do nothing. */
    private volatile boolean running;

    public RadioRepository(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.base = this.context.getSystemService(TelephonyManager.class);
        this.subscriptions = new SubscriptionRepository(this.context);
        this.listener = listener;
        executor.allowCoreThreadTimeOut(true);
    }

    /** Registers callbacks for all active SIMs. Call from the main thread, e.g. in onStart. */
    public void start() {
        if (running) return;
        running = true;
        // The subscription listener already runs on the executor.
        subscriptions.start(executor, this::sync);
        try { base.registerTelephonyCallback(executor, activeData); } catch (SecurityException ignored) { }
        executor.execute(() -> sync(subscriptions.current()));
    }

    /** Unregisters every callback. Call from the main thread, e.g. in onStop. */
    public void stop() {
        if (!running) return;
        running = false;
        subscriptions.stop();
        base.unregisterTelephonyCallback(activeData);
        executor.execute(() -> { for (Monitor m : monitors.values()) m.stop(); monitors.clear(); });
    }

    /** Requests a fresh CellInfo scan for every SIM; a no-op without location permission. */
    public void refreshCells() {
        executor.execute(() -> { if (running) for (Monitor m : monitors.values()) m.requestCells(); });
    }

    /**
     * Supplies PhysicalChannelConfig obtained by the experimental privileged probe for one subscription.
     * The data is a snapshot; RadioStateReducer stops trusting it once it is older than its stale limit.
     */
    public void setPhysicalChannels(int subscriptionId, List<RadioSnapshot.PhysicalChannel> channels, long atElapsed) {
        executor.execute(() -> {
            Monitor m = monitors.get(subscriptionId);
            if (m == null || !running) return;
            m.physical = channels;
            m.physicalAt = atElapsed;
            m.physicalReportedAt = atElapsed;
            m.emit();
        });
    }

    /**
     * A live listener's last report, still current while the listener stays registered (reports are pushed only on a
     * change): it stays current until {@code nowElapsed}, while the time it was reported is kept. Nothing happens when
     * a different report has arrived since.
     */
    public void keepPhysicalChannelsCurrent(int subscriptionId, List<RadioSnapshot.PhysicalChannel> channels, long nowElapsed) {
        executor.execute(() -> {
            Monitor m = monitors.get(subscriptionId);
            if (m == null || !running || m.physical != channels) return;
            m.physicalAt = nowElapsed;
            m.emit();
        });
    }

    public boolean hasFineLocation() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isLocationEnabled() {
        LocationManager lm = context.getSystemService(LocationManager.class);
        return lm != null && lm.isLocationEnabled();
    }

    // Runs on the executor.
    private void sync(List<SubscriptionRepository.Sim> sims) {
        if (!running) return;
        Map<Integer, SubscriptionRepository.Sim> wanted = new HashMap<>();
        for (SubscriptionRepository.Sim sim : sims) wanted.put(sim.subscriptionId, sim);
        monitors.entrySet().removeIf(entry -> {
            SubscriptionRepository.Sim now = wanted.get(entry.getKey());
            boolean gone = now == null || now.slotIndex != entry.getValue().slot;
            if (gone) entry.getValue().stop();
            return gone;
        });
        for (SubscriptionRepository.Sim sim : sims) {
            if (!monitors.containsKey(sim.subscriptionId)) {
                Monitor m = new Monitor(sim.subscriptionId, sim.slotIndex);
                monitors.put(sim.subscriptionId, m);
                m.start();
            }
        }
        main.post(() -> listener.onSubscriptions(sims));
        for (Monitor m : monitors.values()) m.emit();
    }

    private Cells cellsUnavailableReason() {
        if (!hasFineLocation()) return Cells.NO_PERMISSION;
        if (!isLocationEnabled()) return Cells.LOCATION_OFF;
        return null;
    }
    private enum Cells { NO_PERMISSION, LOCATION_OFF }

    private final class ActiveDataCallback extends TelephonyCallback
            implements TelephonyCallback.ActiveDataSubscriptionIdListener {
        @Override public void onActiveDataSubscriptionIdChanged(int subId) {
            if (running) for (Monitor m : monitors.values()) m.emit();
        }
    }

    /** All state is touched only on the executor thread. */
    private final class Monitor {
        final int subId, slot;
        final TelephonyManager tm;
        final StateCallback stateCallback = new StateCallback();
        CellCallback cellCallback;
        RadioSnapshot.Service service;
        RadioSnapshot.Display display;
        RadioSnapshot.Signal signal;
        RadioSnapshot.Cells cells = RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.NOT_REQUESTED);
        List<RadioSnapshot.PhysicalChannel> physical;
        long physicalAt = -1, physicalReportedAt = -1;
        boolean active;

        Monitor(int subId, int slot) {
            this.subId = subId; this.slot = slot;
            this.tm = base.createForSubscriptionId(subId);
        }

        void start() {
            active = true;
            try { tm.registerTelephonyCallback(executor, stateCallback); }
            catch (SecurityException e) { /* Leaves the parts unset, i.e. unknown. */ }
            if (cellsUnavailableReason() == null) {
                cellCallback = new CellCallback();
                try { tm.registerTelephonyCallback(executor, cellCallback); }
                catch (SecurityException e) { cellCallback = null; }
            }
            requestCells();
        }

        void stop() {
            active = false;
            tm.unregisterTelephonyCallback(stateCallback);
            if (cellCallback != null) tm.unregisterTelephonyCallback(cellCallback);
            cellCallback = null;
        }

        void requestCells() {
            if (!active) return;
            Cells reason = cellsUnavailableReason();
            if (reason != null) {
                cells = RadioSnapshot.Cells.unavailable(reason == Cells.NO_PERMISSION
                        ? RadioSnapshot.Cells.Status.NO_PERMISSION : RadioSnapshot.Cells.Status.LOCATION_OFF);
                emit();
                return;
            }
            try {
                tm.requestCellInfoUpdate(executor, new TelephonyManager.CellInfoCallback() {
                    @Override public void onCellInfo(List<CellInfo> list) { onCells(list); }
                    @Override public void onError(int errorCode, Throwable detail) {
                        if (!active) return;
                        cells = RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.ERROR);
                        emit();
                    }
                });
            } catch (SecurityException e) {
                cells = RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.NO_PERMISSION);
                emit();
            }
        }

        void onCells(List<CellInfo> list) {
            if (!active) return;
            long now = SystemClock.elapsedRealtime();
            RadioSnapshot.Cells next = AndroidRadioMapper.cells(list, now);
            // An old list (the one announced long ago, handed over when the listener registers) is not taken. The
            // snapshot is still passed on: each read is what keeps history's once-a-minute row coming when nothing
            // else changes, and the cells held go on ageing, so they show as stale.
            if (CellsFilter.take(cells, next, now)) cells = next;
            emit();
        }

        void emit() {
            if (!active) return;
            RadioSnapshot s = new RadioSnapshot(subId, slot, subId == subscriptions.defaultDataSubscriptionId(),
                    service, display, SignalFallback.effective(signal, cells, SystemClock.elapsedRealtime()), cells,
                    physical, physicalAt, physicalReportedAt);
            main.post(() -> listener.onSnapshot(s));
        }

        final class StateCallback extends TelephonyCallback implements TelephonyCallback.ServiceStateListener,
                TelephonyCallback.DisplayInfoListener, TelephonyCallback.SignalStrengthsListener {
            @Override public void onServiceStateChanged(ServiceState state) {
                if (!active) return;
                service = AndroidRadioMapper.service(state, SystemClock.elapsedRealtime());
                emit();
            }
            @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
                if (!active) return;
                display = AndroidRadioMapper.display(info, SystemClock.elapsedRealtime());
                emit();
            }
            @Override public void onSignalStrengthsChanged(SignalStrength strength) {
                if (!active) return;
                signal = AndroidRadioMapper.signal(strength, SystemClock.elapsedRealtime());
                emit();
            }
        }

        final class CellCallback extends TelephonyCallback implements TelephonyCallback.CellInfoListener {
            @Override public void onCellInfoChanged(List<CellInfo> list) { onCells(list); }
        }
    }
}
