package io.github.fortuneoflab.dualsimbands.traffic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SubscriptionManager;
import java.util.ArrayList;
import java.util.List;
import io.github.fortuneoflab.dualsimbands.ui.AppPrefs;

/**
 * The one traffic generator, held by two users that never double the requests, both only with the "Generate traffic"
 * setting on: the bands screen while it is visible, and the monitor while history is recorded (screen closed or off
 * too). It runs while either holds it. Main thread, except {@link #window} and {@link #drain}.
 */
public final class TrafficSession {
    private static TrafficSession instance;

    public static synchronized TrafficSession get() {
        if (instance == null) instance = new TrafficSession();
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private final TrafficStats stats = new TrafficStats();
    private TrafficGenerator generator;
    private TrafficGenerator.State state;
    private final DataSimBytes bytes = new DataSimBytes();
    private boolean screenHolds, historyHolds;

    private TrafficSession() {}

    /** The bands screen became visible: holds the generator while the setting is on. */
    public void screenShown(Context c) {
        screenHolds = AppPrefs.trafficEnabled(c);
        if (screenHolds) ensureRunning(c); else stopUnheld();
    }

    /** The bands screen is hidden (closed, or the screen turned off). */
    public void screenHidden() {
        screenHolds = false;
        stopUnheld();
    }

    /** The monitor's view: hold while history is recorded and the setting is on, release otherwise. */
    public void history(Context c, boolean hold) {
        if (hold == historyHolds) return;
        historyHolds = hold;
        if (!hold) { stopUnheld(); return; }
        // Results gathered before (the bands screen alone) belong to no row, and beyond the kept times' cap their
        // count and slowest time would cover different spans: recording starts counting afresh.
        if (running()) stats.restart(SystemClock.elapsedRealtime());
        ensureRunning(c);
    }

    private void ensureRunning(Context c) {
        if (generator != null && generator.isRunning()) return;
        if (generator == null) generator = new TrafficGenerator(c.getApplicationContext(), new TrafficGenerator.Listener() {
            @Override public void onState(TrafficGenerator.State s, long b, int gen) {
                main.post(() -> {
                    // Posted before a stop or a restart: belongs to a run that is over.
                    if (generator == null || gen != generator.generation()) return;
                    state = s;
                    bytes.update(SubscriptionManager.getDefaultDataSubscriptionId(), b);
                    changed();
                });
            }
            @Override public void onAttempt(TrafficStats.Outcome outcome, long latencyMs, long beganAt) {
                stats.record(outcome, latencyMs, SystemClock.elapsedRealtime(), beganAt);
            }
            @Override public void onValidated(boolean validated) { stats.setValidated(validated); }
        });
        stats.reset();
        bytes.restart();
        state = TrafficGenerator.State.WAITING_FOR_MOBILE;
        generator.start();
        changed();
    }

    private void stopUnheld() {
        if (generator == null) return;
        if (screenHolds || historyHolds) return;
        if (generator.isRunning()) generator.stop();
        state = null;
        changed();
    }

    public boolean running() { return generator != null && generator.isRunning(); }
    /** True while the monitor holds it for history. */
    public boolean forHistory() { return running() && historyHolds; }
    public TrafficGenerator.State state() { return running() ? state : null; }
    /** Received over the data SIM in use: counted again from 0 when the data SIM changes. */
    public long bytes() { return bytes.bytes(); }

    /** The requests' results for the data SIM's history row; off when no traffic runs. Any thread. */
    public TrafficStats.Window window(long nowElapsed) { return stats.peek(running(), nowElapsed); }
    public void drain(TrafficStats.Window w) { stats.drain(w); }
    /**
     * The data SIM changed: results gathered so far are dropped, as they cannot be told apart by SIM, and so are those of
     * requests sent before now that finish later.
     */
    public void restartResults() { stats.restart(SystemClock.elapsedRealtime()); }

    public void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    private void changed() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    /** "12 KB", "3 MB": light traffic moves little, so below 1 MB the count is in KB. */
    public static String amount(long bytes) {
        return bytes < 1_000_000 ? (bytes / 1000) + " KB" : (bytes / 1_000_000) + " MB";
    }
}
