package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class BandMemoryTest {
    @Test public void learnsOnlyReportedBandsThatResolveAnAmbiguity() {
        assertTrue(BandMemory.shouldLearn(FrequencyCalculator.nr(647328, new int[]{77})));      // n77 vs n78
        assertFalse("no report", BandMemory.shouldLearn(FrequencyCalculator.nr(647328, null)));
        assertFalse("not ambiguous", BandMemory.shouldLearn(FrequencyCalculator.nr(720000, new int[]{79})));
        assertFalse("contradicts frequency", BandMemory.shouldLearn(FrequencyCalculator.nr(647328, new int[]{79})));
        assertFalse("two reported bands", BandMemory.shouldLearn(FrequencyCalculator.nr(647328, new int[]{77, 78})));
    }

    @Test public void appliesOnlyToUnresolvedChannelsAndValidCandidates() {
        NrChannel unresolved = FrequencyCalculator.nr(647328, null);
        assertEquals(77, BandMemory.usable(unresolved, 77));
        assertEquals("not a candidate for this frequency", 0, BandMemory.usable(unresolved, 79));
        assertEquals("nothing remembered", 0, BandMemory.usable(unresolved, 0));
        assertEquals("already reported now", 0, BandMemory.usable(FrequencyCalculator.nr(647328, new int[]{78}), 77));
    }

    @Test public void keyIsPerCarrierAndChannel() {
        assertEquals("nr:44051:647328", BandMemory.key("440", "51", 647328));
        assertNotEquals(BandMemory.key("440", "51", 647328), BandMemory.key("440", "10", 647328));
    }
}
