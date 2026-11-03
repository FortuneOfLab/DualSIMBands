package io.github.fortuneoflab.dualsimbands.ui;

import org.junit.Test;
import static org.junit.Assert.*;

/** A SIM card's title: Android's SIM label, or the carrier name when there is none. */
public class SimLookTest {
    @Test public void theLabelAlone() {
        assertEquals("povo", SimLook.name("povo", "au"));
        assertEquals("IIJmio(au)", SimLook.name("IIJmio(au)", "au"));
    }

    @Test public void theCarrierNameWithoutALabel() {
        assertEquals("au", SimLook.name("", "au"));
        assertEquals("au", SimLook.name("  ", " au "));
    }

    @Test public void bothEmptyGiveNothing() {
        assertEquals("", SimLook.name("", ""));
    }

    @Test public void spacesAroundTheLabelAreDropped() {
        assertEquals("docomo", SimLook.name("docomo ", "au"));
    }

    /** A % in the label is shown as it is. */
    @Test public void percentIsKept() {
        assertEquals("100% SIM", SimLook.name("100% SIM", "au"));
    }

    @Test public void theSameNameIsShownOnce() {
        assertTrue(SimLook.sameName("docomo", "docomo"));
        assertTrue(SimLook.sameName("docomo ", "Docomo"));
        assertFalse(SimLook.sameName("povo", "au"));
        assertFalse(SimLook.sameName("au", ""));
    }
}
