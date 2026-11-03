package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.PhysicalChannelConfig;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Experimental, user-triggered only: reads PhysicalChannelConfig per subscription while shell's
 * READ_PRECISE_PHONE_STATE is temporarily delegated through Shizuku, then releases the delegation.
 * Shizuku does not grant system or signature permissions; this only borrows what the shell UID holds.
 */
public final class ProbeInstrumentation extends Instrumentation {
    static final String PREFS = "probe";
    static final String[] PERMISSIONS = {"android.permission.READ_PRECISE_PHONE_STATE", "android.permission.READ_PHONE_STATE"};
    private static final long WAIT_MS = 3000;
    private Bundle args;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }

    // READ_PHONE_STATE is held (and additionally delegated); results must be on disk before the app polls them.
    @android.annotation.SuppressLint({"MissingPermission", "ApplySharedPref"})
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = args == null ? "" : args.getString("token", "");
        if (token.isEmpty() || !token.equals(prefs.getString("pending", ""))) {
            // Nothing done, nothing borrowed: lets a caller that gave up on this token know it has ended.
            if (!token.isEmpty()) prefs.edit().putString(TaskEnd.ABANDONED, token).commit();
            finish(1, new Bundle());
            return;
        }
        JSONObject result = new JSONObject();
        boolean delegated = false;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            HiddenBridge.delegate(PERMISSIONS); delegated = true;
            JSONObject subs = new JSONObject(), errors = new JSONObject();
            List<SubscriptionInfo> active = c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            TelephonyManager base = c.getSystemService(TelephonyManager.class);
            if (active != null) for (SubscriptionInfo sim : active) {
                int id = sim.getSubscriptionId();
                try { subs.put(Integer.toString(id), read(base.createForSubscriptionId(id), executor)); }
                catch (Exception e) { errors.put(Integer.toString(id), HiddenBridge.error(e)); }
            }
            result.put("at", SystemClock.elapsedRealtime());
            result.put("wall", System.currentTimeMillis());
            result.put("subs", subs);
            result.put("errors", errors);
        } catch (Throwable e) {
            try { result.put("fatal", HiddenBridge.error(e)); } catch (Exception ignored) { }
        } finally {
            executor.shutdownNow();
            if (delegated) try {
                result.put("release", HiddenBridge.release(c, new String[]{"android.permission.READ_PRECISE_PHONE_STATE"}));
            } catch (Throwable e) {
                try { result.put("releaseError", HiddenBridge.error(e)); } catch (Exception ignored) { }
            }
            prefs.edit().putString("result", result.toString()).putString("finished", token).commit();
            finish(0, new Bundle());
        }
    }

    /** Registers, takes the first (current) report, unregisters. Empty array when the modem reports none. */
    private static JSONArray read(TelephonyManager tm, ExecutorService executor) throws Exception {
        AtomicReference<List<PhysicalChannelConfig>> got = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final class Listener extends TelephonyCallback implements TelephonyCallback.PhysicalChannelConfigListener {
            @Override public void onPhysicalChannelConfigChanged(List<PhysicalChannelConfig> configs) {
                got.set(configs); latch.countDown();
            }
        }
        Listener listener = new Listener();
        tm.registerTelephonyCallback(executor, listener);
        try {
            if (!latch.await(WAIT_MS, TimeUnit.MILLISECONDS)) throw new IllegalStateException(t("応答がありません（", "No response (") + WAIT_MS + t("ms）", " ms)"));
        } finally {
            tm.unregisterTelephonyCallback(listener);
        }
        JSONArray out = new JSONArray();
        List<PhysicalChannelConfig> configs = got.get();
        if (configs != null) for (PhysicalChannelConfig p : configs) {
            JSONObject o = new JSONObject();
            o.put("type", p.getNetworkType());
            o.put("status", p.getConnectionStatus());
            o.put("pci", p.getPhysicalCellId());
            o.put("channel", p.getDownlinkChannelNumber());
            o.put("bandwidthKhz", p.getCellBandwidthDownlinkKhz());
            o.put("band", p.getBand());
            out.put(o);
        }
        return out;
    }
}
