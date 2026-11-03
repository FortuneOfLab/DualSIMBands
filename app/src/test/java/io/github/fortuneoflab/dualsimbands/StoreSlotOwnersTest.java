package io.github.fortuneoflab.dualsimbands;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Per slot, the line this app last wrote an override for in this boot. */
public class StoreSlotOwnersTest {
    @Test public void writtenSlotsAreKeptForThisBoot() throws Exception {
        JSONObject slots = new JSONObject();
        Store.setSlotOwner(slots, 0, 7);
        Store.setSlotOwner(slots, 1, 5);
        JSONObject read = Store.slotOwners(Store.slotOwnersValue(slots, 41), 41);
        assertEquals(7, Store.slotOwner(read, 0));
        assertEquals(5, Store.slotOwner(read, 1));
    }

    /** A reboot cleared every override: an earlier boot's record says nothing. */
    @Test public void anotherBootHasNoOwners() throws Exception {
        JSONObject slots = new JSONObject();
        Store.setSlotOwner(slots, 0, 7);
        String stored = Store.slotOwnersValue(slots, 41);
        assertEquals(-1, Store.slotOwner(Store.slotOwners(stored, 42), 0));
        assertEquals(-1, Store.slotOwner(Store.slotOwners(stored, -1), 0));
    }

    @Test public void noRecordOrAnUnreadableOneHasNoOwners() {
        assertEquals(-1, Store.slotOwner(Store.slotOwners(null, 41), 0));
        assertEquals(-1, Store.slotOwner(Store.slotOwners("not json", 41), 0));
        assertEquals(-1, Store.slotOwner(Store.slotOwners("{\"boot\":41}", 41), 0));
    }

    /** A cleared slot has no owner until this app writes there again. */
    @Test public void clearingForgetsTheOwner() throws Exception {
        JSONObject slots = new JSONObject();
        Store.setSlotOwner(slots, 1, 7);
        Store.setSlotOwner(slots, 1, -1);
        assertEquals(-1, Store.slotOwner(slots, 1));
    }

    /** povo written in slot 1, LINEMO found there later: the slot holds povo's override. */
    @Test public void anotherLineInTheSlotIsLeftOver() throws Exception {
        JSONObject slots = new JSONObject();
        Store.setSlotOwner(slots, 1, 7);
        int owner = Store.slotOwner(slots, 1);
        assertTrue(owner >= 0 && owner != 5);
        assertFalse(owner != 7);
    }

    /** Without a slot record, another line's pre-change record of this boot counts, unless this line has its own. */
    @Test public void withoutARecordTheOtherLinesBaselineCounts() {
        assertTrue(Store.leftOnSlot(-1, 5, false, true));
        assertFalse(Store.leftOnSlot(-1, 5, true, true));
        assertFalse(Store.leftOnSlot(-1, 5, false, false));
        assertTrue(Store.leftOnSlot(7, 5, true, false));
        assertFalse(Store.leftOnSlot(5, 5, false, true));
    }

    /**
     * A clearing not seen within the wait leaves the slot recorded for this line: the next apply does not clear
     * again (the old values can equal the carrier's own) and records the values read then.
     */
    @Test public void anUnconfirmedClearingIsNotRepeated() throws Exception {
        JSONObject slots = new JSONObject();
        Store.setSlotOwner(slots, 1, 7);
        Store.setSlotOwner(slots, 1, Store.ownerAfterClear(false, 5));
        assertFalse(Store.leftOnSlot(Store.slotOwner(slots, 1), 5, false, true));
        Store.setSlotOwner(slots, 1, Store.ownerAfterClear(true, 5));
        assertEquals(-1, Store.slotOwner(slots, 1));
    }
}
