package io.github.fortuneoflab.dualsimbands;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StoreProfilesTest {
    private static JSONObject profile(int id, String label) throws Exception {
        return new JSONObject().put("id", id).put("label", label).put("rename", true);
    }

    /** Seen on the Pixel 10a: saving while the docomo eSIM was active dropped the LINEMO line's name. */
    @Test public void savingKeepsInactiveLines() throws Exception {
        JSONArray old = new JSONArray().put(profile(2, "au")).put(profile(5, "SoftBank"));
        JSONArray chosen = new JSONArray().put(profile(4, "docomo"));
        Set<Integer> active = new HashSet<>();
        active.add(2); active.add(4);
        JSONArray saved = Store.withInactive(chosen, old, active);
        assertEquals(2, saved.length());
        assertEquals(4, saved.getJSONObject(0).getInt("id"));
        assertEquals("SoftBank", saved.getJSONObject(1).getString("label"));
    }

    /** An active SIM left unchecked is dropped; it is on screen, so its old entry is not kept. */
    @Test public void activeEntriesAreReplaced() throws Exception {
        JSONArray old = new JSONArray().put(profile(2, "au"));
        Set<Integer> active = new HashSet<>();
        active.add(2);
        JSONArray saved = Store.withInactive(new JSONArray().put(profile(2, "KDDI")), old, active);
        assertEquals(1, saved.length());
        assertEquals("KDDI", saved.getJSONObject(0).getString("label"));
        assertEquals(0, Store.withInactive(new JSONArray(), old, active).length());
    }

    /** The switches are on for every SIM, or that SIM's pre-change value; a name only where it was given. */
    @Test public void desiredValuesFollowTheSwitches() {
        Map<String, Object> baseline = new HashMap<>();
        baseline.put(Store.SHOW, false); baseline.put(Store.RAT, false); baseline.put(Store.HIDE_PLUS, true);
        baseline.put(NameConfig.NAME, ""); baseline.put(NameConfig.OVERRIDE, false);
        baseline.put(NameConfig.RESOLVER, false); baseline.put(NameConfig.CONDITION, -1);

        Map<String, Object> on = Store.desiredValues(true, "au", true, true, true, baseline);
        assertEquals(true, on.get(Store.SHOW));
        assertEquals(true, on.get(Store.RAT));
        assertEquals(false, on.get(Store.HIDE_PLUS));
        assertEquals("au", on.get(NameConfig.NAME));
        assertEquals(true, on.get(NameConfig.OVERRIDE));

        // Everything off: the SIM gets its pre-change values back.
        Map<String, Object> off = Store.desiredValues(false, "", false, false, false, baseline);
        assertEquals(false, off.get(Store.SHOW));
        assertEquals(false, off.get(Store.RAT));
        assertEquals(true, off.get(Store.HIDE_PLUS));
        assertEquals("", off.get(NameConfig.NAME));
        assertEquals(false, off.get(NameConfig.OVERRIDE));

        // A key missing from the baseline is left out, not guessed.
        baseline.remove(Store.RAT);
        assertFalse(Store.desiredValues(false, "", false, false, false, baseline).containsKey(Store.RAT));
    }
}
