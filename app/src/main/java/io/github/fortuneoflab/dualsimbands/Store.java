package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The SIM label state in SharedPreferences "profiles". Keys are unchanged since 0.1.x:
 * profiles, baselines, auto, secureOriginal, secureCaptured, report, pending, finished, lastSuccess.
 * New behaviour only adds to them; nothing is migrated or deleted implicitly. Added in 1.0.0: showName and
 * alwaysRat, the two whole-device switches; until first saved they are derived from the profiles. Later added:
 * showLtePlus (off until saved).
 */
final class Store {
    static final String SHOW = "show_operator_name_in_statusbar_bool";
    static final String NAME = NameConfig.NAME;
    static final String OVERRIDE = NameConfig.OVERRIDE;
    static final String RESOLVER = NameConfig.RESOLVER;
    static final String CONDITION = NameConfig.CONDITION;
    static final String RAT = "always_show_data_rat_icon_bool";
    /** The status bar shows LTE carrier aggregation as plain 4G while this is true (Android's default). */
    static final String HIDE_PLUS = "hide_lte_plus_data_icon_bool";
    static final String[] KEYS = {SHOW, NAME, OVERRIDE, RESOLVER, CONDITION, RAT, HIDE_PLUS};
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("profiles", Context.MODE_PRIVATE); }

    /** Whole-device switches (prefs keys). The status bar follows the data SIM, so each is applied to every active SIM. */
    static final String SHOW_NAME = "showName", ALWAYS_RAT = "alwaysRat", SHOW_LTE_PLUS = "showLtePlus";

    /** Show 4G+ in the status bar for LTE carrier aggregation. */
    static boolean showLtePlus(Context c) { return prefs(c).getBoolean(SHOW_LTE_PLUS, false); }

    /** Before the switch existed, every saved SIM had its name shown in the status bar. */
    static boolean showName(Context c) {
        SharedPreferences p = prefs(c);
        return p.contains(SHOW_NAME) ? p.getBoolean(SHOW_NAME, false) : profiles(c).length() > 0;
    }

    /** Before the switch existed, this was set per SIM: on if any SIM had it. */
    static boolean alwaysRat(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains(ALWAYS_RAT)) return p.getBoolean(ALWAYS_RAT, false);
        JSONArray list = profiles(c);
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && o.optBoolean("alwaysRat", false)) return true;
        }
        return false;
    }

    /**
     * Another line's pre-change record from this boot for the slot this SIM is in, when that line is no longer
     * active. Carrier-config overrides stay with the slot, so its override is still there (seen on the Pixel 10a
     * after switching eSIMs in Android's settings). The latest one if several; null when none.
     */
    static JSONObject leftover(JSONObject backups, SubscriptionInfo s, List<SubscriptionInfo> active, int boot, long elapsed) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (SubscriptionInfo a : active) ids.add(a.getSubscriptionId());
        JSONObject found = null;
        for (Iterator<String> it = backups.keys(); it.hasNext(); ) {
            JSONObject entry = backups.optJSONObject(it.next());
            JSONObject identity = entry == null ? null : entry.optJSONObject("identity");
            if (identity == null) continue;
            int id = identity.optInt("id", -1);
            if (id == s.getSubscriptionId() || ids.contains(id) || identity.optInt("slot", -2) != s.getSimSlotIndex()) continue;
            if (recordedBoot(entry, boot, elapsed) != NameConfig.Boot.THIS) continue;
            if (found == null || entry.optLong("elapsed", -1) > found.optLong("elapsed", -1)) found = entry;
        }
        return found;
    }

    /**
     * Prefs key: {"boot": boot count, "slots": {"0": {"id": subId}, ...}}, per slot the line this app last wrote an
     * override for in this boot. Android keeps an override with the slot, not the line (seen on the Pixel 10a: povo's
     * own name override stayed on the slot and LINEMO, put in that slot, was recorded and shown as "povo"), so another
     * line found in a slot means that slot still holds this app's override for the earlier line.
     */
    static final String SLOT_OWNERS = "slotOwners";

    /** The slots recorded in this boot; empty for an earlier boot, an unknown boot or an unreadable record. */
    static JSONObject slotOwners(String stored, int boot) {
        if (stored == null || boot < 0) return new JSONObject();
        try {
            JSONObject o = new JSONObject(stored);
            JSONObject slots = o.optJSONObject("slots");
            return o.optInt("boot", -1) == boot && slots != null ? slots : new JSONObject();
        } catch (JSONException e) { return new JSONObject(); }
    }

    static String slotOwnersValue(JSONObject slots, int boot) throws JSONException {
        return new JSONObject().put("boot", boot).put("slots", slots).toString();
    }

    /** The line this app last wrote for in this slot, or -1 when none is recorded. */
    static int slotOwner(JSONObject slots, int slot) {
        JSONObject o = slots.optJSONObject(Integer.toString(slot));
        return o == null ? -1 : o.optInt("id", -1);
    }

    static void setSlotOwner(JSONObject slots, int slot, int subId) throws JSONException {
        if (subId < 0) slots.remove(Integer.toString(slot));
        else slots.put(Integer.toString(slot), new JSONObject().put("id", subId));
    }

    /**
     * Whether another line's override can be on the slot of line {@code id}: from the slot record when there is one;
     * without one, from another line's pre-change record of this boot ({@link #leftover}), unless this line has its own.
     */
    static boolean leftOnSlot(int owner, int id, boolean recordedThisBoot, boolean leftoverFound) {
        return owner >= 0 ? owner != id : !recordedThisBoot && leftoverFound;
    }

    /**
     * The slot record after this app cleared the slot for line {@code id}. Seen to take effect: none, until this app
     * writes there. Not seen within the wait: {@code id}, so the next apply does not clear again (the old values can
     * equal the carrier's own) and records the values read then as the pre-change ones; nothing is recorded now, as
     * the values read may still be the other line's.
     */
    static int ownerAfterClear(boolean settled, int id) {
        return settled ? -1 : id;
    }

    /** Whether this app has changed any SIM during this boot (so an apply may have something to put back). */
    static boolean changedThisBoot(Context c) {
        JSONObject backups = baselines(c);
        int boot = bootCount(c); long elapsed = SystemClock.elapsedRealtime();
        for (Iterator<String> it = backups.keys(); it.hasNext(); ) {
            JSONObject entry = backups.optJSONObject(it.next());
            if (entry != null && recordedBoot(entry, boot, elapsed) == NameConfig.Boot.THIS) return true;
        }
        return false;
    }

    /**
     * The custom name of another saved line shown for SIM {@code id}, when it can have been left on this slot: that
     * line was renamed by this app during this boot (its baseline is from this boot) and is not in use now. A matching
     * name alone is no evidence: another line's custom name can equal this SIM's own name. Null otherwise.
     */
    static String staleName(JSONArray saved, JSONObject backups, java.util.Set<Integer> inUse, int boot, long elapsed,
                            int id, JSONObject p, String shown) {
        if (shown == null || shown.isEmpty()) return null;
        if (p.optBoolean("rename", false) && shown.equals(p.optString("label", ""))) return null;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject o = saved.optJSONObject(i);
            if (o == null || !o.optBoolean("rename", false) || !shown.equals(o.optString("label", ""))) continue;
            int other = o.optInt("id", -1);
            if (other == id || inUse.contains(other)) continue;
            JSONObject entry = backups.optJSONObject(Integer.toString(other));
            if (entry != null && recordedBoot(entry, boot, elapsed) == NameConfig.Boot.THIS) return shown;
        }
        return null;
    }

    /** Whether there is anything to apply: a switch on, or a SIM with a custom name. */
    static boolean anythingToApply(Context c) {
        if (showName(c) || alwaysRat(c) || showLtePlus(c)) return true;
        JSONArray list = profiles(c);
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && o.optBoolean("rename", false)) return true;
        }
        return false;
    }

    /**
     * One entry per active SIM for an apply: its identity, with the saved custom name if it has one. Every key
     * (RAT included) is managed, because the switches reach every active SIM.
     */
    static JSONArray targets(Context c, List<SubscriptionInfo> active) throws JSONException {
        JSONArray saved = profiles(c), out = new JSONArray();
        for (SubscriptionInfo s : active) {
            String label = ""; boolean rename = false;
            for (int i = 0; i < saved.length(); i++) {
                JSONObject p = saved.getJSONObject(i);
                if (matches(p, s) && p.optBoolean("rename", false)) { label = p.optString("label", ""); rename = true; }
            }
            out.put(profile(s, label, rename, true));
        }
        return out;
    }
    static JSONArray profiles(Context c) {
        try { return new JSONArray(prefs(c).getString("profiles", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }
    static JSONObject baselines(Context c) {
        try { return new JSONObject(prefs(c).getString("baselines", "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }
    /** Settings.Global.BOOT_COUNT, or -1 when unavailable (no baseline can then be dated). */
    static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** When this baseline entry was recorded; see {@link NameConfig#boot}. */
    static NameConfig.Boot recordedBoot(JSONObject entry, int boot, long elapsed) {
        return NameConfig.boot(entry.optInt("boot", -1), entry.optLong("elapsed", -1), entry.optInt("seenBoot", -1), boot, elapsed);
    }

    /**
     * Marks every baseline that cannot be dated in this boot with the current boot ("seenBoot"), so it stays
     * unused until a later boot shows that the reboot has cleared any override made with it.
     * @return whether an entry changed
     */
    static boolean quarantine(JSONObject backups, int boot, long elapsed) throws JSONException {
        boolean changed = false;
        for (Iterator<String> it = backups.keys(); it.hasNext(); ) {
            JSONObject entry = backups.optJSONObject(it.next());
            if (entry != null && NameConfig.mark(recordedBoot(entry, boot, elapsed), entry.optInt("seenBoot", -1), boot)) {
                entry.put("seenBoot", boot); changed = true;
            }
        }
        return changed;
    }

    /**
     * Held from reading "baselines" until writing them back, by an apply and by the marking after an update, so
     * neither saves a copy older than the other's write (e.g. the marks dropping a baseline an apply just recorded).
     */
    static final ReentrantLock BASELINES = new ReentrantLock();

    /** Baselines with marks that could not be saved; null when none. Applies run in this process (no restart). */
    private static String unsavedMarks;

    /**
     * Saves marked baselines, or keeps them in this process and throws. Every apply and restore first calls
     * {@link #saveUnsavedMarks}, so none of them can use a baseline that was found UNKNOWN but whose mark was lost
     * from the preferences, whichever entry point scheduled it. Once this process ends the kept marks are gone,
     * and a reused boot count found earlier is no longer caught after the uptime passes the recorded one.
     */
    static void saveMarks(Context c, String baselines) { saveMarks(prefs(c), baselines); }

    /** The caller holds {@link #BASELINES} if it read the baselines it passes here. */
    static void saveMarks(SharedPreferences prefs, String baselines) {
        BASELINES.lock();
        try {
            save(prefs, "baselines", baselines);
            unsavedMarks = null;
        } catch (IllegalStateException e) {
            unsavedMarks = baselines;
            throw new IllegalStateException(t("変更前の値の記録を保存できませんでした。保存できるまで表示名は変更しません。端末の空き容量を確認してください", "Could not save the record of original values. Carrier names will not be changed until it can be saved. Check the free storage"));
        } finally { BASELINES.unlock(); }
    }

    /** Retries marks kept by {@link #saveMarks}; throws while they still cannot be saved. */
    static void saveUnsavedMarks(Context c) { saveUnsavedMarks(prefs(c)); }

    static void saveUnsavedMarks(SharedPreferences prefs) {
        BASELINES.lock();
        try {
            if (unsavedMarks != null) saveMarks(prefs, unsavedMarks);
        } finally { BASELINES.unlock(); }
    }

    /** How long the marking after an update waits for a running apply before giving up. */
    static final long MARK_WAIT_MS = 10_000;

    /**
     * Called after an update, off the main thread: baselines of the old version that cannot be dated are marked
     * until the next reboot. If the marks cannot be saved, the user is told; see {@link #saveMarks} for what then
     * stops. If an apply holds the baselines too long, nothing is marked here: the next apply marks them itself.
     * @return whether such a baseline exists in this boot, or it could not be checked (no auto-restore then)
     */
    static boolean quarantineBaselines(Context c) {
        try {
            return quarantineSaved(prefs(c), () -> bootCount(c), SystemClock::elapsedRealtime, MARK_WAIT_MS);
        } catch (IllegalStateException e) {
            Notices.show(c, t("変更前の値の記録を保存できませんでした", "Could not save the record of original values"), t("端末の空き容量を確認してください。保存できるまで表示名は変更しません。", "Check the free storage. Carrier names will not be changed until it can be saved."));
            return true;
        } catch (TimeoutException | InterruptedException e) {
            return true;
        } catch (JSONException e) { return false; }
    }

    /**
     * Reads, marks and saves the baselines under {@link #BASELINES}. The boot and uptime are read only once the
     * lock is held: read before waiting, a baseline an apply records meanwhile would look newer than "now".
     * @return whether a baseline is UNKNOWN in this boot
     * @throws IllegalStateException if the marks could not be saved (they are kept, see {@link #saveMarks})
     * @throws TimeoutException if the lock was not free within {@code waitMs}; nothing was read or marked
     */
    static boolean quarantineSaved(SharedPreferences prefs, IntSupplier bootCount, LongSupplier uptime, long waitMs)
            throws JSONException, TimeoutException, InterruptedException {
        if (!BASELINES.tryLock(waitMs, TimeUnit.MILLISECONDS)) throw new TimeoutException();
        try {
            int boot = bootCount.getAsInt(); long elapsed = uptime.getAsLong();
            JSONObject backups = new JSONObject(prefs.getString("baselines", "{}"));
            if (quarantine(backups, boot, elapsed)) saveMarks(prefs, backups.toString());
            for (Iterator<String> it = backups.keys(); it.hasNext(); ) {
                JSONObject entry = backups.optJSONObject(it.next());
                if (entry != null && recordedBoot(entry, boot, elapsed) == NameConfig.Boot.UNKNOWN) return true;
            }
            return false;
        } finally { BASELINES.unlock(); }
    }
    static JSONObject profile(SubscriptionInfo s, String label, boolean override, boolean alwaysRat) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", s.getSubscriptionId()); o.put("carrier", s.getCarrierId());
        o.put("mcc", s.getMccString() == null ? "" : s.getMccString());
        o.put("mnc", s.getMncString() == null ? "" : s.getMncString());
        o.put("slot", s.getSimSlotIndex()); o.put("label", label); o.put("rename", override);
        o.put("alwaysRat", alwaysRat);
        // eSIM or physical and the SIM's own name, for the row once the line is not active (Android no longer
        // reports them then).
        o.put("embedded", s.isEmbedded());
        o.put("name", ownName(s));
        return o;
    }

    /** Android's SIM label (which this app never changes), or the carrier name when there is none. */
    static String ownName(SubscriptionInfo s) {
        CharSequence label = s.getDisplayName();
        return label != null && label.length() > 0 ? label.toString() : String.valueOf(s.getCarrierName());
    }

    /**
     * A line's own name, as seen in the list of lines not in use that earlier versions showed on the bands screen;
     * null when never seen. Only read now: lines saved since keep their name in the entry.
     */
    static String knownName(Context c, int subId) { return prefs(c).getString("name_" + subId, null); }

    /** eSIM or physical for a line, seen the same way; null when never seen. */
    static Boolean knownKind(Context c, int subId) {
        SharedPreferences p = prefs(c);
        String key = "embedded_" + subId;
        return p.contains(key) ? p.getBoolean(key, false) : null;
    }

    /**
     * Records eSIM-or-physical on saved entries of active SIMs that were saved before it was kept.
     * @return whether an entry changed
     */
    static boolean noteKinds(JSONArray saved, List<SubscriptionInfo> active) throws JSONException {
        boolean changed = false;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject p = saved.getJSONObject(i);
            if (p.has("embedded") && p.has("name")) continue;
            for (SubscriptionInfo s : active) if (matches(p, s)) {
                p.put("embedded", s.isEmbedded()); p.put("name", ownName(s)); changed = true;
            }
        }
        return changed;
    }
    static boolean matches(JSONObject p, SubscriptionInfo s) throws JSONException {
        return Identity.matches(p.getInt("id"), p.getInt("carrier"), p.getString("mcc"), p.getString("mnc"),
                s.getSubscriptionId(), s.getCarrierId(), s.getMccString(), s.getMncString());
    }
    /** Same carrier and network, any subscription ID: a candidate for explicit re-assignment only. */
    static boolean sameCarrier(JSONObject p, SubscriptionInfo s) throws JSONException {
        return Identity.sameCarrier(p.getInt("carrier"), p.getString("mcc"), p.getString("mnc"),
                s.getCarrierId(), s.getMccString(), s.getMncString());
    }

    /** Checks user input without needing a baseline (the first apply records it). */
    static void validate(JSONObject p) throws JSONException {
        if (p.getBoolean("rename")) NameConfig.label(p.optString("label", ""));
    }

    /** Keys this profile manages; profiles from before 0.1.2 did not manage RAT visibility. */
    static String[] managedKeys(JSONObject p) {
        List<String> keys = new ArrayList<>();
        keys.add(SHOW);
        for (String k : NameConfig.KEYS) keys.add(k);
        if (p.has("alwaysRat")) keys.add(RAT);
        if (p.has("alwaysRat")) keys.add(HIDE_PLUS);
        return keys.toArray(new String[0]);
    }

    /**
     * The values for one SIM: its name (custom, or the pre-change one), and the two whole-device switches (on, or
     * the pre-change value). A key the baseline does not hold is left out rather than guessed.
     */
    static Map<String, Object> desiredValues(boolean rename, String label, boolean showName, boolean alwaysRat,
                                             boolean showPlus, Map<String, Object> baseline) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (showName) out.put(SHOW, true); else if (baseline != null && baseline.containsKey(SHOW)) out.put(SHOW, baseline.get(SHOW));
        if (alwaysRat) out.put(RAT, true); else if (baseline != null && baseline.containsKey(RAT)) out.put(RAT, baseline.get(RAT));
        if (showPlus) out.put(HIDE_PLUS, false); else if (baseline != null && baseline.containsKey(HIDE_PLUS)) out.put(HIDE_PLUS, baseline.get(HIDE_PLUS));
        out.putAll(NameConfig.values(rename, label, baseline));
        return out;
    }

    static PersistableBundle desired(boolean rename, String label, boolean showName, boolean alwaysRat, boolean showPlus,
                                     JSONObject baselineValues) throws JSONException {
        PersistableBundle b = new PersistableBundle();
        for (Map.Entry<String, Object> e : desiredValues(rename, label, showName, alwaysRat, showPlus, toMap(baselineValues)).entrySet()) {
            Object value = e.getValue();
            if (value == null || value instanceof String) b.putString(e.getKey(), (String) value);
            else if (value instanceof Boolean) b.putBoolean(e.getKey(), (Boolean) value);
            else if (value instanceof Integer) b.putInt(e.getKey(), (Integer) value);
        }
        return b;
    }

    static Map<String, Object> toMap(JSONObject o) throws JSONException {
        if (o == null) return null;
        Map<String, Object> m = new HashMap<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next(); Object v = o.get(k);
            m.put(k, v == JSONObject.NULL ? null : v);
        }
        return m;
    }
    static JSONObject snapshot(PersistableBundle actual, String[] keys) throws JSONException {
        JSONObject o = new JSONObject();
        for (String k : keys) {
            if (!actual.containsKey(k)) throw new IllegalStateException(t("設定を取得できません: ", "Cannot read setting: ") + k);
            Object v = actual.get(k); o.put(k, v == null ? JSONObject.NULL : v);
        }
        return o;
    }
    static PersistableBundle bundle(JSONObject o) throws JSONException {
        PersistableBundle b = new PersistableBundle();
        for (String k : KEYS) if (o.has(k)) {
            Object v = o.get(k);
            if (v instanceof Boolean) b.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) b.putInt(k, (Integer) v);
            else if (v instanceof String) b.putString(k, (String) v);
            else if (v == JSONObject.NULL) b.putString(k, null);
            else throw new IllegalArgumentException(t("未対応の設定値: ", "Unsupported setting value: ") + k);
        }
        return b;
    }
    static boolean contains(PersistableBundle actual, PersistableBundle desired) {
        if (actual == null) return false;
        for (String k : desired.keySet()) {
            Object a = actual.get(k), b = desired.get(k);
            if (!actual.containsKey(k) || !java.util.Objects.equals(a, b)) return false;
        }
        return true;
    }

    /** The recorded baseline whose identity matches this active SIM, or null. */
    static JSONObject baselineFor(Context c, SubscriptionInfo s) {
        try {
            JSONObject entry = baselines(c).optJSONObject(Integer.toString(s.getSubscriptionId()));
            return entry != null && matches(entry.getJSONObject("identity"), s) ? entry : null;
        } catch (JSONException e) { return null; }
    }

    /**
     * Saves one value, or throws so that a request depending on it does not start. commit() keeps a value it
     * could not write in memory, where this process (e.g. RestoreJob) would read it: the old value is put back.
     */
    @SuppressLint("ApplySharedPref") // must be on disk before the apply/instrumentation reads it
    static void save(Context c, String key, Object value) { save(prefs(c), key, value); }

    @SuppressLint("ApplySharedPref") // must be on disk before the apply/instrumentation reads it
    static void save(SharedPreferences prefs, String key, Object value) {
        Object old = prefs.getAll().get(key);
        if (put(prefs.edit(), key, value).commit()) return;
        // Puts the old value back in memory and asks for it to be written too; that write is not checked.
        put(prefs.edit(), key, old).apply();
        throw new IllegalStateException(t("設定を保存できませんでした。何も変更していません", "Could not save the settings. Nothing was changed"));
    }

    private static SharedPreferences.Editor put(SharedPreferences.Editor edit, String key, Object value) {
        if (value == null) return edit.remove(key);
        if (value instanceof Boolean) return edit.putBoolean(key, (Boolean) value);
        if (value instanceof String) return edit.putString(key, (String) value);
        throw new IllegalArgumentException(t("未対応の設定値: ", "Unsupported setting value: ") + key);
    }

    /**
     * Explicit, user-confirmed re-assignment of a saved profile to a SIM whose subscription ID changed.
     * The old baseline stays under its old ID; the next apply records a fresh one for the new subscription.
     */
    @SuppressLint("ApplySharedPref") // must be on disk before the apply/instrumentation reads it
    static void reassign(Context c, int index, SubscriptionInfo s) throws JSONException {
        JSONArray list = profiles(c);
        JSONObject p = list.getJSONObject(index);
        if (!sameCarrier(p, s)) throw new IllegalStateException(t("回線事業者が異なるSIMには割り当てできません", "Cannot assign to a SIM of a different carrier"));
        p.put("id", s.getSubscriptionId());
        p.put("slot", s.getSimSlotIndex());
        save(c, "profiles", list.toString());
    }

    /**
     * The settings to save: those chosen for the SIMs on screen, plus the saved ones of SIMs that are not active now.
     * A name belongs to its line, so switching to another eSIM and saving must not drop the inactive line's settings.
     * @param activeIds subscription IDs of the SIMs on screen; their old entries are replaced by {@code chosen}
     */
    static JSONArray withInactive(JSONArray chosen, JSONArray old, java.util.Set<Integer> activeIds) throws JSONException {
        JSONArray out = new JSONArray();
        for (int i = 0; i < chosen.length(); i++) out.put(chosen.getJSONObject(i));
        for (int i = 0; i < old.length(); i++) {
            JSONObject p = old.getJSONObject(i);
            if (!activeIds.contains(p.optInt("id", -1))) out.put(p);
        }
        return out;
    }

    static void report(Context c, String message) {
        prefs(c).edit().putString("report", java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.MEDIUM, Tr.locale()).format(new java.util.Date()) + "\n" + message).commit();
    }
    @SuppressLint("ApplySharedPref") // must be on disk before the apply/instrumentation reads it
    static void appendReport(Context c, String message) {
        String current = prefs(c).getString("report", "");
        prefs(c).edit().putString("report", current.isEmpty() ? message : current + "\n" + message).commit();
    }
}
