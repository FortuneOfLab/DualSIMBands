package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class ApplyInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }
    // Carrier config reads run with the delegated shell permissions; results must be on disk before the app polls them.
    @android.annotation.SuppressLint({"MissingPermission", "ApplySharedPref"})
    @Override public void onStart() {
        Context c = getTargetContext(); SharedPreferences prefs = Store.prefs(c);
        String token = args == null ? "" : args.getString("token", "");
        if (token.isEmpty() || !token.equals(prefs.getString("pending", ""))) {
            // Nothing done, nothing borrowed: lets a caller that gave up on this token know it has ended.
            if (!token.isEmpty()) prefs.edit().putString(TaskEnd.ABANDONED, token).commit();
            finish(1, new Bundle());
            return;
        }
        boolean delegated = false; StringBuilder report = new StringBuilder(); boolean success = false; String done = "";
        try {
            HiddenBridge.delegate(true); delegated = true;
            SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
            CarrierConfigManager cm = c.getSystemService(CarrierConfigManager.class);
            // A network name filter left changed by a run that ended halfway is put back before anything else.
            settleLeftFilters(c, cm, report);
            List<SubscriptionInfo> active = sm.getActiveSubscriptionInfoList();
            if (active == null || active.isEmpty()) throw new IllegalStateException(t("有効なSIMがありません。SIM準備後に再実行してください", "No active SIM. Try again once a SIM is ready"));
            boolean restore = args.getBoolean("restore");
            // An apply covers every active SIM: the switches reach them all; names only the SIMs given one.
            JSONArray profiles = restore ? new JSONArray() : Store.targets(c, active);
            boolean showName = Store.showName(c), alwaysRat = Store.alwaysRat(c), showPlus = Store.showLtePlus(c);
            // From reading the baselines until saving them; released in finally if an exception comes first.
            Store.BASELINES.lock();
            // Marks that could not be saved earlier in this process must be on disk before any baseline is used.
            Store.saveUnsavedMarks(c);
            JSONObject backups = new JSONObject(prefs.getString("baselines", "{}"));
            if (restore) {
                profiles = new JSONArray();
                java.util.Iterator<String> keys = backups.keys();
                while (keys.hasNext()) {
                    JSONObject identity = backups.getJSONObject(keys.next()).getJSONObject("identity");
                    profiles.put(identity);
                }
            }
            List<Integer> ids = new ArrayList<>(); List<PersistableBundle> targets = new ArrayList<>();
            List<JSONObject> chosen = new ArrayList<>();
            List<String> names = new ArrayList<>(), modes = new ArrayList<>(), unknown = new ArrayList<>(), waiting = new ArrayList<>();
            int skipped = 0, untouched = 0, cleaned = 0;
            int boot = Store.bootCount(c); long elapsed = SystemClock.elapsedRealtime();
            // Without the boot count no baseline can be dated: writing back or re-recording could use this app's own override.
            if (boot < 0) throw new IllegalStateException(t("端末の起動回数を読み取れないため、変更前の値を確認できません。何も変更していません", "The boot count cannot be read, so the original values cannot be checked. Nothing was changed"));
            if (Store.quarantine(backups, boot, elapsed)) Store.saveMarks(c, backups.toString());
            JSONObject owners = Store.slotOwners(prefs.getString(Store.SLOT_OWNERS, null), boot);
            List<Integer> slots = new ArrayList<>();
            // Prepare and validate every target before the first write.
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject p = profiles.getJSONObject(i); SubscriptionInfo selected = null;
                for (SubscriptionInfo s : active) if (Store.matches(p, s)) { selected = s; break; }
                if (selected == null) { skipped++; report.append("SIM ID ").append(p.getInt("id")).append(t("：一致しないため適用せず\n", ": not applied (no matching SIM)\n")); continue; }
                PersistableBundle target; int id = selected.getSubscriptionId(); String sim = "SIM " + (selected.getSimSlotIndex() + 1);
                String key = Integer.toString(id); JSONObject saved = backups.optJSONObject(key);
                if (restore) {
                    if (saved == null || !sameSavedIdentity(saved, p)) throw new IllegalStateException(t("このSIMの変更前の値がありません", "No original values recorded for this SIM"));
                    NameConfig.Boot recorded = Store.recordedBoot(saved, boot, elapsed);
                    if (recorded == NameConfig.Boot.EARLIER) {
                        // Not changed since the reboot cleared the overrides; an old baseline would re-apply stale values.
                        untouched++;
                        report.append(sim).append(t("：再起動後は変更していないため、変更前の値のままです\n", ": not changed since the reboot, still at the original values\n"));
                        continue;
                    }
                    if (recorded == NameConfig.Boot.UNKNOWN) {
                        unknown.add(sim);
                        report.append(sim).append(t("：変更前の値がいつ記録されたか確認できないため、戻していません。再起動すると、このアプリの変更は消えます\n", ": not restored, because it is unknown when the original values were recorded. A reboot clears this app's changes\n"));
                        continue;
                    }
                    target = Store.bundle(saved.getJSONObject("values"));
                    modes.add(t("変更前の値に戻しました", "restored to the original values"));
                } else {
                    PersistableBundle current = cm.getConfigForSubId(id);
                    if (current == null) throw new IllegalStateException(t("SIM設定の読み取りに失敗しました", "Could not read the SIM settings"));
                    // Record the pre-change values first: an "off" name override is built from them.
                    String[] managed = Store.managedKeys(p);
                    NameConfig.Boot recorded = saved == null || !sameSavedIdentity(saved, p) ? null : Store.recordedBoot(saved, boot, elapsed);
                    // Another line's override from this boot may still be on this slot (a SIM switched outside this
                    // app). Known from the slot record; without one (written by an earlier version in this boot), from
                    // that line's pre-change record. Writing that line's pre-change values back cannot remove it when
                    // they hold a name override of the carrier's own (povo's "povo"), and they would also be read as
                    // this SIM's pre-change values. So this slot's temporary overrides are cleared (decision of
                    // 2026-11-01, plan X): another tool's temporary overrides on this slot go too, and the report says
                    // so; overrides kept across reboots are not touched. Done before this SIM's own values are read.
                    int slot = selected.getSimSlotIndex(), owner = Store.slotOwner(owners, slot);
                    if (Store.leftOnSlot(owner, id, recorded == NameConfig.Boot.THIS,
                            owner < 0 && Store.leftover(backups, selected, active, boot, elapsed) != null)) {
                        JSONObject was = Store.snapshot(current, managed);
                        HiddenBridge.override(cm, id, null);
                        // Nothing to compare with: the carrier's own values may equal the old ones. Waits for the
                        // config to change, at most 3 s.
                        boolean settled = false;
                        for (int attempt = 0; attempt < 6 && !settled; attempt++) {
                            Thread.sleep(500);
                            PersistableBundle now = cm.getConfigForSubId(id);
                            settled = now != null && !Store.snapshot(now, managed).toString().equals(was.toString());
                        }
                        Store.setSlotOwner(owners, slot, Store.ownerAfterClear(settled, id));
                        cleaned++;
                        if (!settled) {
                            // The values read now may still be the other line's: recorded as this SIM's pre-change
                            // values, they would be written back by every later apply and restore.
                            waiting.add(sim);
                            report.append(sim).append(t("：前の回線の表示設定が残っていたため、この差し口の一時的な設定をすべて消しましたが（ほかのアプリの一時的な設定も消えます）、消し終わったのを確かめられなかったので、このSIMはまだ変更していません。少し待ってからもう一度適用してください\n",
                                    ": the previous line's display settings were still there, so all temporary settings on this slot were cleared (other apps' temporary settings too), but the clearing could not be confirmed, so this SIM was not changed yet. Wait a moment, then apply again\n"));
                            continue;
                        }
                        current = cm.getConfigForSubId(id);
                        if (current == null) throw new IllegalStateException(t("SIM設定の読み取りに失敗しました", "Could not read the SIM settings"));
                        // Android keeps the name it showed until the radio restarts (seen on the Pixel 10a; airplane mode clears it).
                        report.append(sim).append(t("：前の回線の表示設定が残っていたため、この差し口の一時的な設定をすべて消しました（ほかのアプリの一時的な設定も消えます。前の名前が残るときは、機内モードをオンにしてからオフにしてください）\n",
                                ": the previous line's display settings were still there, so all temporary settings on this slot were cleared (other apps' temporary settings too; if the old name stays, turn airplane mode on and off)\n"));
                    }
                    // Nothing asked for and nothing changed by this app in this boot: leave the SIM alone.
                    if (!p.getBoolean("rename") && !showName && !alwaysRat && !showPlus && recorded != NameConfig.Boot.THIS) {
                        untouched++;
                        continue;
                    }
                    if (recorded == NameConfig.Boot.UNKNOWN) {
                        // The current values may be this app's override from this boot: keep the record, write nothing.
                        unknown.add(sim);
                        continue;
                    }
                    if (recorded != NameConfig.Boot.THIS) {
                        saved = new JSONObject(); saved.put("identity", p); saved.put("boot", boot); saved.put("elapsed", elapsed);
                        saved.put("values", Store.snapshot(current, managed));
                        backups.put(key, saved);
                    } else {
                        // Keys this app has never written still hold their pre-change values.
                        JSONObject values = saved.getJSONObject("values");
                        List<String> missing = new ArrayList<>();
                        for (String k : managed) if (!values.has(k)) missing.add(k);
                        JSONObject extra = Store.snapshot(current, missing.toArray(new String[0]));
                        for (String k : missing) values.put(k, extra.get(k));
                    }
                    target = Store.desired(p.getBoolean("rename"), p.getString("label"), showName, alwaysRat, showPlus, saved.getJSONObject("values"));
                    // Names put on this line, kept after the name is turned off: one of them can stay shown. Added before
                    // the write, so a write that does not take effect leaves the earlier name on the list too.
                    if (p.getBoolean("rename")) NameFilters.addApplied(saved, p.getString("label"));
                    modes.add(p.getBoolean("rename") ? t("表示名を「", "name set to \"") + p.getString("label") + t("」にしました", "\"") : t("表示名は変更前の値", "original name"));
                }
                ids.add(id); targets.add(target); names.add(sim); chosen.add(p); slots.add(selected.getSimSlotIndex());
            }
            if (!restore && !unknown.isEmpty())
                throw new IllegalStateException(String.join(t("、", ", "), unknown) + t("：変更前の値がいつ記録されたか確認できません", ": it is unknown when the original values were recorded")
                        + t("（以前の版で記録した値か、起動回数を確かめられない記録です）。何も変更していません。端末を再起動してから適用してください", " (recorded by an earlier version, or without a boot count). Nothing was changed. Reboot the device, then apply"));
            if (ids.isEmpty() && untouched == 0 && unknown.isEmpty() && waiting.isEmpty()) throw new IllegalStateException(t("保存したSIMと一致する有効なSIMがありません", "No active SIM matches the saved settings"));
            if (!restore) {
                // The slots cleared above, with the baselines read after the clearing.
                SharedPreferences.Editor edit = prefs.edit().putString("baselines", backups.toString())
                        .putString(Store.SLOT_OWNERS, Store.slotOwnersValue(owners, boot));
                if (!prefs.contains("secureCaptured")) {
                    edit.putBoolean("secureCaptured", true);
                    String original = Settings.Secure.getString(c.getContentResolver(), "show_operator_name");
                    edit.putString("secureOriginal", original);
                }
                if (!edit.commit()) throw new IllegalStateException(t("変更前の値を保存できませんでした", "Could not save the original values"));
            }
            Store.BASELINES.unlock();
            for (int i = 0; i < ids.size(); i++) {
                HiddenBridge.override(cm, ids.get(i), targets.get(i));
                boolean verified = false;
                for (int attempt = 0; attempt < 12; attempt++) {
                    Thread.sleep(500);
                    if (Store.contains(cm.getConfigForSubId(ids.get(i)), targets.get(i))) { verified = true; break; }
                }
                // Verified by reading the value back; the line says only what happened to the name.
                report.append(names.get(i)).append(verified ? t("：", ": ") + modes.get(i) + "\n"
                        : t("：設定できたか確認できませんでした\n", ": could not confirm the change\n"));
                if (!verified) throw new IllegalStateException(t("設定の反映待ち、またはOSが設定を拒否しました", "The change is still pending, or the OS rejected it"));
                // On disk right after each write: a run ending halfway still leaves the slot's line known.
                Store.setSlotOwner(owners, slots.get(i), ids.get(i));
                prefs.edit().putString(Store.SLOT_OWNERS, Store.slotOwnersValue(owners, boot)).commit();
            }
            java.util.Set<Integer> inUse = new java.util.HashSet<>();
            for (SubscriptionInfo s : active) inUse.add(s.getSubscriptionId());
            for (int i = 0; i < ids.size(); i++) {
                String note = restore ? null : replaceStaleName(c, cm, ids.get(i), chosen.get(i), targets.get(i), inUse);
                String shown = refreshShownName(c, cm, ids.get(i), chosen.get(i), inUse);
                if (note != null) report.append(names.get(i)).append(note).append('\n');
                if (shown != null) report.append(names.get(i)).append(shown).append('\n');
            }
            if (!restore) report.append(t("すべてのSIM：", "All SIMs: "))
                    .append(showName ? t("キャリア名を表示", "carrier name shown") : t("キャリア名の表示は変更前の値", "carrier name display as it was"))
                    .append(t("、", ", "))
                    .append(alwaysRat ? t("Wi-Fi中も4G／5Gを表示", "4G/5G shown on Wi-Fi") : t("Wi-Fi中の4G／5Gは変更前の値", "4G/5G on Wi-Fi as it was"))
                    .append(t("、", ", "))
                    .append(showPlus ? t("4G+を表示", "4G+ shown") : t("4G+の表示は変更前の値", "4G+ as it was"))
                    .append('\n');
            String wantedSecure = restore || !showName ? prefs.getString("secureOriginal", null) : "1";
            if (restore && (skipped > 0 || !unknown.isEmpty())) {
                report.append(skipped > 0 ? t("使えないSIMがあるため", "A SIM is unavailable") : t("戻していないSIMがあるため", "A SIM was not restored"))
                        .append(t("、両方のSIMに共通の設定は変えていません\n", ", so the setting shared by both SIMs was not changed\n"));
            } else {
                if (!Settings.Secure.putString(c.getContentResolver(), "show_operator_name", wantedSecure))
                    throw new IllegalStateException(t("共通の表示スイッチを変更できませんでした", "Could not change the shared display switch"));
                if (!java.util.Objects.equals(wantedSecure, Settings.Secure.getString(c.getContentResolver(), "show_operator_name")))
                    throw new IllegalStateException(t("共通の表示スイッチの確認に失敗しました", "Could not confirm the shared display switch"));
            }
            // A SIM left as it was because its baseline cannot be dated is not restored.
            // A SIM whose slot clearing was not seen to take effect is not changed yet.
            success = skipped == 0 && unknown.isEmpty() && waiting.isEmpty();
            done = !restore ? (ids.isEmpty() && cleaned == 0 ? t("変更するものはありません。", "Nothing to change.") : t("適用しました。", "Applied.")) : ids.isEmpty() ? t("変更前の値に戻っています。", "Already at the original values.") : t("変更前の値に戻しました。", "Restored to the original values.");
            if (!unknown.isEmpty()) report.append(t("\n戻していないSIMがあるため、完了していません。", "\nNot finished: a SIM was not restored."));
            if (!waiting.isEmpty()) report.append(t("\nまだ変更していないSIMがあるため、完了していません。", "\nNot finished: a SIM was not changed yet."));
        } catch (Throwable e) {
            report.append(t("\n完了していません：", "\nNot finished: ")).append(HiddenBridge.error(e));
        } finally {
            if (Store.BASELINES.isHeldByCurrentThread()) Store.BASELINES.unlock();
            if (delegated) try {
                // Releasing the borrowed permissions is checked; only a failure is worth reporting.
                HiddenBridge.release(c);
            } catch (Throwable e) {
                success = false;
                report.append(t("\n一時的に借りた権限を戻せたか確認できませんでした：", "\nCould not confirm that the borrowed permissions were returned: ")).append(HiddenBridge.error(e));
            }
            if (success) report.append(done);
            Store.report(c, report.toString());
            prefs.edit().putString("finished", token).putBoolean("lastSuccess", success).commit();
            finish(success ? 0 : 1, new Bundle());
        }
    }
    /**
     * Android keeps the SIM name it last set for a slot until the radio restarts, even after the override is gone:
     * on the Pixel 10a docomo showed LINEMO's custom name "SoftBank" after an eSIM moved to LINEMO's port. When this
     * SIM shows the custom name of another saved line that can have been left on the slot (see
     * {@link Store#staleName}), the name is put through once as the network's own name (an
     * override Android does apply), then the wanted values are written again. If Android brings the old name back,
     * the network's name stays as an override until the next reboot.
     * @return the report text after the SIM label, or null when nothing was stale
     */
    @android.annotation.SuppressLint("MissingPermission") // carrier config reads run with the delegated shell permissions
    private static String replaceStaleName(Context c, CarrierConfigManager cm, int id, JSONObject p, PersistableBundle target,
                                           java.util.Set<Integer> inUse) throws Exception {
        TelephonyManager tm = c.getSystemService(TelephonyManager.class).createForSubscriptionId(id);
        String stale = staleName(c, id, p, tm.getSimOperatorName(), inUse);
        if (stale == null) return null;
        String network = tm.getNetworkOperatorName();
        if (network == null || network.isEmpty()) {
            SubscriptionInfo info = c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfo(id);
            network = info == null || info.getDisplayName() == null ? "" : info.getDisplayName().toString();
        }
        if (network.isEmpty() || network.equals(stale)) return null;
        PersistableBundle own = new PersistableBundle();
        own.putBoolean(Store.OVERRIDE, true);
        own.putString(Store.NAME, network);
        // Each write is read back from the config (a failure); whether the shown name follows is only reported.
        write(cm, id, own);
        for (int i = 0; i < 12 && !network.equals(tm.getSimOperatorName()); i++) Thread.sleep(500);
        write(cm, id, target);
        Thread.sleep(1500);
        if (staleName(c, id, p, tm.getSimOperatorName(), inUse) == null)
            return t("：前の回線の名前「", ": the previous line's name \"") + stale + t("」が残っていたので外しました", "\" was still shown, removed");
        write(cm, id, own);
        return t("：前の回線の名前「", ": the previous line's name \"") + stale + t("」が消えないため、「", "\" stays, so \"") + network
                + t("」で上書きしています（再起動で外れます）", "\" is set instead until the next reboot");
    }

    /**
     * Android works out the name in the status bar again only when the network state changes, not when the carrier
     * config does (seen on the Pixel 10a after a reboot: the override was in place, the status bar kept the SIM's own
     * name until airplane mode). The network name filter is a carrier-config value that changes the network state,
     * so it is set for a moment to cut the last letter, then to one that keeps the name as it is, then back to the
     * carrier's own value. Android keeps the last non-empty filter until the radio restarts; the one kept changes nothing.
     * The status bar shows the custom name throughout, as the carrier-name override comes before the network name.
     * The wanted name is the one the carrier-name override holds now (a custom name, or the network's name put there by
     * {@link #replaceStaleName}); without one, the names this app put on this line count as old ({@link NameFilters}),
     * and another line's custom name can have been left on the slot ({@link NameFilters#leftOver}): it is worked out
     * again once; if it stays, that is reported as not known, as it may also be the SIM's own name.
     * @return the report text after the SIM label, or null when the status bar already shows the right name
     */
    // Subscription reads run with the delegated shell permissions; the mark must be on disk before the filter changes.
    @android.annotation.SuppressLint({"MissingPermission", "ApplySharedPref"})
    private static String refreshShownName(Context c, CarrierConfigManager cm, int id, JSONObject p,
                                           java.util.Set<Integer> inUse) throws Exception {
        SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
        TelephonyManager tm = c.getSystemService(TelephonyManager.class).createForSubscriptionId(id);
        PersistableBundle current = cm.getConfigForSubId(id);
        if (current == null) return null;
        String wanted = NameFilters.wanted(p.optBoolean("rename", false), current.getBoolean(Store.OVERRIDE, false),
                current.getString(Store.NAME, ""));
        List<String> applied = wanted == null ? NameFilters.applied(Store.baselines(c).optJSONObject(Integer.toString(id))) : new ArrayList<>();
        // Another line's custom name may have been left on the slot: replaceStaleName watches the SIM's name, which can
        // be right again while the carrier name still holds the old one (seen on the Pixel 10a: IIJmio's "au" stayed on
        // docomo after the physical SIM was swapped for a docomo eSIM). Only a candidate, kept apart from the names put
        // on this line: it can also be this SIM's own carrier name.
        String left = wanted == null ? NameFilters.leftOver(staleName(c, id, p, shownName(sm, id), inUse), tm.getSimOperatorName()) : null;
        java.util.function.Predicate<String> right = shown -> NameFilters.shownRight(shown, wanted, applied, left, tm.getNetworkOperatorName());
        // The SIM could not be read: nothing is known, so nothing is changed or forgotten.
        if (shownName(sm, id).isEmpty()) return null;
        if (right.test(shownName(sm, id))) { settleApplied(c, id, wanted); return null; }
        SharedPreferences prefs = Store.prefs(c);
        JSONObject marks;
        try { marks = NameFilters.parse(prefs.getString(LEFT_FILTERS, "{}")); }
        catch (org.json.JSONException e) {
            return t("：設定は適用済みです。網名の一時的な変更の記録が読めないため、ステータスバーの名前の更新だけ見送りました（再起動の後に再開します）",
                    ": the settings are applied. The record of network name filter changes cannot be read, so only the status bar name was not updated (it resumes after a reboot)");
        }
        // A filter not put back yet: the config may hold the shortening one, which must not be taken as the original.
        if (NameFilters.marked(marks, id))
            return t("：設定は適用済みです。前回一時的に変えた網名の設定がまだ戻っていないため、ステータスバーの名前の更新だけ見送りました（戻せたとき、または再起動の後に再開します）",
                    ": the settings are applied. The network name filter changed last time is not back yet, so only the status bar name was not updated (it resumes once that is back, or after a reboot)");
        String original = current.getString(NAME_FILTER, "");
        // On disk first, dated by boot: if this run ends halfway, the next one puts the filter back (settleLeftFilters).
        // The record reads, so an old "first seen unreadable" boot is dropped in the same save.
        if (!NameFilters.mark(marks, id, original, Store.bootCount(c))
                || !prefs.edit().putString(LEFT_FILTERS, marks.toString()).remove(LEFT_FILTERS_SEEN).commit())
            return t("：設定は適用済みです。網名の一時的な変更を記録できないため、ステータスバーの名前の更新だけ見送りました",
                    ": the settings are applied. The network name filter change could not be recorded, so only the status bar name was not updated");
        PersistableBundle filter = new PersistableBundle();
        boolean settled;
        try {
            filter.putString(NAME_FILTER, "^(.+).$");
            write(cm, id, filter);
            for (int i = 0; i < 12 && !right.test(shownName(sm, id)); i++) Thread.sleep(500);
        } catch (IllegalStateException e) {
            // A write that cannot be read back only leaves the name as it was; the settings are already in place.
        } finally {
            // Also after an interruption: the shortening filter must not stay.
            settled = settleFilter(cm, id, original);
        }
        if (!settled)
            return t("：設定は適用済みです。網名を一時的に変えた設定を元に戻せたか確認できませんでした。次の適用で戻すか、機内モードのオン・オフか再起動で戻ります",
                    ": the settings are applied. Could not confirm that the network name filter was put back. The next apply puts it back, or turn airplane mode on and off, or reboot");
        NameFilters.unmark(marks, id);
        // A mark left on disk only makes the next apply put the same value back once more.
        prefs.edit().putString(LEFT_FILTERS, marks.toString()).commit();
        for (int i = 0; i < 6 && !right.test(shownName(sm, id)); i++) Thread.sleep(500);
        // Still the candidate: it may be this SIM's own name, or one Android has not worked out again yet (or the filter
        // never took). Neither is known, so nothing is settled and no airplane mode is asked for.
        String still = shownName(sm, id);
        if (!right.test(still) && NameFilters.shownRight(still, wanted, applied, tm.getNetworkOperatorName()))
            return t("：キャリア名の作り直しを試しましたが、前の回線の名前「", ": tried to have the carrier name worked out again, but whether the previous line's name \"")
                    + still + t("」が残っているのか、このSIM本来の名前なのかは確かめられませんでした", "\" is left over or is this SIM's own name could not be told");
        if (!right.test(shownName(sm, id)))
            return t("：ステータスバーの名前が変わらないときは、機内モードをオンにしてからオフにしてください", ": if the name in the status bar stays, turn airplane mode on and off");
        settleApplied(c, id, wanted);
        return t("：ステータスバーの名前を更新しました", ": updated the name in the status bar");
    }

    /** CarrierConfigManager.KEY_OPERATOR_NAME_FILTER_PATTERN_STRING, hidden from the SDK. */
    private static final String NAME_FILTER = "operator_name_filter_pattern_string";
    /** Prefs key: the network name filter changes under way ({@link NameFilters#mark}). */
    private static final String LEFT_FILTERS = "nameFilters";
    /** Prefs key: the boot in which {@link #LEFT_FILTERS} was first found unreadable. */
    private static final String LEFT_FILTERS_SEEN = "nameFiltersSeen";

    /**
     * Puts the filter back: through one that keeps the name as it is when the carrier's own value is empty, as
     * Android ignores an empty filter and would keep the shortening one. Each write is read back.
     * @return whether every write was confirmed
     */
    private static boolean settleFilter(CarrierConfigManager cm, int id, String original) {
        PersistableBundle filter = new PersistableBundle();
        try {
            if (original.isEmpty()) {
                filter.putString(NAME_FILTER, "^(.*)$");
                write(cm, id, filter);
            }
            filter.putString(NAME_FILTER, original);
            write(cm, id, filter);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Settles the filters a run that ended halfway left changed in this boot, for the lines in use. A line not in use
     * keeps its mark until it is back or the device reboots; Android keeps the filter for the slot, so another line
     * there may show the short network name (not the status bar name) until airplane mode or a reboot.
     */
    @android.annotation.SuppressLint({"MissingPermission", "ApplySharedPref"})
    private static void settleLeftFilters(Context c, CarrierConfigManager cm, StringBuilder report) {
        SharedPreferences prefs = Store.prefs(c);
        String stored = prefs.getString(LEFT_FILTERS, "{}");
        int boot = Store.bootCount(c);
        JSONObject marks;
        List<NameFilters.Mark> left;
        try {
            marks = NameFilters.parse(stored);
            left = NameFilters.toSettle(marks, boot);
        } catch (org.json.JSONException e) {
            // Nothing in it can be written back. It blocks status bar name updates until a reboot is seen, which
            // cleared any filter it was about; the boot it was first seen in is kept beside it.
            switch (NameFilters.broken(prefs.getInt(LEFT_FILTERS_SEEN, -1), boot)) {
                case RESET:
                    prefs.edit().putString(LEFT_FILTERS, "{}").remove(LEFT_FILTERS_SEEN).commit();
                    return;
                case DATE:
                    if (!prefs.edit().putInt(LEFT_FILTERS_SEEN, boot).commit()) {
                        report.append(t("網名の一時的な変更の記録が読めず、読めないことも記録できませんでした。ステータスバーの名前の更新は行いません。網名は機内モードのオン・オフか再起動で戻ります\n",
                                "The record of network name filter changes cannot be read, and that could not be recorded; status bar names are not updated. Airplane mode or a reboot puts the network name back\n"));
                        return;
                    }
                    break;
                default:
                    break;
            }
            report.append(t("網名の一時的な変更の記録が読めないため、ステータスバーの名前の更新は再起動の後まで行いません。網名は機内モードのオン・オフか再起動で戻ります\n",
                    "The record of network name filter changes cannot be read, so status bar names are not updated until a reboot. Airplane mode or a reboot puts the network name back\n"));
            return;
        }
        // The record reads again: an old "first seen unreadable" boot must not date a later breakage.
        if (prefs.contains(LEFT_FILTERS_SEEN)) prefs.edit().remove(LEFT_FILTERS_SEEN).commit();
        SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
        for (NameFilters.Mark m : left) {
            if (sm.getActiveSubscriptionInfo(m.id) == null) continue;
            if (settleFilter(cm, m.id, m.original)) NameFilters.unmark(marks, m.id);
            else report.append("SIM ID ").append(m.id).append(t("：前回変えた網名の設定を元に戻せたか確認できませんでした\n", ": could not confirm that the network name filter changed last time was put back\n"));
        }
        if (!marks.toString().equals(stored)) prefs.edit().putString(LEFT_FILTERS, marks.toString()).commit();
    }

    /**
     * The status bar shows the right name: with a custom name shown, only it can be left shown later; without one,
     * none of this app's names is shown any more. A failed save only keeps more names to check next time.
     */
    @android.annotation.SuppressLint("ApplySharedPref")
    private static void settleApplied(Context c, int id, String wanted) {
        Store.BASELINES.lock();
        try {
            JSONObject backups = Store.baselines(c);
            JSONObject entry = backups.optJSONObject(Integer.toString(id));
            if (entry == null) return;
            boolean changed;
            if (wanted != null) changed = NameFilters.confirmApplied(entry, wanted);
            else changed = entry.remove(NameFilters.APPLIED) != null;
            if (changed) Store.prefs(c).edit().putString("baselines", backups.toString()).commit();
        } catch (org.json.JSONException ignored) {
        } finally {
            Store.BASELINES.unlock();
        }
    }

    /** The name in the status bar, which Android keeps as the subscription's carrier name. */
    @android.annotation.SuppressLint("MissingPermission")
    private static String shownName(SubscriptionManager sm, int id) {
        SubscriptionInfo info = sm.getActiveSubscriptionInfo(id);
        return info == null || info.getCarrierName() == null ? "" : info.getCarrierName().toString();
    }

    /** Writes one override and reads it back from the config; throws when it is not there within 6 s. */
    private static void write(CarrierConfigManager cm, int id, PersistableBundle values) throws Exception {
        HiddenBridge.override(cm, id, values);
        for (int i = 0; i < 12; i++) {
            Thread.sleep(500);
            if (Store.contains(cm.getConfigForSubId(id), values)) return;
        }
        throw new IllegalStateException(t("名前を変える途中で、設定できたか確認できませんでした", "Could not confirm a change while updating the name"));
    }

    private static String staleName(Context c, int id, JSONObject p, String shown, java.util.Set<Integer> inUse) {
        return Store.staleName(Store.profiles(c), Store.baselines(c), inUse, Store.bootCount(c), SystemClock.elapsedRealtime(), id, p, shown);
    }

    private boolean sameSavedIdentity(JSONObject saved, JSONObject profile) throws Exception {
        JSONObject p = saved.getJSONObject("identity");
        return Identity.matches(p.getInt("id"), p.getInt("carrier"), p.getString("mcc"), p.getString("mnc"),
                profile.getInt("id"), profile.getInt("carrier"), profile.getString("mcc"), profile.getString("mnc"));
    }
}
