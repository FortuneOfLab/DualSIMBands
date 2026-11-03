package io.github.fortuneoflab.dualsimbands;

import android.app.Application;
import android.content.pm.PackageManager;
import com.google.android.material.color.DynamicColors;
import org.lsposed.hiddenapibypass.HiddenApiBypass;
import rikka.shizuku.Shizuku;

public final class LabelApp extends Application {
    @Override public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        Tr.init(this);
    }

    @Override public void onCreate() {
        super.onCreate();
        Tr.init(this);
        DynamicColors.applyToActivitiesIfAvailable(this);
        io.github.fortuneoflab.dualsimbands.ui.AppPrefs.applyTheme(this);
        // Only the two registry interfaces are added, for the experimental live PhysicalChannelConfig mode.
        HiddenApiBypass.addHiddenApiExemptions("Landroid/app/", "Landroid/os/", "Landroid/telephony/",
                "Lcom/android/internal/telephony/ITelephonyRegistry", "Lcom/android/internal/telephony/IPhoneStateListener");
        Shizuku.addBinderReceivedListenerSticky(() -> {
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && Store.prefs(this).getBoolean("auto", false))
                    RestoreJob.schedule(this, 3000);
            } catch (RuntimeException ignored) { /* The UI exposes current readiness. */ }
            // Recording ended while Shizuku was away: put nr_nsa_tracking_screen_off_mode back now.
            if (NrTracking.active(this) && !io.github.fortuneoflab.dualsimbands.ui.AppPrefs.historyAlways(this))
                NrTracking.sync(this, false);
        });
    }
}
