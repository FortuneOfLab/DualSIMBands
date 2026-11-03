package io.github.fortuneoflab.dualsimbands;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameConfigTest {
    private static Map<String, Object> baseline(String name, boolean override, boolean resolver, int condition) {
        Map<String, Object> m = new HashMap<>();
        m.put(NameConfig.NAME, name);
        m.put(NameConfig.OVERRIDE, override);
        m.put(NameConfig.RESOLVER, resolver);
        m.put(NameConfig.CONDITION, condition);
        m.put("always_show_data_rat_icon_bool", false); // other recorded keys are ignored here
        return m;
    }

    @Test public void customNameWritesAllFourKeysExplicitly() {
        Map<String, Object> v = NameConfig.values(true, " docomo ", null);
        assertEquals("docomo", v.get(NameConfig.NAME));
        assertEquals(true, v.get(NameConfig.OVERRIDE));
        assertEquals(true, v.get(NameConfig.RESOLVER));
        assertEquals(2, v.get(NameConfig.CONDITION));
        assertEquals(4, v.size());
    }

    /** Decision (b): turning the override off writes back the recorded pre-change values, not ""/false. */
    @Test public void turningOffWritesTheBaselineNotBlankValues() {
        Map<String, Object> pixelIms = baseline("au", true, true, 2);
        Map<String, Object> actual = new HashMap<>(NameConfig.values(true, "custom", null));
        actual.putAll(NameConfig.values(false, "custom", pixelIms));
        assertEquals("au", actual.get(NameConfig.NAME));
        assertEquals(true, actual.get(NameConfig.OVERRIDE));
        assertEquals(true, actual.get(NameConfig.RESOLVER));
        assertEquals(2, actual.get(NameConfig.CONDITION));
    }

    @Test public void baselineOfTheReferenceDeviceEqualsThePreviousOffValues() {
        // Both SIMs on the Pixel 10a recorded "" / false / false / -1, so 0.1.3 users see no change.
        Map<String, Object> v = NameConfig.values(false, "", baseline("", false, false, -1));
        assertEquals("", v.get(NameConfig.NAME));
        assertEquals(false, v.get(NameConfig.OVERRIDE));
        assertEquals(false, v.get(NameConfig.RESOLVER));
        assertEquals(-1, v.get(NameConfig.CONDITION));
        assertEquals(4, v.size());
    }

    @Test public void offIgnoresTheTextField() {
        Map<String, Object> b = baseline("", false, false, -1);
        assertEquals(NameConfig.values(false, null, b), NameConfig.values(false, new String(new char[100]), b));
    }

    @Test public void nullCarrierNameInBaselineIsKept() {
        Map<String, Object> b = baseline(null, false, false, -1);
        assertTrue(NameConfig.values(false, "", b).containsKey(NameConfig.NAME));
        assertNull(NameConfig.values(false, "", b).get(NameConfig.NAME));
    }

    @Test(expected = IllegalStateException.class) public void offWithoutBaselineFailsInsteadOfGuessing() {
        NameConfig.values(false, "", null);
    }

    @Test(expected = IllegalStateException.class) public void offWithAnIncompleteBaselineFails() {
        Map<String, Object> b = baseline("", false, false, -1);
        b.remove(NameConfig.RESOLVER);
        NameConfig.values(false, "", b);
    }

    @Test(expected = IllegalStateException.class) public void offWithAMistypedBaselineFails() {
        Map<String, Object> b = baseline("", false, false, -1);
        b.put(NameConfig.CONDITION, "-1");
        NameConfig.values(false, "", b);
    }

    @Test(expected = IllegalArgumentException.class) public void customNameMustNotBeEmpty() {
        NameConfig.values(true, " ", null);
    }

    @Test(expected = IllegalArgumentException.class) public void customNameMustNotExceedFortyCharacters() {
        NameConfig.values(true, new String(new char[41]).replace('\0', 'x'), null);
    }

    @Test public void fortyCharactersAreAccepted() {
        assertEquals(40, NameConfig.label(new String(new char[40]).replace('\0', 'x')).length());
    }

    /** A baseline from before a reboot may hold an override that the reboot already cleared. */
    @Test public void baselineFromAnEarlierBootIsRecordedAgain() {
        assertEquals(NameConfig.Boot.THIS, NameConfig.boot(12, 5_000, -1, 12, 9_000));
        assertEquals(NameConfig.Boot.EARLIER, NameConfig.boot(11, 900_000, -1, 12, 9_000));
    }

    /** Baselines of 2026-10-22 carry the boot count only. */
    @Test public void baselineWithoutUptimeIsDatedByTheBootCount() {
        assertEquals(NameConfig.Boot.THIS, NameConfig.boot(12, -1, -1, 12, 9_000));
    }

    /** "KDDI" -> apply "au" -> override off must not record "au": an undated baseline is never replaced. */
    @Test public void unavailableBootCountDatesNothing() {
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(12, 5_000, -1, -1, 9_000));
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(-1, -1, -1, -1, 9_000));
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(12, 5_000, 12, -1, 9_000));
    }

    @Test public void bootCountGoingBackOrReusedIsUnknown() {
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(13, 5_000, -1, 12, 9_000));
        // Same count, but the record was made later in its boot than "now": not this boot.
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(12, 50_000, -1, 12, 9_000));
    }

    /** Once found UNKNOWN (and marked), a growing uptime must not turn a reused count into THIS. */
    @Test public void markedBaselineStaysUnknownUntilALaterBoot() {
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(12, 50_000, 12, 12, 60_000));
        assertEquals(NameConfig.Boot.EARLIER, NameConfig.boot(12, 50_000, 12, 13, 1_000));
    }

    /** A count that went back recovers after the next reboot, without catching up with the recorded count. */
    @Test public void bootCountGoingBackRecoversAfterTheNextBoot() {
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(20, 5_000, 12, 12, 9_000));
        assertEquals(NameConfig.Boot.EARLIER, NameConfig.boot(20, 5_000, 12, 13, 9_000));
    }

    /** Old version applies "au", update without a reboot: the old baseline is unusable until the next boot. */
    @Test public void legacyBaselineWaitsForTheRebootAfterItWasSeen() {
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(-1, -1, -1, 12, 9_000)); // not seen yet: marked in this boot
        assertEquals(NameConfig.Boot.UNKNOWN, NameConfig.boot(-1, -1, 12, 12, 9_000)); // same boot as the update
        assertEquals(NameConfig.Boot.EARLIER, NameConfig.boot(-1, -1, 12, 13, 9_000));
    }

    /** Marked at 20, then the count went back to 12: marked again at 12, usable from 13 (not only from 21). */
    @Test public void markFollowsABootCountThatWentBelowIt() {
        NameConfig.Boot now = NameConfig.boot(-1, -1, 20, 12, 9_000);
        assertEquals(NameConfig.Boot.UNKNOWN, now);
        assertTrue(NameConfig.mark(now, 20, 12));
        assertEquals(NameConfig.Boot.EARLIER, NameConfig.boot(-1, -1, 12, 13, 9_000));
    }

    @Test public void markIsSetOnceAndNeverWithoutABootCount() {
        assertTrue(NameConfig.mark(NameConfig.Boot.UNKNOWN, -1, 12));
        assertFalse(NameConfig.mark(NameConfig.Boot.UNKNOWN, 12, 12)); // already marked in this boot
        assertFalse(NameConfig.mark(NameConfig.Boot.UNKNOWN, -1, -1));
        assertFalse(NameConfig.mark(NameConfig.Boot.THIS, -1, 12));
        assertFalse(NameConfig.mark(NameConfig.Boot.EARLIER, 11, 12));
    }
}
