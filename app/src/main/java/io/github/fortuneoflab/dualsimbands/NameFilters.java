package io.github.fortuneoflab.dualsimbands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Records kept around {@link ApplyInstrumentation}'s status bar name update, without Android calls so they can be
 * tested: the marks of network name filters changed for a moment ("nameFilters" in the prefs), and the names this
 * app may have left shown on a line ("applied" in the line's pre-change record).
 */
final class NameFilters {
    private NameFilters() {}

    /** A filter change under way: the line, the value to put back, and the boot it was made in. */
    static final class Mark {
        final int id;
        final String original;
        final int boot;
        Mark(int id, String original, int boot) { this.id = id; this.original = original; this.boot = boot; }
    }

    /** @throws JSONException when the stored marks cannot be read: no new filter change may start then */
    static JSONObject parse(String stored) throws JSONException {
        return new JSONObject(stored == null || stored.isEmpty() ? "{}" : stored);
    }

    static boolean marked(JSONObject marks, int id) { return marks.has(Integer.toString(id)); }

    /**
     * Adds a mark; an existing one keeps its value, as only the first holds the carrier's own filter.
     * @return false when the boot count is unknown: a mark that cannot be dated must not be made, so no filter change starts
     */
    static boolean mark(JSONObject marks, int id, String original, int boot) throws JSONException {
        if (boot < 0) return false;
        String key = Integer.toString(id);
        if (!marks.has(key)) marks.put(key, new JSONObject().put("original", original).put("boot", boot));
        return true;
    }

    static void unmark(JSONObject marks, int id) { marks.remove(Integer.toString(id)); }

    /** A non-negative whole number stored under {@code key}, or -1 when missing or of another type. */
    static int count(JSONObject entry, String key) {
        Object v = entry == null ? null : entry.opt(key);
        if (!(v instanceof Integer) && !(v instanceof Long)) return -1;
        long n = ((Number) v).longValue();
        return n >= 0 && n <= Integer.MAX_VALUE ? (int) n : -1;
    }

    /**
     * The marks to settle in this boot. A mark from another boot is removed: a reboot cleared the override and the
     * filter Android kept, so writing the old value back could cover a newer carrier value. An entry that cannot be
     * read (a wrong type, no boot, or the bare value an earlier build kept) is never written back: it is dated with the
     * boot it was first seen in ("seen") and removed after the next reboot, as it still blocks new changes until then.
     * With the boot count unknown, nothing is settled, dated or removed.
     */
    static List<Mark> toSettle(JSONObject marks, int boot) throws JSONException {
        List<Mark> out = new ArrayList<>();
        if (boot < 0) return out;
        List<String> old = new ArrayList<>(), unread = new ArrayList<>();
        for (Iterator<String> it = marks.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject entry = marks.optJSONObject(key);
            int id;
            try { id = Integer.parseInt(key); } catch (NumberFormatException e) { id = -1; }
            int made = count(entry, "boot");
            if (id >= 0 && made >= 0 && entry.opt("original") instanceof String) {
                if (made != boot) old.add(key);
                else out.add(new Mark(id, entry.getString("original"), boot));
                continue;
            }
            int seen = count(entry, "seen");
            if (seen < 0) unread.add(key);
            else if (seen != boot) old.add(key);
        }
        for (String key : old) marks.remove(key);
        for (String key : unread) marks.put(key, new JSONObject().put("seen", boot));
        return out;
    }

    /** What to do when the whole record of marks cannot be read. */
    enum Broken {
        /** The boot count is unknown: nothing can be dated, nothing changes. */
        WAIT,
        /** First seen now: the boot is kept beside the record. */
        DATE,
        /** Seen earlier in this boot: still blocks status bar name updates. */
        KEEP,
        /** Seen in an earlier boot: the reboot cleared any filter it was about, so it is emptied. */
        RESET
    }

    /**
     * @param seen the boot the unreadable record was first seen in, or -1. It is removed whenever the record reads
     *             again, so it never belongs to an earlier breakage.
     */
    static Broken broken(int seen, int boot) {
        if (boot < 0) return Broken.WAIT;
        if (seen < 0) return Broken.DATE;
        return seen == boot ? Broken.KEEP : Broken.RESET;
    }

    /** Key in a line's pre-change record: names this app put on the line that may still be shown. */
    static final String APPLIED = "applied";

    /** Read from a list, or from the single name an earlier build kept. */
    static List<String> applied(JSONObject entry) {
        List<String> out = new ArrayList<>();
        if (entry == null) return out;
        JSONArray list = entry.optJSONArray(APPLIED);
        if (list != null) {
            for (int i = 0; i < list.length(); i++) if (!list.optString(i, "").isEmpty()) out.add(list.optString(i));
        } else if (!entry.optString(APPLIED, "").isEmpty()) {
            out.add(entry.optString(APPLIED));
        }
        return out;
    }

    /** Before a name is written: the earlier ones stay, as the write may not take effect. */
    static void addApplied(JSONObject entry, String name) throws JSONException {
        List<String> names = applied(entry);
        if (!names.contains(name)) names.add(name);
        entry.put(APPLIED, new JSONArray(names));
    }

    /** Once {@code name} is seen in the status bar, only it can be left shown. */
    static boolean confirmApplied(JSONObject entry, String name) throws JSONException {
        List<String> names = applied(entry);
        if (!names.contains(name) || names.size() == 1) return false;
        entry.put(APPLIED, new JSONArray().put(name));
        return true;
    }

    /**
     * The name the status bar has to show: the carrier-name override's, for a line given a custom name (the custom name,
     * or the network's name {@code replaceStaleName} put there). Null for a line without one, also when its config has
     * a name override of the carrier's own: Android may show it with the network's name (seen on the Pixel 10a: povo's
     * own "povo" shown as "KDDI - povo"), which would never match and had the name worked out again on every apply.
     */
    static String wanted(boolean rename, boolean override, String name) {
        return rename && override ? name : null;
    }

    /**
     * Whether the status bar shows the right name for a line. An empty name (the SIM could not be read) is never right.
     * @param wanted the name the carrier-name override holds, or null without an override
     * @param applied without an override, the names this app may have left shown on the line
     * @param network the network's name: a custom name equal to it is not told apart from the line's own name
     */
    static boolean shownRight(String shown, String wanted, List<String> applied, String network) {
        if (shown == null || shown.isEmpty()) return false;
        if (wanted != null) return wanted.equals(shown);
        return !applied.contains(shown) || shown.equals(network);
    }

    /**
     * As {@link #shownRight(String, String, List, String)}, with another line's custom name that may have been left
     * on the slot ({@link #leftOver}) also not right until it has been worked out again.
     */
    static boolean shownRight(String shown, String wanted, List<String> applied, String left, String network) {
        if (!shownRight(shown, wanted, applied, network)) return false;
        return wanted != null || left == null || !left.equals(shown) || shown.equals(network);
    }

    /**
     * Another line's custom name found in this SIM's carrier name ({@link Store#staleName}), as a name that may have
     * been left on the slot: only while the SIM's own name reads and is different, as otherwise nothing tells it from
     * this SIM's own carrier name. Null when it does not count.
     */
    static String leftOver(String stale, String simName) {
        if (stale == null || simName == null || simName.isEmpty() || stale.equals(simName)) return null;
        return stale;
    }
}
