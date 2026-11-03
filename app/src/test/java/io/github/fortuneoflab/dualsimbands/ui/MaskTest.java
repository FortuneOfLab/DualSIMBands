package io.github.fortuneoflab.dualsimbands.ui;

import org.junit.Test;
import static org.junit.Assert.*;

/** Cell ID, TAC and TA are masked, not left out, while the ID setting is off. */
public class MaskTest {
    @Test public void valuesAreMaskedWhileOff() {
        assertEquals("••••", RadioCard.masked(false, 168892676L));
        assertEquals("••••", RadioCard.masked(false, "9（約 700 m）"));
    }

    @Test public void valuesAreShownWhileOn() {
        assertEquals("12345", RadioCard.masked(true, 12345));
        assertEquals("9（約 700 m）", RadioCard.masked(true, "9（約 700 m）"));
    }

    @Test public void screenReadersHearHidden() {
        assertEquals("CI 非表示", RadioCard.spoken("CI ••••"));
        assertEquals("105\nTAC 非表示\nCI 非表示", RadioCard.spoken("105\nTAC ••••\nCI ••••"));
        assertEquals("unchanged without a mask", "TAC 12345", RadioCard.spoken("TAC 12345"));
    }
}
