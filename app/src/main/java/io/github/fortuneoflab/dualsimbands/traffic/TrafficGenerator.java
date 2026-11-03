package io.github.fortuneoflab.dualsimbands.traffic;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import io.github.fortuneoflab.dualsimbands.ui.AppPrefs;

/**
 * Generates light mobile traffic on purpose (a 1 KB download at the chosen interval, 2 s at first), so the network adds carriers (4G+ CA, 5G+ NR)
 * that only appear while data flows, and each request's answer is measured. Always on the cellular network (even when
 * Wi-Fi is the default). Runs between {@link #start} and {@link #stop}. Data is only received
 * from a public speed-test endpoint; nothing about the device is sent.
 */
public final class TrafficGenerator {
    public interface Listener {
        /**
         * Called on a background thread whenever the state or the byte count changes; {@code generation} is the run's,
         * for a receiver that hands it on to another thread to compare with {@link #generation()} there.
         */
        void onState(State state, long bytes, int generation);
        /**
         * One request finished; {@code latencyMs} (request to first byte) counts only for OK, {@code beganAt} is when
         * it was sent (elapsedRealtime). Background thread.
         */
        void onAttempt(TrafficStats.Outcome outcome, long latencyMs, long beganAt);
        /** Android's validation of internet access on the mobile network changed. Background thread. */
        void onValidated(boolean validated);
    }

    public enum State { WAITING_FOR_MOBILE, RUNNING, ERROR, STOPPED }

    static final String HOST = "https://speed.cloudflare.com/__down?bytes=";
    private static final long CHUNK = 1000;

    private final ConnectivityManager cm;
    private final Context app;
    private final Listener listener;
    private ConnectivityManager.NetworkCallback callback;
    private volatile Thread worker;
    private volatile boolean running;
    private volatile long bytes;
    /**
     * Counts starts and stops. A thread, read or callback of an earlier run checks it before touching the state, so a
     * read still blocked when the generator is stopped and started again adds nothing to the new run.
     */
    private volatile int generation;

    public TrafficGenerator(Context c, Listener listener) {
        cm = c.getSystemService(ConnectivityManager.class);
        app = c.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        int gen = ++generation;
        bytes = 0;
        listener.onState(State.WAITING_FOR_MOBILE, 0, gen);
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { run(network, gen); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                synchronized (TrafficGenerator.this) {
                    if (gen == generation) listener.onValidated(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                }
            }
            @Override public void onLost(Network network) {
                synchronized (TrafficGenerator.this) { if (gen == generation) interruptWorker(); }
            }
        };
        cm.requestNetwork(req, callback);
    }

    public boolean isRunning() { return running; }
    /** The current run's number: start and stop each count it up. */
    public int generation() { return generation; }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        generation++;
        interruptWorker();
        try { cm.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) { /* already gone */ }
        callback = null;
        listener.onState(State.STOPPED, bytes, generation);
    }

    private synchronized void run(Network network, int gen) {
        if (gen != generation) return;
        interruptWorker();
        Thread t = new Thread(() -> loop(network, gen), "traffic");
        worker = t;
        t.start();
    }

    private void interruptWorker() {
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
    }

    /** This thread still belongs to the run that started it. */
    private boolean current(Thread self, int gen) { return running && worker == self && gen == generation; }

    /**
     * Applies {@code effect} only if this thread still belongs to its run, checked under the same lock that start and
     * stop take, so no stop and restart can come between the check and the effect. The effect, and every listener
     * method, must stay short and never wait for another thread (they update counters and post to the main thread):
     * a wait here could deadlock with start or stop.
     */
    private synchronized boolean deliver(Thread self, int gen, Runnable effect) {
        if (!current(self, gen)) return false;
        effect.run();
        return true;
    }

    private void loop(Network network, int gen) {
        Thread self = Thread.currentThread();
        if (!deliver(self, gen, () -> listener.onState(State.RUNNING, bytes, gen))) return;
        byte[] buf = new byte[64 * 1024];
        while (current(self, gen)) {
            long begin = SystemClock.elapsedRealtime();
            TrafficStats.Outcome outcome = TrafficStats.Outcome.OK;
            long[] latency = {-1};
            try {
                HttpURLConnection http = (HttpURLConnection) network.openConnection(new URL(HOST + CHUNK));
                http.setConnectTimeout(10_000);
                http.setReadTimeout(15_000);
                http.setRequestProperty("User-Agent", "DualSIMBands");
                try (InputStream in = http.getInputStream()) {
                    int n;
                    while (current(self, gen) && (n = in.read(buf)) > 0) {
                        int read = n;
                        // A read that returns after a stop belongs to the old run.
                        if (!deliver(self, gen, () -> {
                            if (latency[0] < 0) latency[0] = SystemClock.elapsedRealtime() - begin;
                            bytes += read;
                        })) break;
                    }
                } finally {
                    http.disconnect();
                }
            } catch (SocketTimeoutException e) {
                outcome = TrafficStats.Outcome.TIMEOUT;
            } catch (Exception e) {
                outcome = TrafficStats.Outcome.ERROR;
            }
            // Stopped, or the network went away mid-request: not a result of the network's answer.
            TrafficStats.Outcome result = outcome;
            if (!deliver(self, gen, () -> {
                listener.onAttempt(result, latency[0] < 0 ? 0 : latency[0], begin);
                listener.onState(result == TrafficStats.Outcome.OK ? State.RUNNING : State.ERROR, bytes, gen);
            })) return;
            // Read each time, so a change in Settings applies from the next request.
            try { Thread.sleep(AppPrefs.trafficIntervalSeconds(app) * 1000L); } catch (InterruptedException e) { return; }
        }
    }
}
