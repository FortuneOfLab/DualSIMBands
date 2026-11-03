package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent i) {
        Context c = context.getApplicationContext();
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) { prepareRestore(c); return; }
        // After an update, the old version's overrides stay until the reboot and its baselines cannot be dated:
        // nothing is re-applied in this boot (the apply would refuse anyway). Marking may wait for a running
        // apply, so it runs off the main thread.
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                if (!Store.quarantineBaselines(c)) prepareRestore(c);
            } finally { result.finish(); }
        }, "baseline-marks").start();
    }

    private static void prepareRestore(Context c) {
        if (!Store.prefs(c).getBoolean("auto", false)) return;
        Notices.show(c, t("表示名を戻す準備ができています", "Ready to restore carrier names"), t("Shizukuを起動すると、保存した表示名を戻します。", "Start Shizuku to restore the saved carrier names."));
        RestoreJob.schedule(c, 20000);
    }
}
