package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.github.fortuneoflab.dualsimbands.radio.PhysicalChannels;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;

/** App side of the experimental probe: one run per user action, never concurrent with label writes. */
public final class PrivilegedProbe {
    public interface Done {
        /** Main thread. {@code bySub} is empty on failure; {@code atElapsed} is the probe's elapsedRealtime. */
        void onResult(Map<Integer, List<RadioSnapshot.PhysicalChannel>> bySub, long atElapsed, String message);
    }

    private PrivilegedProbe() {}

    public static boolean ready(Context c) { return Coordinator.ready(c); }

    /** Why the probe cannot run; NONE when it can. Shizuku stopped and its binder not yet received look the same here. */
    public enum Block { NONE, PHONE_PERMISSION, SHIZUKU_NOT_RUNNING, SHIZUKU_NOT_GRANTED }

    public static Block block(Context c) {
        switch (Coordinator.readiness(c)) {
            case READY: return Block.NONE;
            case NO_PHONE_PERMISSION: return Block.PHONE_PERMISSION;
            case SHIZUKU_NOT_GRANTED: return Block.SHIZUKU_NOT_GRANTED;
            default: return Block.SHIZUKU_NOT_RUNNING;
        }
    }

    public static boolean shizukuInstalled(Context c) { return Coordinator.shizukuInstalled(c); }

    @android.annotation.SuppressLint("ApplySharedPref") // the instrumentation reads "pending" right away
    public static void run(Context context, Done done) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Map<Integer, List<RadioSnapshot.PhysicalChannel>> bySub = new HashMap<>();
            long at = -1;
            String message;
            if (!Coordinator.tryAcquire(app)) {
                String busy = Coordinator.busyText(app);
                main.post(() -> done.onResult(bySub, -1, busy));
                return;
            }
            boolean[] told = {false};
            try {
                if (!Coordinator.ready(app)) throw new IllegalStateException(t("Shizukuの起動と権限許可を確認してください", "Check that Shizuku is running and allowed"));
                SharedPreferences prefs = app.getSharedPreferences(ProbeInstrumentation.PREFS, Context.MODE_PRIVATE);
                String token = UUID.randomUUID().toString();
                prefs.edit().putString("pending", token).putString("finished", "").remove("result").commit();
                Bundle args = new Bundle();
                args.putString("token", token);
                HiddenBridge.launch(app, ProbeInstrumentation.class, args);
                TaskEnd.await(prefs, token, 10_000, () -> {
                    told[0] = true;
                    String late = Coordinator.lateText();
                    main.post(() -> done.onResult(new HashMap<>(), -1, late));
                }, t("取得処理の応答がありません", "The read did not respond"));
                JSONObject r = new JSONObject(prefs.getString("result", "{}"));
                if (r.has("fatal")) throw new IllegalStateException(r.getString("fatal"));
                at = r.optLong("at", -1);
                StringBuilder b = new StringBuilder();
                JSONObject subs = r.optJSONObject("subs");
                if (subs != null) for (Iterator<String> it = subs.keys(); it.hasNext(); ) {
                    String key = it.next();
                    JSONArray arr = subs.getJSONArray(key);
                    List<RadioSnapshot.PhysicalChannel> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        RadioSnapshot.PhysicalChannel p = PhysicalChannels.from(o.optInt("type"), o.optInt("status"),
                                o.optInt("channel", Integer.MAX_VALUE), o.optInt("bandwidthKhz", Integer.MAX_VALUE),
                                o.optInt("band", 0), o.optInt("pci", Integer.MAX_VALUE));
                        if (p != null) list.add(p);
                    }
                    bySub.put(Integer.parseInt(key), list);
                    b.append(b.length() > 0 ? " / " : "").append(t("回線ID ", "subscription ID ")).append(key).append(t("：", ": ")).append(list.size()).append(t("キャリア", "carriers"));
                }
                JSONObject errors = r.optJSONObject("errors");
                if (errors != null) for (Iterator<String> it = errors.keys(); it.hasNext(); ) {
                    String key = it.next();
                    b.append(b.length() > 0 ? " / " : "").append(t("回線ID ", "subscription ID ")).append(key).append(t("：", ": ")).append(errors.getString(key));
                }
                if (r.has("releaseError")) b.append(t("\n一時的に借りた権限を戻せたか確認できませんでした：", "\nCould not confirm that the borrowed permissions were returned: ")).append(r.getString("releaseError"));
                message = b.length() == 0 ? t("取得できる情報がありませんでした", "Nothing could be read") : b.toString();
            } catch (Throwable e) {
                bySub.clear();
                message = t("取得できませんでした：", "Could not read: ") + HiddenBridge.error(e);
            } finally {
                Coordinator.releaseLock();
            }
            if (told[0]) return;
            long finalAt = at;
            String m = message;
            main.post(() -> done.onResult(bySub, finalAt, m));
        }, "privileged-probe").start();
    }
}
