package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class CellsFilterTest {
    private static final long NOW = Snapshots.NOW;

    private static RadioSnapshot.Cells at(long t) {
        return new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, new ArrayList<>(), new ArrayList<>(), t);
    }

    /** Seen on the Pixel 10a: the list handed over on registering was minutes to hours old. */
    @Test public void anOldListIsNotTaken() {
        assertFalse("nothing held yet", CellsFilter.take(RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.NOT_REQUESTED),
                at(NOW - 13_480_000), NOW));
        assertFalse("older than 30 s", CellsFilter.take(at(NOW - 40_000), at(NOW - 31_000), NOW));
    }

    @Test public void aListOlderThanTheOneHeldIsNotTaken() {
        assertFalse(CellsFilter.take(at(NOW - 2_000), at(NOW - 9_000), NOW));
    }

    @Test public void aCurrentListIsTaken() {
        assertTrue(CellsFilter.take(at(NOW - 9_000), at(NOW - 2_000), NOW));
        assertTrue(CellsFilter.take(RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.NOT_REQUESTED), at(NOW), NOW));
    }

    @Test public void aStatusChangeIsAlwaysTaken() {
        assertTrue(CellsFilter.take(at(NOW), RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.LOCATION_OFF), NOW));
    }
}
