package io.github.fortuneoflab.dualsimbands;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabelVerdictTest {
    @Test public void customLabelThatIsShown() {
        assertEquals(LabelVerdict.Result.MATCHES, LabelVerdict.evaluate(true, " au ", "au"));
    }

    @Test public void writeSucceededButNameNotYetUpdated() {
        LabelVerdict.Result r = LabelVerdict.evaluate(true, "au", "KDDI");
        assertEquals(LabelVerdict.Result.DIFFERS, r);
        String line = LabelVerdict.describe("SIM 1", r, "KDDI");
        assertTrue(line.contains("まだ「KDDI」"));
        assertTrue(line.contains("書き込み済み"));
        assertFalse(line.contains("と表示されています"));
    }

    @Test public void overrideOffHasNoFixedExpectation() {
        assertEquals(LabelVerdict.Result.NO_EXPECTATION, LabelVerdict.evaluate(false, "au", "KDDI"));
    }

    @Test public void unreadableNameIsUnknown() {
        assertEquals(LabelVerdict.Result.UNKNOWN, LabelVerdict.evaluate(true, "au", null));
        assertEquals("SIM 2：表示名を確認できませんでした", LabelVerdict.describe("SIM 2", LabelVerdict.Result.UNKNOWN, null));
    }
}
