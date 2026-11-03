package io.github.fortuneoflab.dualsimbands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameFiltersTest {
    private static final int BOOT = 40;

    // --- the name the status bar has to show ---

    @Test public void aCustomNameIsWanted() {
        assertEquals("au", NameFilters.wanted(true, true, "au"));
    }

    /** povo without a custom name: its own "povo" override is shown as "KDDI - povo", so nothing is wanted. */
    @Test public void theCarriersOwnOverrideIsNotWanted() {
        assertNull(NameFilters.wanted(false, true, "povo"));
        assertTrue(NameFilters.shownRight("KDDI - povo", NameFilters.wanted(false, true, "povo"),
                Collections.singletonList("au"), "KDDI"));
    }

    @Test public void noOverrideNoWantedName() {
        assertNull(NameFilters.wanted(true, false, ""));
        assertNull(NameFilters.wanted(false, false, ""));
    }

    // --- marks of network name filter changes ---

    @Test(expected = JSONException.class)
    public void brokenMarksCannotBeRead() throws Exception {
        NameFilters.parse("{broken");
    }

    @Test public void noMarksIsEmpty() throws Exception {
        assertEquals(0, NameFilters.parse(null).length());
        assertEquals(0, NameFilters.parse("").length());
    }

    /** A mark not put back yet keeps the carrier's own value: a later one would hold the shortening filter. */
    @Test public void firstMarkKeepsTheOriginal() throws Exception {
        JSONObject marks = new JSONObject();
        NameFilters.mark(marks, 5, "", BOOT);
        NameFilters.mark(marks, 5, "^(.+).$", BOOT);
        assertTrue(NameFilters.marked(marks, 5));
        List<NameFilters.Mark> left = NameFilters.toSettle(marks, BOOT);
        assertEquals(1, left.size());
        assertEquals("", left.get(0).original);
        NameFilters.unmark(marks, 5);
        assertFalse(NameFilters.marked(marks, 5));
    }

    /** A reboot cleared the override and the filter Android kept: an old mark is dropped, not written back. */
    @Test public void marksFromAnotherBootAreDropped() throws Exception {
        JSONObject marks = new JSONObject();
        NameFilters.mark(marks, 5, "", BOOT - 1);
        NameFilters.mark(marks, 4, "x", BOOT);
        List<NameFilters.Mark> left = NameFilters.toSettle(marks, BOOT);
        assertEquals(1, left.size());
        assertEquals(4, left.get(0).id);
        assertFalse(NameFilters.marked(marks, 5));
    }

    @Test public void unknownBootSettlesAndDropsNothing() throws Exception {
        JSONObject marks = new JSONObject();
        NameFilters.mark(marks, 5, "", BOOT);
        assertTrue(NameFilters.toSettle(marks, -1).isEmpty());
        assertTrue(NameFilters.marked(marks, 5));
    }

    /** Entries that cannot be read are never written back; they are dated and kept for this boot. */
    @Test public void unreadableEntriesAreDatedNotSettled() throws Exception {
        JSONObject marks = new JSONObject().put("abc", new JSONObject().put("original", "").put("boot", BOOT))
                .put("5", "not an object").put("4", new JSONObject().put("boot", BOOT))
                .put("3", new JSONObject().put("original", "").put("boot", "invalid"))
                .put("2", new JSONObject().put("original", new JSONObject().put("x", 1)).put("boot", BOOT))
                .put("1", new JSONObject().put("original", "").put("boot", -1));
        assertTrue(NameFilters.toSettle(marks, BOOT).isEmpty());
        assertEquals(6, marks.length());
        for (String key : new String[] {"abc", "5", "4", "3", "2", "1"})
            assertEquals(key, BOOT, marks.getJSONObject(key).getInt("seen"));
        assertTrue("still blocks new changes in this boot", NameFilters.marked(marks, 5));
    }

    /** The bare value an earlier build kept ({"5":""}) blocks only until the next reboot. */
    @Test public void earlierBuildsMarkIsDroppedAfterAReboot() throws Exception {
        JSONObject marks = new JSONObject().put("5", "");
        assertTrue(NameFilters.toSettle(marks, BOOT).isEmpty());
        assertTrue(NameFilters.marked(marks, 5));
        assertTrue("same boot: kept", NameFilters.toSettle(marks, BOOT).isEmpty());
        assertTrue(NameFilters.marked(marks, 5));
        assertTrue(NameFilters.toSettle(marks, BOOT + 1).isEmpty());
        assertFalse("rebooted: dropped", NameFilters.marked(marks, 5));
    }

    /** A mark cannot be made without the boot count, so no filter change starts. */
    @Test public void noMarkWithoutTheBootCount() throws Exception {
        JSONObject marks = new JSONObject();
        assertFalse(NameFilters.mark(marks, 5, "", -1));
        assertFalse(NameFilters.marked(marks, 5));
        assertTrue(NameFilters.mark(marks, 5, "", BOOT));
    }

    @Test public void countAcceptsOnlyNonNegativeWholeNumbers() throws Exception {
        assertEquals(40, NameFilters.count(new JSONObject().put("boot", 40), "boot"));
        assertEquals(40, NameFilters.count(new JSONObject().put("boot", 40L), "boot"));
        assertEquals(-1, NameFilters.count(new JSONObject().put("boot", -3), "boot"));
        assertEquals(-1, NameFilters.count(new JSONObject().put("boot", "40"), "boot"));
        assertEquals(-1, NameFilters.count(new JSONObject().put("boot", 40.5), "boot"));
        assertEquals(-1, NameFilters.count(new JSONObject(), "boot"));
        assertEquals(-1, NameFilters.count(null, "boot"));
    }

    // --- the whole record unreadable ---

    @Test public void unreadableRecordIsDatedKeptThenReset() {
        assertEquals(NameFilters.Broken.DATE, NameFilters.broken(-1, BOOT));
        assertEquals(NameFilters.Broken.KEEP, NameFilters.broken(BOOT, BOOT));
        assertEquals(NameFilters.Broken.RESET, NameFilters.broken(BOOT, BOOT + 1));
        assertEquals("boot count unknown", NameFilters.Broken.WAIT, NameFilters.broken(BOOT, -1));
        assertEquals(NameFilters.Broken.WAIT, NameFilters.broken(-1, -1));
    }

    /**
     * Broken in boot 40, readable again (the date is removed then), broken again in boot 41: the new breakage is
     * dated 41 and kept, not reset at once by the old 40.
     */
    @Test public void aLaterBreakageIsDatedAfresh() {
        int seen = -1;
        assertEquals(NameFilters.Broken.DATE, NameFilters.broken(seen, BOOT));
        seen = BOOT;
        seen = -1; // the record read again: the date is removed
        assertEquals(NameFilters.Broken.DATE, NameFilters.broken(seen, BOOT + 1));
        seen = BOOT + 1;
        assertEquals(NameFilters.Broken.KEEP, NameFilters.broken(seen, BOOT + 1));
        assertEquals(NameFilters.Broken.RESET, NameFilters.broken(seen, BOOT + 2));
    }

    // --- names this app may have left shown ---

    /** "A" applied, then "B" whose write may not take effect: both can be shown until "B" is seen. */
    @Test public void earlierNameStaysUntilTheNewOneIsSeen() throws Exception {
        JSONObject entry = new JSONObject();
        NameFilters.addApplied(entry, "A");
        NameFilters.addApplied(entry, "B");
        assertEquals(Arrays.asList("A", "B"), NameFilters.applied(entry));
        assertTrue(NameFilters.confirmApplied(entry, "B"));
        assertEquals(Collections.singletonList("B"), NameFilters.applied(entry));
        assertFalse("already only B", NameFilters.confirmApplied(entry, "B"));
    }

    /** The network's name put in by replaceStaleName is not one of this app's names. */
    @Test public void confirmingAnotherNameChangesNothing() throws Exception {
        JSONObject entry = new JSONObject();
        NameFilters.addApplied(entry, "A");
        NameFilters.addApplied(entry, "B");
        assertFalse(NameFilters.confirmApplied(entry, "docomo"));
        assertEquals(2, NameFilters.applied(entry).size());
    }

    @Test public void singleNameOfAnEarlierBuildIsRead() throws Exception {
        assertEquals(Collections.singletonList("SoftBank"), NameFilters.applied(new JSONObject().put("applied", "SoftBank")));
        assertTrue(NameFilters.applied(null).isEmpty());
        assertTrue(NameFilters.applied(new JSONObject().put("applied", new JSONArray().put(""))).isEmpty());
    }

    // --- whether the status bar shows the right name ---

    /** After a reboot the override says "SoftBank", the status bar still the SIM's own "LINEMO". */
    @Test public void customNameNotShownYet() {
        assertFalse(NameFilters.shownRight("LINEMO", "SoftBank", Collections.emptyList(), "LINEMO"));
        assertTrue(NameFilters.shownRight("SoftBank", "SoftBank", Collections.emptyList(), "LINEMO"));
    }

    /** Renamed "A", then "B", then turned off: either can stay. */
    @Test public void anyOfThisLinesNamesIsOldAfterTurningOff() {
        List<String> applied = Arrays.asList("A", "B");
        assertFalse(NameFilters.shownRight("A", null, applied, "LINEMO"));
        assertFalse(NameFilters.shownRight("B", null, applied, "LINEMO"));
        assertTrue(NameFilters.shownRight("LINEMO", null, applied, "LINEMO"));
    }

    /** Another SIM's custom name equal to this SIM's own name does not count: only this line's names do. */
    @Test public void otherLinesNamesDoNotCount() {
        assertTrue(NameFilters.shownRight("IIJmio", null, Collections.emptyList(), "docomo"));
    }

    /** A custom name equal to the network's name cannot be told apart from the line's own name. */
    @Test public void nameEqualToTheNetworkIsRight() {
        assertTrue(NameFilters.shownRight("docomo", null, Collections.singletonList("docomo"), "docomo"));
    }

    /** replaceStaleName put the network's name in the override: that is the name to show. */
    @Test public void networkNameInTheOverrideIsWanted() {
        assertFalse(NameFilters.shownRight("SoftBank", "docomo", Collections.emptyList(), "docomo"));
    }

    /** The SIM could not be read: an empty name is never taken as the right one. */
    @Test public void emptyNameIsNeverRight() {
        assertFalse(NameFilters.shownRight("", null, Collections.emptyList(), "docomo"));
        assertFalse(NameFilters.shownRight(null, "SoftBank", Collections.emptyList(), "LINEMO"));
    }

    /** Another line's name in the carrier name counts only while the SIM's own name reads and differs. */
    @Test public void leftOverNeedsTheSimsOwnName() {
        assertEquals("au", NameFilters.leftOver("au", "docomo"));
        assertNull("unread SIM name", NameFilters.leftOver("au", ""));
        assertNull(NameFilters.leftOver("au", null));
        assertNull("the SIM's own name", NameFilters.leftOver("au", "au"));
        assertNull("nothing stale", NameFilters.leftOver(null, "docomo"));
    }

    /** A name possibly left over is not right until worked out again; the line's own judgement is kept apart. */
    @Test public void leftOverIsNotRightUntilWorkedOutAgain() {
        List<String> none = Collections.emptyList();
        assertFalse(NameFilters.shownRight("au", null, none, "au", "docomo"));
        // Still there after working it out again: by the line's own record it is right, so it is taken as its own.
        assertTrue(NameFilters.shownRight("au", null, none, "docomo"));
        assertTrue(NameFilters.shownRight("docomo", null, none, "au", "docomo"));
        assertTrue("the network's name", NameFilters.shownRight("au", null, none, "au", "au"));
        assertTrue("no candidate", NameFilters.shownRight("au", null, none, null, "docomo"));
        assertTrue("an override decides", NameFilters.shownRight("au", "au", none, "au", "docomo"));
        assertFalse("a name put on this line", NameFilters.shownRight("A", null, Arrays.asList("A"), null, "docomo"));
    }
}
