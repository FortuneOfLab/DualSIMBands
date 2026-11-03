package io.github.fortuneoflab.dualsimbands.radio;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PeakRateTest {
    private static PeakRate.Carrier lte(int khz, boolean tdd, long dl) { return new PeakRate.Carrier(Rat.LTE, khz, tdd, dl, false); }
    private static PeakRate.Carrier nr(int khz, boolean tdd, long dl) { return new PeakRate.Carrier(Rat.NR, khz, tdd, dl, false); }

    @Test public void lte20MhzFourLayersIsTheCategoryPeak() {
        assertEquals(391.7, PeakRate.mbps(Collections.singletonList(lte(20000, false, 2_120_000))), 0.1);
    }

    @Test public void lowBandUsesTwoLayers() {
        // B19 (875 MHz), 15 MHz: 75 RB x 2 layers.
        assertEquals(146.9, PeakRate.mbps(Collections.singletonList(lte(15000, false, 882_500))), 0.1);
    }

    @Test public void lteTddCountsOnlyTheDownlinkShare() {
        assertEquals(391.7 * 0.75, PeakRate.mbps(Collections.singletonList(lte(20000, true, 3_450_500))), 0.1);
    }

    /** TS 38.306 example: 100 MHz, 30 kHz, 4 layers, 256QAM is about 2.34 Gbit/s before the TDD share. */
    @Test public void nr100MhzTdd() {
        double fdd = 1e-6 * 4 * 8 * (948.0 / 1024) * (273 * 12) / (1e-3 / 28) * 0.86;
        assertEquals(2337, fdd, 1);
        assertEquals(fdd * 0.75, PeakRate.mbps(Collections.singletonList(nr(100000, true, 3_709_920))), 0.1);
    }

    @Test public void nrLowBandFddUses15KhzAndTwoLayers() {
        double expected = 1e-6 * 2 * 8 * (948.0 / 1024) * (52 * 12) / (1e-3 / 14) * 0.86;
        assertEquals(expected, PeakRate.mbps(Collections.singletonList(nr(10000, false, 777_850))), 0.1);
    }

    /** n257 (28 GHz), 100 MHz: 120 kHz, 66 PRB, two layers and the FR2 overhead, then the TDD share. */
    @Test public void nrMmWaveUses120KhzAndTwoLayers() {
        double expected = 1e-6 * 2 * 8 * (948.0 / 1024) * (66 * 12) / (1e-3 / 112) * 0.82 * 0.75;
        assertEquals(expected, PeakRate.mbps(Collections.singletonList(
                new PeakRate.Carrier(Rat.NR, 100000, true, 27_925_000, true))), 0.1);
    }

    @Test public void compositionIsTheSum() {
        double b1 = PeakRate.mbps(Collections.singletonList(lte(20000, false, 2_120_000)));
        double n77 = PeakRate.mbps(Collections.singletonList(nr(100000, true, 3_709_920)));
        assertEquals(b1 + n77, PeakRate.mbps(Arrays.asList(lte(20000, false, 2_120_000), nr(100000, true, 3_709_920))), 0.01);
    }

    @Test public void unknownBandwidthGivesNoEstimate() {
        assertNull(PeakRate.mbps(Arrays.asList(lte(20000, false, 2_120_000), lte(12000, false, 1_870_000))));
        assertNull(PeakRate.mbps(Collections.singletonList(nr(-1, true, 3_709_920))));
        assertNull(PeakRate.mbps(Collections.emptyList()));
    }
}
