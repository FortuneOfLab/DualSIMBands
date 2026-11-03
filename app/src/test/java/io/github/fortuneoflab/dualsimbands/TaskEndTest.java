package io.github.fortuneoflab.dualsimbands;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskEndTest {
    /** Strings only, with change listeners called on every written key, like SharedPreferencesImpl. */
    private static final class Prefs implements SharedPreferences {
        final Map<String, Object> memory = new HashMap<>();
        final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(memory); }
        @Override public String getString(String k, String d) { return memory.containsKey(k) ? (String) memory.get(k) : d; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { throw new UnsupportedOperationException(); }
        @Override public int getInt(String k, int d) { throw new UnsupportedOperationException(); }
        @Override public long getLong(String k, long d) { throw new UnsupportedOperationException(); }
        @Override public float getFloat(String k, float d) { throw new UnsupportedOperationException(); }
        @Override public boolean getBoolean(String k, boolean d) { throw new UnsupportedOperationException(); }
        @Override public boolean contains(String k) { return memory.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.add(l); }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.remove(l); }

        @Override public Editor edit() {
            Map<String, Object> changes = new HashMap<>();
            return new Editor() {
                @Override public Editor putString(String k, String v) { changes.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { throw new UnsupportedOperationException(); }
                @Override public Editor putInt(String k, int v) { throw new UnsupportedOperationException(); }
                @Override public Editor putLong(String k, long v) { throw new UnsupportedOperationException(); }
                @Override public Editor putFloat(String k, float v) { throw new UnsupportedOperationException(); }
                @Override public Editor putBoolean(String k, boolean v) { throw new UnsupportedOperationException(); }
                @Override public Editor remove(String k) { changes.put(k, null); return this; }
                @Override public Editor clear() { throw new UnsupportedOperationException(); }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() {
                    for (Map.Entry<String, Object> e : changes.entrySet()) {
                        if (e.getValue() == null) memory.remove(e.getKey()); else memory.put(e.getKey(), e.getValue());
                        for (OnSharedPreferenceChangeListener l : new ArrayList<>(listeners)) l.onSharedPreferenceChanged(Prefs.this, e.getKey());
                    }
                }
            };
        }
    }

    private final AtomicLong now = new AtomicLong();
    private final AtomicInteger settled = new AtomicInteger();
    private Prefs prefs;

    @Before public void setUp() {
        prefs = new Prefs();
        TaskEnd.reset();
        TaskEnd.clock = now::get;
        TaskEnd.sleeper = ms -> now.addAndGet(ms);
        TaskEnd.setOnSettled(settled::incrementAndGet);
    }

    @After public void tearDown() { TaskEnd.reset(); TaskEnd.setOnSettled(null); }

    private void giveUp(String token) throws InterruptedException {
        prefs.edit().putString("pending", token).commit();
        try {
            TaskEnd.await(prefs, token, 90_000, null, "no answer");
            fail("expected Unfinished");
        } catch (TaskEnd.Unfinished expected) {
            assertEquals("no answer", expected.getMessage());
        }
    }

    @Test public void finishedInTimeReturnsAndLeavesNothingUnsettled() throws Exception {
        TaskEnd.sleeper = ms -> { now.addAndGet(ms); if (now.get() >= 5_000) prefs.edit().putString(TaskEnd.FINISHED, "t1").commit(); };
        TaskEnd.await(prefs, "t1", 90_000, null, "no answer");
        assertFalse(TaskEnd.unsettled());
    }

    @Test public void lateRunsOnceAndTheWaitGoesOn() throws Exception {
        AtomicInteger late = new AtomicInteger();
        TaskEnd.sleeper = ms -> { now.addAndGet(ms); if (now.get() >= 200_000) prefs.edit().putString(TaskEnd.FINISHED, "t1").commit(); };
        TaskEnd.await(prefs, "t1", 90_000, late::incrementAndGet, "no answer");
        assertEquals(1, late.get());
        assertFalse(TaskEnd.unsettled());
    }

    /** Past the longest wait: still refused, and a late start of it is stopped at its token check. */
    @Test public void pastTheLimitStaysUnsettledAndClearsPending() throws Exception {
        giveUp("t1");
        assertTrue(now.get() >= TaskEnd.LATE_LIMIT_MS);
        assertNull(prefs.getString("pending", null));
        assertTrue(TaskEnd.unsettled());
        now.addAndGet(60 * 60_000);
        assertTrue(TaskEnd.unsettled());
        assertEquals(0, settled.get());
    }

    /** It ends after all: no longer refused, and a change saved meanwhile gets applied. */
    @Test public void aLateEndSettlesAndAppliesTheWaitingChange() throws Exception {
        giveUp("t1");
        prefs.edit().putString(TaskEnd.FINISHED, "t1").commit();
        assertEquals(1, settled.get());
        assertFalse(TaskEnd.unsettled());
    }

    /** A late start that stopped at its token check did nothing, so it settles too. */
    @Test public void anAbandonedLateStartSettles() throws Exception {
        giveUp("t1");
        prefs.edit().putString(TaskEnd.ABANDONED, "t1").commit();
        assertEquals(1, settled.get());
        assertFalse(TaskEnd.unsettled());
    }

    @Test public void anotherTokensEndDoesNotSettle() throws Exception {
        giveUp("t1");
        prefs.edit().putString(TaskEnd.FINISHED, "t2").commit();
        assertEquals(0, settled.get());
        assertTrue(TaskEnd.unsettled());
    }
}
