package io.github.fortuneoflab.dualsimbands;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StoreStaleNameTest {
    private static final int BOOT = 40;
    private static final long NOW = 600_000;

    private static JSONObject profile(int id, String label, boolean rename) throws Exception {
        return new JSONObject().put("id", id).put("label", label).put("rename", rename);
    }

    private static JSONObject baseline(int boot, long elapsed) throws Exception {
        return new JSONObject().put("boot", boot).put("elapsed", elapsed);
    }

    /** LINEMO (5) renamed "SoftBank" this boot, then the docomo eSIM (4) took its port: the name is stale. */
    @Test public void nameOfALineRenamedThisBootAndGoneIsStale() throws Exception {
        JSONArray saved = new JSONArray().put(profile(5, "SoftBank", true));
        JSONObject backups = new JSONObject().put("5", baseline(BOOT, 1000));
        assertEquals("SoftBank", Store.staleName(saved, backups, Collections.singleton(4), BOOT, NOW,
                4, profile(4, "", false), "SoftBank"));
    }

    /** Another line's custom name equal to this SIM's own name, with that line still in use: not stale. */
    @Test public void sameNameOfALineInUseIsNotStale() throws Exception {
        JSONArray saved = new JSONArray().put(profile(2, "IIJmio", true));
        JSONObject backups = new JSONObject().put("2", baseline(BOOT, 1000));
        Set<Integer> inUse = new HashSet<>(java.util.Arrays.asList(2, 4));
        assertNull(Store.staleName(saved, backups, inUse, BOOT, NOW, 4, profile(4, "", false), "IIJmio"));
    }

    /** The other line was never changed by this app in this boot: no override of it can be on the slot. */
    @Test public void sameNameWithoutAnOverrideThisBootIsNotStale() throws Exception {
        JSONArray saved = new JSONArray().put(profile(5, "SoftBank", true));
        assertNull(Store.staleName(saved, new JSONObject(), Collections.singleton(4), BOOT, NOW,
                4, profile(4, "", false), "SoftBank"));
        JSONObject earlier = new JSONObject().put("5", baseline(BOOT - 1, 1000));
        assertNull(Store.staleName(saved, earlier, Collections.singleton(4), BOOT, NOW,
                4, profile(4, "", false), "SoftBank"));
    }

    @Test public void ownWantedNameIsNeverStale() throws Exception {
        JSONArray saved = new JSONArray().put(profile(5, "SoftBank", true)).put(profile(4, "SoftBank", true));
        JSONObject backups = new JSONObject().put("5", baseline(BOOT, 1000));
        assertNull(Store.staleName(saved, backups, Collections.singleton(4), BOOT, NOW,
                4, profile(4, "SoftBank", true), "SoftBank"));
    }
}
