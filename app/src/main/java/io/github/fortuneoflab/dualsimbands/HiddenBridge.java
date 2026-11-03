package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/** Narrow bridge to the same platform entry points used for carrier overrides. */
final class HiddenBridge {
    private static Object activityManager() throws Exception {
        IBinder binder = SystemServiceHelper.getSystemService("activity");
        if (binder == null) throw new IllegalStateException(t("ActivityManagerを取得できません", "Cannot get the ActivityManager"));
        return Class.forName("android.app.IActivityManager$Stub").getMethod("asInterface", IBinder.class)
                .invoke(null, new ShizukuBinderWrapper(binder));
    }
    static void launch(Context context, String token, boolean restore) throws Exception {
        Bundle args = new Bundle(); args.putString("token", token); args.putBoolean("restore", restore);
        launch(context, ApplyInstrumentation.class, args);
    }
    static void launch(Context context, Class<?> instrumentation, Bundle args) throws Exception {
        // Every instrumentation starts here, also the ones run one after another under the same lock (an eSIM change
        // and the apply after it): none starts while permissions borrowed by an earlier one are still in effect.
        if (Coordinator.delegationActive(context))
            throw new IllegalStateException(t("前の処理で一時的に借りた権限がまだ有効なため、開始しませんでした", "Not started: permissions borrowed by an earlier step are still in effect"));
        Object am = activityManager();
        Object connection = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
        // INSTR_FLAG_NO_RESTART (8): do not replace the currently running app process.
        for (Method m : Class.forName("android.app.IActivityManager").getMethods()) {
            if (m.getName().equals("startInstrumentation") && m.getParameterCount() == 8) {
                Object result = m.invoke(am, new ComponentName(context, instrumentation),
                        null, 8, args, null, connection, android.os.Process.myUserHandle().hashCode(), null);
                if (Boolean.FALSE.equals(result)) throw new IllegalStateException(t("設定処理を開始できませんでした", "Could not start the settings step"));
                return;
            }
        }
        throw new UnsupportedOperationException(t("このOSのstartInstrumentation形式は未対応です", "This OS's startInstrumentation signature is not supported"));
    }
    static void delegate(boolean start) throws Exception {
        if (start) delegate(new String[]{
                "android.permission.MODIFY_PHONE_STATE", "android.permission.READ_PRIVILEGED_PHONE_STATE",
                "android.permission.READ_PHONE_STATE", "android.permission.WRITE_SECURE_SETTINGS"});
        else throw new IllegalArgumentException(t("解除にはrelease(Context)を使用してください", "Use release(Context) to release"));
    }
    /** Never pass null: a null list delegates every shell permission. */
    static void delegate(String[] permissions) throws Exception {
        if (permissions == null || permissions.length == 0) throw new IllegalArgumentException(t("委譲する権限がありません", "No permissions to delegate"));
        Object am = activityManager(); Class<?> type = Class.forName("android.app.IActivityManager");
        type.getMethod("startDelegateShellPermissionIdentity", int.class, String[].class)
                .invoke(am, android.os.Process.myUid(), permissions);
    }
    static String release(Context context) throws Exception {
        // These three privileged permissions are never requested in our manifest.
        return release(context, new String[]{"android.permission.MODIFY_PHONE_STATE",
                "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.WRITE_SECURE_SETTINGS"});
    }
    /** @param privileged permissions that must be denied again once the delegation is released */
    static String release(Context context, String[] privileged) throws Exception {
        Object am = activityManager(); Class<?> type = Class.forName("android.app.IActivityManager");
        return ShellPermissionCompat.release(am, type, android.os.Process.myUid(), () -> {
            // Check the effective permissions, rather than assuming a reflected call worked.
            for (int attempt = 0; attempt < 5; attempt++) {
                boolean denied = true;
                for (String permission : privileged) {
                    if (context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid())
                            == PackageManager.PERMISSION_GRANTED) denied = false;
                }
                if (denied) return;
                Thread.sleep(100);
            }
            throw new SecurityException(t("一時権限がまだ有効です", "The temporary permissions are still active"));
        });
    }
    static void override(CarrierConfigManager c, int id, PersistableBundle values) throws Exception {
        // Always non-persistent; never reset another tool's complete override bundle.
        CarrierConfigManager.class.getMethod("overrideConfig", int.class, PersistableBundle.class, boolean.class)
                .invoke(c, id, values, false);
    }
    static String error(Throwable e) {
        while (e instanceof InvocationTargetException && ((InvocationTargetException)e).getCause() != null) e = e.getCause();
        return e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
    }
}
