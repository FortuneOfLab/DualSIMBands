package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

/**
 * Data-SIM switching, the one SIM change kept in the app (decision of 2026-11-01). {@link SimSettings#open} is the
 * supported path; {@link #switchTo} is the experimental Shizuku path, never concurrent with other instrumentations.
 */
public final class DataSimSwitch {
    public interface Done { void onResult(boolean success, String message); }

    private DataSimSwitch() {}

    public static boolean shizukuReady(Context c) { return Coordinator.ready(c); }

    @android.annotation.SuppressLint("ApplySharedPref") // the instrumentation reads "pending" right away
    public static void switchTo(Context context, int subscriptionId, Done done) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok = false;
            String message;
            if (!Coordinator.tryAcquire(app)) {
                String busy = Coordinator.busyText(app);
                main.post(() -> done.onResult(false, busy));
                return;
            }
            boolean[] told = {false};
            try {
                if (!Coordinator.ready(app)) throw new IllegalStateException(t("Shizukuの起動と権限許可を確認してください", "Check that Shizuku is running and allowed"));
                SharedPreferences prefs = app.getSharedPreferences(DataSimInstrumentation.PREFS, Context.MODE_PRIVATE);
                String token = UUID.randomUUID().toString();
                prefs.edit().putString("pending", token).putString("finished", "").remove("result").remove("detail").commit();
                Bundle args = new Bundle();
                args.putString("token", token);
                args.putInt("subId", subscriptionId);
                HiddenBridge.launch(app, DataSimInstrumentation.class, args);
                // Up to 5 s for the switch and 5 s for mobile data: 30 s leaves room.
                TaskEnd.await(prefs, token, 30_000, () -> {
                    told[0] = true;
                    String late = Coordinator.lateText();
                    main.post(() -> done.onResult(false, late));
                }, t("切り替え処理の応答がありません", "The switch did not respond"));
                ok = "ok".equals(prefs.getString("result", ""));
                String detail = prefs.getString("detail", "");
                message = ok ? t("データ通信SIMを切り替えました。", "Data SIM switched.") + detail : t("切り替えできませんでした：", "Could not switch: ") + detail;
            } catch (Throwable e) {
                message = t("切り替えできませんでした：", "Could not switch: ") + HiddenBridge.error(e);
            } finally {
                Coordinator.releaseLock();
            }
            if (told[0]) return;
            boolean result = ok;
            String m = message;
            main.post(() -> done.onResult(result, m));
        }, "data-sim-switch").start();
    }
}
