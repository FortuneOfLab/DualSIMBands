package io.github.fortuneoflab.dualsimbands.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class NrFrequencyTest {
    private static String mhz(int arfcn) { return Mhz.format(NrRaster.frequencyKhz(arfcn)); }

    @Test public void globalRasterBoundaries() {
        assertEquals("0.0", mhz(0));
        assertEquals("2999.995", mhz(599999));    // 5 kHz raster ends
        assertEquals("3000.0", mhz(600000));      // 15 kHz raster starts
        assertEquals("24249.99", mhz(2016666));   // 15 kHz raster ends
        assertEquals("24250.08", mhz(2016667));   // 60 kHz raster starts at F_REF-Offs 24250.08 MHz
        assertEquals("99999.96", mhz(NrRaster.MAX_NR_ARFCN));
    }

    @Test public void stepSizeDependsOnFrequencyRange() {
        assertEquals(5, NrRaster.frequencyKhz(1001) - NrRaster.frequencyKhz(1000));
        assertEquals(15, NrRaster.frequencyKhz(700001) - NrRaster.frequencyKhz(700000));
        assertEquals(60, NrRaster.frequencyKhz(2100001) - NrRaster.frequencyKhz(2100000));
    }

    @Test public void invalidNrArfcn() {
        for (int bad : new int[]{-1, NrRaster.MAX_NR_ARFCN + 1, Integer.MAX_VALUE}) {
            NrChannel c = FrequencyCalculator.nr(bad, null);
            assertEquals(-1, c.nrArfcn);
            assertFalse(c.hasFrequency());
            assertEquals(0, c.band);
            assertEquals(Provenance.UNKNOWN, c.bandProvenance);
        }
    }

    @Test public void n77AndN78OverlapSoBandIsNotGuessed() {
        NrChannel c = FrequencyCalculator.nr(643334, null);
        assertEquals("3650.01", Mhz.format(c.frequencyKhz));
        assertEquals(1, c.frequencyRange);
        assertEquals(0, c.band);
        assertTrue(c.isBandAmbiguous());
        assertArrayEquals(new int[]{48, 77, 78}, c.candidateBands); // n48 (3550–3700) overlaps as well
        assertEquals(Provenance.UNKNOWN, c.bandProvenance);
    }

    @Test public void reportedBandIsPreferred() {
        NrChannel c = FrequencyCalculator.nr(643334, new int[]{78});
        assertEquals(78, c.band);
        assertEquals(Provenance.VERIFIED, c.bandProvenance);
        assertFalse(c.bandMismatch);
    }

    @Test public void reportedBandThatCannotContainTheFrequencyIsFlagged() {
        NrChannel c = FrequencyCalculator.nr(643334, new int[]{79});
        assertTrue(c.bandMismatch);
        assertEquals(0, c.band);
    }

    @Test public void uniqueBandIsDerived() {
        NrChannel c = FrequencyCalculator.nr(720000, null);
        assertEquals("4800.0", Mhz.format(c.frequencyKhz));
        assertEquals(79, c.band);
        assertEquals(Provenance.DERIVED, c.bandProvenance);
    }

    @Test public void otherOverlaps() {
        assertArrayEquals(new int[]{1, 65, 66}, FrequencyCalculator.nr(431000, null).candidateBands); // 2155 MHz
        assertArrayEquals(new int[]{41, 90}, FrequencyCalculator.nr(504000, null).candidateBands);    // 2520 MHz
        assertArrayEquals(new int[]{20, 28}, FrequencyCalculator.nr(158200, null).candidateBands);    // 791 MHz
    }

    @Test public void fr2() {
        NrChannel c = FrequencyCalculator.nr(2070833, null);
        assertEquals("27500.04", Mhz.format(c.frequencyKhz));
        assertEquals(2, c.frequencyRange);
        assertArrayEquals(new int[]{257, 261}, c.candidateBands);
    }

    /**
     * NR-ARFCN band ranges as published in AOSP AccessNetworkUtils (2026-10). For n41 AOSP stops at
     * 537999; 538000 is exactly the 2690 MHz band edge, so the edge itself is used here.
     */
    @Test public void bandEdgesMatchAospArfcnRanges() {
        int[][] ranges = {
                {1, 422000, 434000}, {3, 361000, 376000}, {8, 185000, 192000}, {28, 151600, 160600},
                {41, 499200, 538000}, {77, 620000, 680000}, {78, 620000, 653333}, {79, 693334, 733333},
                {257, 2054166, 2104165}, {258, 2016667, 2070832}, {260, 2229166, 2279165}, {261, 2070833, 2084999}};
        for (int[] r : ranges) {
            NrBandTable.Band band = NrBandTable.byNumber(r[0]);
            assertTrue("n" + r[0] + " first", band.contains(NrRaster.frequencyKhz(r[1])));
            assertTrue("n" + r[0] + " last", band.contains(NrRaster.frequencyKhz(r[2])));
            assertFalse("n" + r[0] + " below", band.contains(NrRaster.frequencyKhz(r[1] - 1)));
            assertFalse("n" + r[0] + " above", band.contains(NrRaster.frequencyKhz(r[2] + 1)));
        }
    }

    @Test public void mhzFormatting() {
        assertEquals("2120.0", Mhz.format(2120000));
        assertEquals("1503.4", Mhz.format(1503400));
        assertEquals("3650.01", Mhz.format(3650010));
        assertEquals("2999.995", Mhz.format(2999995));
    }
}
