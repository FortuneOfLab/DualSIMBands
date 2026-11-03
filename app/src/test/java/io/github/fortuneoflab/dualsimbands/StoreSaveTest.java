package io.github.fortuneoflab.dualsimbands;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StoreSaveTest {
    /** Like SharedPreferencesImpl: an edit reaches memory even when writing it to disk fails. */
    private static final class Prefs implements SharedPreferences {
        final Map<String, Object> memory = new HashMap<>();
        boolean diskWritable = true;

        @Override public Map<String, ?> getAll() { return new HashMap<>(memory); }
        @Override public String getString(String k, String d) { return memory.containsKey(k) ? (String) memory.get(k) : d; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { throw new UnsupportedOperationException(); }
        @Override public int getInt(String k, int d) { throw new UnsupportedOperationException(); }
        @Override public long getLong(String k, long d) { throw new UnsupportedOperationException(); }
        @Override public float getFloat(String k, float d) { throw new UnsupportedOperationException(); }
        @Override public boolean getBoolean(String k, boolean d) { return memory.containsKey(k) ? (Boolean) memory.get(k) : d; }
        @Override public boolean contains(String k) { return memory.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        @Override public Editor edit() {
            Map<String, Object> changes = new HashMap<>();
            return new Editor() {
                @Override public Editor putString(String k, String v) { changes.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { throw new UnsupportedOperationException(); }
                @Override public Editor putInt(String k, int v) { throw new UnsupportedOperationException(); }
                @Override public Editor putLong(String k, long v) { throw new UnsupportedOperationException(); }
                @Override public Editor putFloat(String k, float v) { throw new UnsupportedOperationException(); }
                @Override public Editor putBoolean(String k, boolean v) { changes.put(k, v); return this; }
                @Override public Editor remove(String k) { changes.put(k, null); return this; }
                @Override public Editor clear() { throw new UnsupportedOperationException(); }
                @Override public boolean commit() { apply(); return diskWritable; }
                @Override public void apply() {
                    for (Map.Entry<String, Object> e : changes.entrySet())
                        if (e.getValue() == null) memory.remove(e.getKey()); else memory.put(e.getKey(), e.getValue());
                }
            };
        }
    }

    @Test public void savedValueIsKept() {
        Prefs prefs = new Prefs();
        Store.save(prefs, "auto", false);
        assertEquals(false, prefs.memory.get("auto"));
    }

    /** Restore pressed with auto-restore on: a failed save must not leave RestoreJob reading "off". */
    @Test public void failedSaveLeavesTheOldValueInMemory() {
        Prefs prefs = new Prefs();
        prefs.memory.put("auto", true);
        prefs.diskWritable = false;
        try {
            Store.save(prefs, "auto", false);
            fail("a failed save must stop the request");
        } catch (IllegalStateException expected) { /* the request does not start */ }
        assertTrue(prefs.getBoolean("auto", false));
    }

    @Test public void failedSaveOfANewKeyLeavesItAbsent() {
        Prefs prefs = new Prefs();
        prefs.diskWritable = false;
        try {
            Store.save(prefs, "profiles", "[{\"id\":2}]");
            fail("a failed save must stop the request");
        } catch (IllegalStateException expected) { /* the request does not start */ }
        assertFalse(prefs.contains("profiles"));
    }

    /**
     * Marks found after an update but not saved: an apply scheduled from any entry point (Shizuku, screen) must
     * stop until they are saved, then find them in the preferences.
     */
    @Test public void unsavedMarksStopEveryApplyUntilSaved() {
        Prefs prefs = new Prefs();
        prefs.memory.put("baselines", "{\"2\":{\"boot\":12}}");
        prefs.diskWritable = false;
        String marked = "{\"2\":{\"boot\":12,\"seenBoot\":12}}";
        try {
            Store.saveMarks(prefs, marked);
            fail("a failed save of the marks must be reported");
        } catch (IllegalStateException expected) { /* the update notice */ }
        assertEquals("{\"2\":{\"boot\":12}}", prefs.getString("baselines", null));
        try {
            Store.saveUnsavedMarks(prefs);
            fail("an apply must stop while the marks are unsaved");
        } catch (IllegalStateException expected) { /* the apply stops */ }
        prefs.diskWritable = true;
        Store.saveUnsavedMarks(prefs);
        assertEquals(marked, prefs.getString("baselines", null));
        Store.saveUnsavedMarks(prefs); // nothing left to save
        assertEquals(marked, prefs.getString("baselines", null));
    }

    /**
     * An update marks SIM A while an apply holds the baselines and records SIM B at uptime 9100: the marking waits
     * for the apply, keeps B's new record, and dates it with the uptime after waiting (9200), not before (9000),
     * so B is not marked as undatable.
     */
    @Test public void updateMarkingWaitsForAnApplyAndKeepsItsRecord() throws Exception {
        Prefs prefs = new Prefs();
        prefs.memory.put("baselines", "{\"2\":{\"boot\":-1}}"); // SIM A: undatable, B not recorded yet
        AtomicLong uptime = new AtomicLong(9_000);
        Store.BASELINES.lock(); // the apply has read the baselines
        Thread update = new Thread(() -> {
            try { Store.quarantineSaved(prefs, () -> 12, uptime::get, 5_000); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        try {
            update.start();
            awaitQueued(update);
            prefs.memory.put("baselines", "{\"2\":{\"boot\":-1},\"4\":{\"boot\":12,\"elapsed\":9100}}"); // B recorded
            uptime.set(9_200);
        } finally {
            Store.BASELINES.unlock();
            finish(update);
        }
        JSONObject saved = new JSONObject(prefs.getString("baselines", ""));
        assertEquals(12, saved.getJSONObject("2").getInt("seenBoot"));
        assertFalse("B was recorded in this boot", saved.getJSONObject("4").has("seenBoot"));
    }

    /** An apply holding the baselines too long: the marking gives up without reading or saving anything. */
    @Test(expected = TimeoutException.class)
    public void updateMarkingGivesUpWhenTheLockStaysBusy() throws Exception {
        Prefs prefs = new Prefs();
        prefs.memory.put("baselines", "{\"2\":{\"boot\":-1}}");
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread apply = new Thread(() -> {
            Store.BASELINES.lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException ignored) { /* the test ends */ }
            finally { Store.BASELINES.unlock(); }
        });
        apply.start();
        try {
            assertTrue("the apply must hold the lock", held.await(5, TimeUnit.SECONDS));
            Store.quarantineSaved(prefs, () -> 12, () -> 9_000, 200);
        } finally {
            release.countDown();
            finish(apply);
            assertEquals("{\"2\":{\"boot\":-1}}", prefs.getString("baselines", null));
        }
    }

    /**
     * Makes sure a test thread has ended, so it cannot touch the shared lock during later tests: interrupted if
     * still running after 5 s, and reported as a failure if even that does not end it.
     */
    private static void finish(Thread thread) throws InterruptedException {
        thread.join(5_000);
        if (!thread.isAlive()) return;
        thread.interrupt();
        thread.join(5_000);
        assertFalse(thread.getName() + " did not end", thread.isAlive());
        fail(thread.getName() + " ended only when interrupted");
    }

    /** Waits until {@code thread} is queued on the baselines lock, i.e. really waiting for the apply. */
    private static void awaitQueued(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Store.BASELINES.hasQueuedThread(thread)) {
            assertTrue("marking must wait for the apply", System.nanoTime() < deadline);
            Thread.sleep(10);
        }
    }
}
