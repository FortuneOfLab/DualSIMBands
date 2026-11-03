package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.content.pm.PackageManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;

final class Coordinator {
    /** Why an apply cannot start; shown verbatim so the user knows which step is missing. */
    enum Readiness { READY, NO_PHONE_PERMISSION, SHIZUKU_NOT_RUNNING, SHIZUKU_NOT_GRANTED }

    private static final AtomicBoolean running = new AtomicBoolean(false);
    // A task that ends after its caller gave up lets a change saved meanwhile be applied.
    static { TaskEnd.setOnSettled(Coordinator::applyWaiting); }
    static boolean isRunning() { return running.get(); }
    /**
     * Shared by every instrumentation so two never run at once. Also refused while any borrowed permission is still
     * in effect: an earlier instrumentation is still running (its caller stopped waiting) or its release did not take.
     */
    static boolean tryAcquire(Context c) {
        if (!running.compareAndSet(false, true)) return false;
        if (delegationActive(c) || TaskEnd.unsettled()) { running.set(false); return false; }
        return true;
    }

    static void releaseLock() { running.set(false); applyWaiting(); }

    /** Every permission an instrumentation borrows that this app never holds otherwise (none is in the manifest). */
    private static final String[] BORROWED = {"android.permission.MODIFY_PHONE_STATE",
            "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.READ_PRECISE_PHONE_STATE"};

