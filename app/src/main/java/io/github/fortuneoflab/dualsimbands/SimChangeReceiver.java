package io.github.fortuneoflab.dualsimbands;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Wakes the app when a SIM's carrier config is (re)loaded, which happens when a line becomes active: an eSIM
 * switched in Android's settings or a SIM swapped. A listener inside the process is not enough, because Android
 * freezes this app in the background (seen on the Pixel 10a). This app's own overrides send it too; only a change
 * in the set of active lines schedules anything, so that does not loop.
 */
public final class SimChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!android.telephony.CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) return;
        RestoreJob.onSubscriptionsChanged(context.getApplicationContext());
    }
}
