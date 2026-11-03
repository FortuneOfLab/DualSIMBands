package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class BandwidthInferenceTest {
    private static final int PRIMARY = 1, SECONDARY = 2;

    private static CaAssessment ca(int[] reported, int... lteBandwidths) {
        Snapshots s = new Snapshots();
        s.service = Snapshots.service(Rat.LTE, 100, reported);
        boolean first = true;
        int[] earfcns = {100, 42000, 41802, 1500};
        for (int i = 0; i < lteBandwidths.length; i++) {
            s.lteCell(first, first ? PRIMARY : SECONDARY, earfcns[i], lteBandwidths[i]);
            first = false;
        }
        s.nrCell(false, SECONDARY, 647328);
        return CaAnalyzer.analyze(s.build(), Snapshots.NOW, Snapshots.STALE_AFTER);
    }

    /** Pixel 10a: ServiceState [20,20,20,100] with three verified 20 MHz LTE carriers → NR 100 MHz. */
    @Test public void leftoverBandwidthIsTheNrLeg() {
        assertEquals(100000, BandwidthInference.nrBandwidthKhz(ca(new int[]{20000, 20000, 20000, 100000}, 20000, 20000, 20000)));
        assertEquals("order does not matter", 100000,
                BandwidthInference.nrBandwidthKhz(ca(new int[]{100000, 20000, 20000, 20000}, 20000, 20000, 20000)));
    }

    @Test public void ambiguousOrInconsistentListsInferNothing() {
        assertEquals("two leftovers", -1, BandwidthInference.nrBandwidthKhz(ca(new int[]{20000, 20000, 100000, 40000}, 20000, 20000)));
        assertEquals("nothing left", -1, BandwidthInference.nrBandwidthKhz(ca(new int[]{20000, 20000}, 20000, 20000)));
        assertEquals("LTE value missing from the list", -1,
                BandwidthInference.nrBandwidthKhz(ca(new int[]{15000, 100000}, 20000)));
        assertEquals("unknown LTE bandwidth", -1, BandwidthInference.nrBandwidthKhz(ca(new int[]{20000, 100000}, 0)));
        assertEquals("no reported list", -1, BandwidthInference.nrBandwidthKhz(ca(new int[0], 20000)));
    }
}