    /** Whether a borrowed permission is in effect for this app right now, read from the effective permissions. */
    static boolean delegationActive(Context c) {
        for (String p : BORROWED)
            if (c.checkPermission(p, android.os.Process.myPid(), android.os.Process.myUid()) == PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }

    /**
     * Why {@link #tryAcquire} refused, for the message shown to the user. A task holding the lock has its permissions
     * borrowed too, so the lock is asked first: the "still in effect" text is only for permissions no task holds.
     */
    static String busyText(Context c) {
        if (!running.get() && TaskEnd.unsettled())
            return t("前の処理が終わったことを確認できないため、新しい処理を始めません。その処理が終われば、保存した設定は自動で適用されます。変わらないときは端末を再起動してください",
                    "An earlier task could not be confirmed as ended, so no new task starts. Saved settings are applied once it ends; if nothing changes, restart the device");
        return !running.get() && delegationActive(c)
                ? t("前の処理で一時的に借りた権限がまだ有効なため、新しい処理を始めません。しばらく待っても変わらないときは、端末を再起動してください",
                    "Permissions borrowed by an earlier task are still in effect, so no new task starts. If this persists, restart the device")
                : t("別の処理（表示名の適用など）が実行中です", "Another task (such as applying carrier names) is running");
    }

    /** The message for a task whose caller stopped waiting: its outcome is not known, and nothing else starts. */
    static String lateText() {
        return t("応答がないため、結果は未確定です。この処理が終わるまで、次の操作は始めません",
                "No answer yet, so the outcome is not known. Nothing else starts until this task ends");
    }

    /** Requests to apply the saved settings as soon as the lock is free, and the context to apply them with. */
    private static final WaitingApply waiting = new WaitingApply();
    @android.annotation.SuppressLint("StaticFieldLeak") // always the application context
    private static volatile Context waitingContext;
    /** Told when an apply started by {@link #applyWhenFree} ends; set by the screen that shows the result. */
    private static volatile Runnable onWaitingApplied;
    static void setOnWaitingApplied(Runnable r) { onWaitingApplied = r; }

    /**
     * Applies the saved settings now, or right after whatever task holds the lock (an apply, an eSIM change,
     * reading the SIM list): the request is kept here, not by the screen, so no task's end can miss it.
     */
    static void applyWhenFree(Context c) { request(c, false); }

    /** Automatic restore's request: dropped if automatic restore is turned off before it runs. */
    static void restoreWhenFree(Context c) { request(c, true); }

    private static void request(Context c, boolean byRestore) {
        waitingContext = c.getApplicationContext();
        waiting.request(byRestore);
        applyWaiting();
    }

    private static void applyWaiting() {
        Context app = waitingContext;
        if (app == null || !waiting.pending() || running.get()) return;
        if (!ready(app)) { waiting.clear(); return; }
        try {
            // The requests are taken, and automatic restore read, only once the lock is held.
            waiting.runIfWanted(new WaitingApply.Gate() {
                @Override public boolean tryAcquire() { return Coordinator.tryAcquire(app); }
                @Override public void release() { releaseLock(); }
                @Override public boolean autoOn() { return Store.prefs(app).getBoolean("auto", false); }
                @Override public void start() {
                    startLocked(app, false, () -> {
                        Runnable r = onWaitingApplied;
                        if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
                    });
                }
            });
        } catch (Exception ignored) { } // the lock is given back when the start fails
    }
    static boolean ready(Context c) { return readiness(c) == Readiness.READY; }

    /** Whether the Shizuku app is installed at all (it may be installed but not running). */
    static boolean shizukuInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }
    static Readiness readiness(Context c) {
        if (c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return Readiness.NO_PHONE_PERMISSION;
        try {
            if (!Shizuku.pingBinder()) return Readiness.SHIZUKU_NOT_RUNNING;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? Readiness.READY : Readiness.SHIZUKU_NOT_GRANTED;
        } catch (RuntimeException e) { return Readiness.SHIZUKU_NOT_RUNNING; }
    }
    /** Settings saved for a request; run only once the request is accepted. */
    interface Step { void run() throws Exception; }

    /**
     * @param prepare runs on the calling thread after the lock is taken, so a refused request leaves no saved
     *     settings behind (e.g. auto-restore turned off without the restore it was meant for); may be null
     * @return false, without running {@code prepare} or {@code done}, when another apply is still running
     */
    static boolean apply(Context c, boolean restore, Step prepare, Runnable done) throws Exception {
        Context app = c.getApplicationContext();
        if (!tryAcquire(app)) return false;
        try {
            if (prepare != null) prepare.run();
            startLocked(app, restore, done);
        } catch (Throwable e) {
            // Released here only when the worker never started; once started, the worker releases it.
            running.set(false);
            throw e;
        }
        return true;
    }

    /**
     * Starts one apply or restore on a worker, under the lock the caller just took; the worker gives it back. When
     * this throws, no worker started and the caller gives the lock back.
     */
    private static void startLocked(Context app, boolean restore, Runnable done) {
        new Thread(() -> {
            try {
                applyLocked(app, restore);
            } catch (Throwable e) {
                Store.prefs(app).edit().putBoolean("lastSuccess", false).commit();
                Store.report(app, t("適用できませんでした：", "Could not apply: ") + HiddenBridge.error(e));
            } finally {
                running.set(false); done.run();
                applyWaiting();
            }
        }, "carrier-label-apply").start();
    }

    /**
     * Runs one apply or restore and waits for it, on the calling thread. The caller holds the lock
     * ({@link #tryAcquire}). The outcome is in Store's "lastSuccess" and report.
     */
    static void applyLocked(Context app, boolean restore) throws Exception {
        String token = UUID.randomUUID().toString();
        try {
            if (!ready(app)) throw new IllegalStateException(t("Shizukuを起動して権限を許可してください", "Start Shizuku and allow the permission"));
            Store.prefs(app).edit().putString("pending", token).putString("finished", "").commit();
            HiddenBridge.launch(app, token, restore);
            // Two SIMs with a slot clean-up and a stale name each take about a minute; 120 s leaves room.
            TaskEnd.await(Store.prefs(app), token, 120_000, () -> Store.report(app, lateText()),
                    t("設定処理の応答がありません。OSとの互換性を確認してください", "The settings step did not respond. Check compatibility with this OS"));
            // Config readback succeeded; separately report whether the visible name followed.
            // A failure here must not turn the confirmed write into a reported failure.
            if (!restore && Store.prefs(app).getBoolean("lastSuccess", false)) {
                try {
                    DisplayCheck.Outcome check = DisplayCheck.run(app);
                    Store.prefs(app).edit().putBoolean("displayPending", check.pending).apply();
                    if (!check.text.isEmpty()) Store.appendReport(app, check.text.trim());
                } catch (Exception e) {
                    Store.appendReport(app, t("表示の確認を完了できませんでした：", "Could not finish checking the display: ") + HiddenBridge.error(e));
                }
            }
        } finally {
            Store.prefs(app).edit().remove("pending").commit();
        }
    }
}
