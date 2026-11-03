package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import android.os.SystemClock;
import android.telephony.PhysicalChannelConfig;
import android.telephony.TelephonyCallback;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.fortuneoflab.dualsimbands.radio.PhysicalChannels;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Experimental continuous PhysicalChannelConfig, used only while the Radio tab is visible.
 * The listener is registered with TelephonyRegistry through Shizuku, so the registry sees the shell
 * identity (com.android.shell, which holds READ_PRECISE_PHONE_STATE). Shizuku does not grant system or
 * signature permissions; this only uses what the shell UID holds. Location access is renounced, and
 * {@link #stop()} unregisters everything; if this process dies, the registry drops the records itself.
 */
public final class LivePhysicalChannels {
    public interface Listener {
        /**
         * Called on a background thread; {@code atElapsed} is elapsedRealtime of the report. Called while holding the
         * lock {@link #stop()} takes, so it must return quickly and never wait for another thread (post, don't block).
         */
        void onChannels(int subscriptionId, List<RadioSnapshot.PhysicalChannel> channels, long atElapsed);
    }

    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final String LISTENER = "com.android.internal.telephony.IPhoneStateListener";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "live-pcc"));
    private final Map<Integer, Registration> registrations = new HashMap<>();
    private final Listener listener;
    private Object registry;
    private Method listen;

    public LivePhysicalChannels(Listener listener) { this.listener = listener; }

    /** Registers for each subscription; returns an error message, or null on success. */
    public synchronized String start(List<Integer> subscriptionIds) {
        try {
            if (registry == null) {
                String error = acquire();
                if (error != null) return error;
            }
            for (int subId : subscriptionIds) {
                if (registrations.containsKey(subId)) continue;
                Registration r = new Registration(subId);
                call(r, new int[]{physicalChannelEvent()});
                registrations.put(subId, r);
            }
            return null;
        } catch (Throwable e) {
            registry = null;
            stop();
            return HiddenBridge.error(e);
        }
    }

    /** Fetches the registry through Shizuku; returns an error message, or null on success. */
    private String acquire() throws Exception {
        IBinder binder = SystemServiceHelper.getSystemService("telephony.registry");
        if (binder == null) return t("TelephonyRegistryを取得できません", "Cannot get the TelephonyRegistry");
        Object proxy = Class.forName("com.android.internal.telephony.ITelephonyRegistry$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, new ShizukuBinderWrapper(binder));
        // Blocked for apps; reflection lists it only through the exemption added in LabelApp.
        for (Method m : proxy.getClass().getMethods()) if (m.getName().equals("listenWithEventList")) listen = m;
        if (listen == null) return t("このOSのlistenWithEventList形式は未対応です", "This OS's listenWithEventList signature is not supported");
        registry = proxy;
        return null;
    }

    /**
     * Unregisters every subscription (an empty event list is how TelephonyManager unregisters).
     * Without Shizuku the call cannot be made: those records stay and are retried on the next start/stop
     * (start reuses them), and the registry drops them anyway when this process ends.
     */
    public synchronized void stop() {
        java.util.Iterator<Registration> it = registrations.values().iterator();
        while (it.hasNext()) {
            Registration r = it.next();
            try {
                if (registry == null && acquire() != null) return;
                // Reports still in flight from this registration are dropped, so a line registered again at once
                // never takes one from before as its current configuration.
                r.active = false;
                call(r, new int[0]);
                it.remove();
            } catch (Throwable e) {
                r.active = true; // Still registered.
                registry = null; // The wrapped Shizuku binder may be dead; fetch a fresh one next time.
                return;
            }
        }
    }

    /** EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED is a SystemApi constant; 33 is its AOSP value. */
    private static int physicalChannelEvent() {
        try { return TelephonyCallback.class.getField("EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED").getInt(null); }
        catch (ReflectiveOperationException e) { return 33; }
    }

    public synchronized boolean isRunning() { return !registrations.isEmpty(); }

    /** Fills listenWithEventList's parameters by type, so minor signature changes across releases still fit. */
    private void call(Registration r, int[] events) throws Exception {
        Class<?>[] types = listen.getParameterTypes();
        Object[] args = new Object[types.length];
        int booleans = 0, strings = 0;
        int booleanCount = 0;
        for (Class<?> t : types) if (t == boolean.class) booleanCount++;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == boolean.class) {
                // Leading booleans renounce fine/coarse location (true); the last one is notifyNow.
                booleans++;
                boolean last = booleans == booleanCount;
                args[i] = last ? events.length > 0 : Boolean.TRUE;
            } else if (t == int.class) {
                args[i] = r.subId;
            } else if (t == String.class) {
                args[i] = strings++ == 0 ? SHELL_PACKAGE : null;
            } else if (t == int[].class) {
                args[i] = events;
            } else if (t.isInstance(r.binder)) {
                args[i] = r.binder;
            } else {
                throw new UnsupportedOperationException(t("未対応の引数: ", "Unsupported parameter: ") + t.getName());
            }
        }
        listen.invoke(registry, args);
    }

    /**
     * Our own Binder that speaks IPhoneStateListener. Only the physical-channel event is registered, so every
     * call the registry makes is onPhysicalChannelConfigChanged(in List<PhysicalChannelConfig>), parsed here
     * with the public CREATOR. This avoids TelephonyCallback internals, which are blocked for API 36+ targets.
     */
    private final class Registration {
        final int subId;
        final Object binder;
        /** False once unregistering starts; reports after that are dropped. */
        volatile boolean active = true;

        Registration(int subId) throws Exception {
            this.subId = subId;
            Receiver receiver = new Receiver();
            binder = Class.forName(LISTENER + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, receiver);
            if (binder == null) throw new IllegalStateException(t("コールバックを準備できません", "Cannot prepare the callback"));
        }

        final class Receiver extends Binder {
            Receiver() { attachInterface(null, LISTENER); }

            @Override protected boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
                if (!active) return true;
                List<PhysicalChannelConfig> configs;
                try {
                    data.enforceInterface(LISTENER);
                    configs = data.createTypedArrayList(PhysicalChannelConfig.CREATOR);
                } catch (RuntimeException e) {
                    return true; // Not the payload we registered for; ignore rather than guess.
                }
                long at = SystemClock.elapsedRealtime();
                executor.execute(() -> {
                    if (!active) return;
                    List<RadioSnapshot.PhysicalChannel> out = new ArrayList<>();
                    if (configs != null) for (PhysicalChannelConfig p : configs) {
                        RadioSnapshot.PhysicalChannel c = PhysicalChannels.from(p.getNetworkType(), p.getConnectionStatus(),
                                p.getDownlinkChannelNumber(), p.getCellBandwidthDownlinkKhz(), p.getBand(), p.getPhysicalCellId());
                        if (c != null) out.add(c);
                    }
                    // Checked and handed over under stop()'s lock: once stop() returns, nothing from this registration
                    // reaches the listener. Listeners only post to a Handler, so the lock is held briefly.
                    synchronized (LivePhysicalChannels.this) {
                        if (active) listener.onChannels(subId, out, at);
                    }
                });
                return true;
            }
        }
    }
}
