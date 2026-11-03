package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

/**
 * Experimental, user-confirmed only: makes one subscription the default for mobile data through the
 * system API SubscriptionManager.setDefaultDataSubId, with shell's MODIFY_PHONE_STATE delegated via
 * Shizuku for the duration of the call. The result is read back, never assumed.
 */
public final class DataSimInstrumentation extends Instrumentation {
    static final String PREFS = "datasim";
    static final String[] PERMISSIONS = {"android.permission.MODIFY_PHONE_STATE", "android.permission.READ_PHONE_STATE"};
    private Bundle args;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }

    // Delegated permissions cover the calls; the result must be on disk before the app polls it.
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
        int target = args.getInt("subId", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        String result, detail = "";
        boolean delegated = false;
        try {
            SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
            if (sm.getActiveSubscriptionInfo(target) == null) throw new IllegalStateException(t("切り替え先のSIMが有効ではありません", "The target SIM is not active"));
            HiddenBridge.delegate(PERMISSIONS); delegated = true;
            TelephonyManager base = c.getSystemService(TelephonyManager.class);
            int previous = SubscriptionManager.getDefaultDataSubscriptionId();
            // Android keeps "mobile data" on for the data SIM only and turns the old SIM off by itself, but
            // setDefaultDataSubId alone does not turn the new one on (seen on the Pixel 10a). Carry the
            // user's on/off choice over, as the Settings SIM screen does.
            boolean wasOn = SubscriptionManager.isValidSubscriptionId(previous)
                    && base.createForSubscriptionId(previous).isDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER);
            SubscriptionManager.class.getMethod("setDefaultDataSubId", int.class).invoke(sm, target);
            boolean switched = false;
            for (int i = 0; i < 20 && !switched; i++) {
                Thread.sleep(250);
                switched = SubscriptionManager.getDefaultDataSubscriptionId() == target;
            }
            if (!switched) throw new IllegalStateException(t("切り替わったことを確認できませんでした", "Could not confirm the switch"));
            // Written either way: the new data SIM may still have mobile data on from earlier.
            if (!carryData(c, target, wasOn)) throw new IllegalStateException(wasOn
                    ? t("データSIMは切り替わりましたが、切り替え先のモバイルデータをオンにできませんでした。Androidの設定で確認してください", "The data SIM was switched, but mobile data could not be turned on for it. Check Android's settings")
                    : t("データSIMは切り替わりましたが、切り替え先のモバイルデータをオフにできませんでした。Androidの設定で確認してください", "The data SIM was switched, but mobile data could not be turned off for it. Check Android's settings"));
            detail = wasOn ? t("切り替え先のモバイルデータもオンにしました。", "Mobile data was turned on for it too.")
                    : t("切り替え先のモバイルデータもオフにしました（切り替え前からオフでした）。", "Mobile data was turned off for it too (it was off before the switch).");
            result = "ok";
        } catch (Throwable e) {
            result = "error";
            detail = HiddenBridge.error(e);
        } finally {
            if (delegated) try {
                HiddenBridge.release(c, new String[]{"android.permission.MODIFY_PHONE_STATE"});
            } catch (Throwable e) {
                result = "error";
                detail = detail + t("\n一時的に借りた権限を戻せたか確認できませんでした：", "\nCould not confirm that the borrowed permissions were returned: ") + HiddenBridge.error(e);
            }
        }
        prefs.edit().putString("result", result).putString("detail", detail).putString("finished", token).commit();
        finish(0, new Bundle());
    }

    /** Sets the user's mobile-data switch of {@code subId} and reads it back; false when it did not follow. */
    @android.annotation.SuppressLint("MissingPermission") // MODIFY_PHONE_STATE is delegated by the caller
    private static boolean carryData(Context c, int subId, boolean on) throws InterruptedException {
        TelephonyManager tm = c.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
        tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER, on);
        for (int i = 0; i < 20; i++) {
            Thread.sleep(250);
            if (tm.isDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER) == on) return true;
        }
        return false;
    }
}
