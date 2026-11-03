package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class RestoreJob extends JobService {
    private static final int ID = 104;
    /** Retries 5 s apart while another task holds the lock: 150 cover the longest hold (TaskEnd.LATE_LIMIT_MS). */
    private static final int MAX_TRIES = 150;
    static void schedule(Context c, long delay) { schedule(c, delay, 0); }
    private static void schedule(Context c, long delay, int tries) {
        if (!Store.prefs(c).getBoolean("auto", false)) return;
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putInt("tries", tries);
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(ID, new ComponentName(c, RestoreJob.class))
                .setMinimumLatency(delay).setExtras(extras).build());
    }
    static void cancel(Context c) { c.getSystemService(JobScheduler.class).cancel(ID); }

    /**
     * Called from {@link SimChangeReceiver}: when the set of active lines differs from the last one seen (an eSIM
     * switched in Android's settings, a SIM swapped), the saved settings are applied again so each line shows its
     * own name and no other line's.
     */
    static void onSubscriptionsChanged(Context c) {
        java.util.List<android.telephony.SubscriptionInfo> list;
        try { list = c.getSystemService(android.telephony.SubscriptionManager.class).getActiveSubscriptionInfoList(); }
        catch (SecurityException e) { return; }
        java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
        if (list != null) for (android.telephony.SubscriptionInfo s : list) ids.add(s.getSubscriptionId());
        String now = ids.toString(), last = Store.prefs(c).getString("activeSubs", null);
        if (now.equals(last)) return;
        Store.prefs(c).edit().putString("activeSubs", now).apply();
        if (last != null) schedule(c, 3000);
    }
    @Override public boolean onStartJob(JobParameters p) {
        if (!Store.prefs(this).getBoolean("auto", false)) return false;
        if (!Store.anythingToApply(this) && !Store.changedThisBoot(this)) return false;
        // An earlier task that may still run: retrying cannot help, so the apply waits for its end instead.
        if (!Coordinator.isRunning() && TaskEnd.unsettled()) {
            Store.report(this, t("自動復元を待っています：", "Restore waiting: ") + Coordinator.busyText(this));
            Coordinator.restoreWhenFree(this);
            return false;
        }
        // Another task (an eSIM change, reading the SIM list) holds the lock for a moment, or a borrowed permission
        // is still in effect: try again shortly, a bounded number of times.
        if (Coordinator.isRunning() || Coordinator.delegationActive(this)) {
            int tries = p.getExtras().getInt("tries", 0);
            if (tries < MAX_TRIES) schedule(this, 5000, tries + 1);
            else Store.report(this, t("自動復元できませんでした：", "Could not restore automatically: ") + Coordinator.busyText(this));
            return false;
        }
        if (!Coordinator.ready(this)) {
            Store.report(this, t("自動復元待ち：Shizukuを起動してください。起動通知を受け取れない場合は、このアプリを開いてください。", "Waiting to restore: start Shizuku. If nothing happens, open this app."));
            Notices.show(this, t("復元待ち", "Waiting to restore"), t("Shizukuを起動後、必要ならこのアプリを開いてください。", "After starting Shizuku, open this app if needed."));
            return false;
        }
        try {
            // Refused while another apply runs: that one already holds the saved settings.
            return Coordinator.apply(this, false, null, () -> {
                String[] notice = RestoreNotice.text(Store.prefs(this).getBoolean("lastSuccess", false),
                        Store.prefs(this).getBoolean("displayPending", false));
                Notices.show(this, notice[0], notice[1]);
                jobFinished(p, false);
            });
        } catch (Exception e) { return false; } // only prepare throws, and there is none
    }
    @Override public boolean onStopJob(JobParameters p) { return false; }
}
