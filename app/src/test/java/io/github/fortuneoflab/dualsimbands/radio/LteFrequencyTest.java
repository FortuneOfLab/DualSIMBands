package io.github.fortuneoflab.dualsimbands.radio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class LteFrequencyTest {
    private static LteChannel lte(int earfcn) { return FrequencyCalculator.lte(earfcn, null); }

    @Test public void band1Earfcn100Is2120MHz() {
        LteChannel c = lte(100);
        assertEquals(1, c.band);
        assertEquals("2120.0", Mhz.format(c.downlinkKhz));
        assertEquals(Duplex.FDD, c.duplex);
        assertEquals("1930.0", Mhz.format(c.uplinkKhz));
        assertEquals(LteChannel.Uplink.DERIVED_DEFAULT_SEPARATION, c.uplink);
        assertEquals(Provenance.DERIVED, c.bandProvenance);
    }

    @Test public void band21Earfcn6525Is1503_4MHz() {
        LteChannel c = lte(6525);
        assertEquals(21, c.band);
        assertEquals("1503.4", Mhz.format(c.downlinkKhz));
        assertEquals("1455.4", Mhz.format(c.uplinkKhz));
    }

    @Test public void band28Earfcn9510Is788MHz() {
        assertEquals(28, lte(9510).band);
        assertEquals("788.0", Mhz.format(lte(9510).downlinkKhz));
    }

    @Test public void bandBoundaries() {
        assertEquals("2110.0", Mhz.format(lte(0).downlinkKhz));
        assertEquals(1, lte(599).band);
        assertEquals("2169.9", Mhz.format(lte(599).downlinkKhz));
        assertEquals(2, lte(600).band);
        assertEquals("1930.0", Mhz.format(lte(600).downlinkKhz));
        assertEquals("1844.9", Mhz.format(lte(3800).downlinkKhz));
        assertEquals(88, lte(70645).band);
        assertEquals("426.9", Mhz.format(lte(70645).downlinkKhz));
        assertEquals("416.9", Mhz.format(lte(70645).uplinkKhz));
    }

    @Test public void reverseDuplexBand13HasUplinkAboveDownlink() {
        LteChannel c = lte(5230);
        assertEquals("751.0", Mhz.format(c.downlinkKhz));
        assertEquals("782.0", Mhz.format(c.uplinkKhz));
    }

    @Test public void tddSharesTheCarrierAndDoesNotFabricateAnUplinkChannel() {
        LteChannel b42 = lte(41590);
        assertEquals(42, b42.band);
        assertEquals(Duplex.TDD, b42.duplex);
        assertEquals("3400.0", Mhz.format(b42.downlinkKhz));
        assertEquals(b42.downlinkKhz, b42.uplinkKhz);
        assertEquals(LteChannel.Uplink.TDD_SHARED, b42.uplink);
        assertEquals("3599.9", Mhz.format(lte(43589).downlinkKhz));
        assertEquals("2496.0", Mhz.format(lte(39650).downlinkKhz));
        assertEquals("1900.0", Mhz.format(lte(36000).downlinkKhz));
    }

    @Test public void supplementalDownlinkHasNoUplink() {
        LteChannel c = lte(9660);
        assertEquals(29, c.band);
        assertEquals("717.0", Mhz.format(c.downlinkKhz));
        assertEquals(-1, c.uplinkKhz);
        assertEquals(LteChannel.Uplink.NONE_SDL, c.uplink);
    }

    @Test public void variableSeparationBandDoesNotGuessUplink() {
        LteChannel c = lte(67236);
        assertEquals(66, c.band);
        assertEquals("2190.0", Mhz.format(c.downlinkKhz));
        assertEquals(-1, c.uplinkKhz);
        assertEquals(LteChannel.Uplink.UNKNOWN, c.uplink);
    }

    @Test public void overlappingFrequencyBandsUseTheEarfcnNumberingAndFlagContradictions() {
        // B1 and B65 cover the same spectrum but have separate EARFCN ranges.
        assertEquals("2110.0", Mhz.format(lte(65536).downlinkKhz));
        assertEquals(65, lte(65536).band);
        LteChannel confirmed = FrequencyCalculator.lte(100, new int[]{1, 65});
        assertEquals(Provenance.VERIFIED, confirmed.bandProvenance);
        assertFalse(confirmed.bandMismatch);
        LteChannel contradicted = FrequencyCalculator.lte(65536, new int[]{1});
        assertTrue(contradicted.bandMismatch);
        assertEquals(Provenance.DERIVED, contradicted.bandProvenance);
        assertEquals(65, contradicted.band);
    }

    @Test public void unknownAndUnavailableEarfcn() {
        for (int bad : new int[]{-1, Integer.MAX_VALUE, LteBandTable.MAX_EARFCN + 1}) {
            LteChannel c = lte(bad);
            assertEquals(-1, c.earfcn);
            assertFalse(c.hasFrequency());
            assertEquals(Provenance.UNKNOWN, c.bandProvenance);
        }
        LteChannel gap = lte(10360);
        assertEquals(10360, gap.earfcn);
        assertEquals(0, gap.band);
        assertFalse(gap.hasFrequency());
        assertFalse(lte(LteBandTable.MAX_EARFCN).hasFrequency());
        LteChannel sentinelBands = FrequencyCalculator.lte(100, new int[]{Integer.MAX_VALUE, 0});
        assertEquals(0, sentinelBands.reportedBands.length);
        assertEquals(Provenance.DERIVED, sentinelBands.bandProvenance);
    }

    @Test public void tableIsInternallyConsistent() {
        Set<Integer> seen = new HashSet<>();
        LteBand previous = null;
        for (LteBand b : LteBandTable.all()) {
            assertTrue("duplicate band " + b.number, seen.add(b.number));
            assertTrue(b.dlLast >= b.dlFirst);
            if (previous != null) assertTrue("EARFCN ranges overlap at B" + b.number, b.dlFirst > previous.dlLast);
            if (b.duplex == Duplex.FDD && b.number != 66 && b.number != 70) {
                assertEquals("B" + b.number + " paired widths", b.dlLast - b.dlFirst, b.ulLast - b.ulFirst);
            }
            previous = b;
        }
        assertFalse(LteBandTable.byNumber(66).hasFixedPairing());
        assertFalse(LteBandTable.byNumber(70).hasFixedPairing());
    }

    /**
     * Cross-check against AOSP. AOSP omits SDL bands and has three rows that contradict the
     * 36.101 rule N_Offs = first channel of the range; 3GPP is used for those.
     */
    @Test public void matchesAospTableExceptDocumentedErrata() throws Exception {
        Set<String> differences = new HashSet<>();
        int rows = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("aosp_eutran_2026-10.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] f = line.split(",");
                int band = Integer.parseInt(f[0]);
                LteBand mine = LteBandTable.byNumber(band);
                assertNotNull("missing B" + band, mine);
                rows++;
                long[] aosp = new long[6];
                for (int i = 0; i < 6; i++) aosp[i] = Long.parseLong(f[i + 1]);
                long[] ours = {mine.dlLowKhz, mine.dlFirst, mine.dlLast, mine.ulLowKhz, mine.ulFirst, mine.ulLast};
                String[] names = {"dlLow", "dlFirst", "dlLast", "ulLow", "ulFirst", "ulLast"};
                for (int i = 0; i < 6; i++) if (aosp[i] != ours[i]) differences.add("B" + band + "." + names[i]);
            }
        }
        assertEquals(60, rows);
        Set<String> errata = new HashSet<>();
        errata.add("B1.ulFirst");    // AOSP 18800 lies outside its own UL range 18000–18599
        errata.add("B14.ulFirst");   // AOSP 23230 lies below its own UL range 23280–23379
        errata.add("B88.ulFirst");   // AOSP 134231 overlaps B87's last UL channel
        errata.add("B88.ulLast");
        assertEquals(errata, differences);
    }
}
