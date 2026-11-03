package io.github.fortuneoflab.dualsimbands.radio;

import java.util.List;

/**
 * Theoretical downlink peak rate of a carrier composition, an estimate under fixed assumptions that the
 * platform cannot report: 256QAM, 4 MIMO layers above 1 GHz and 2 below, NR subcarrier spacing 30 kHz for
 * TDD and 15 kHz for FDD (120 kHz in FR2), and 75 % of the time on the downlink for TDD. Real throughput is
 * usually a fraction of it.
 */
public final class PeakRate {
    private PeakRate() {}

    /** One component carrier; {@code tdd} from the band, {@code downlinkKhz} only to choose the layer count. */
    public static final class Carrier {
        final Rat rat;
        final int bandwidthKhz;
        final boolean tdd;
        final long downlinkKhz;
        final boolean fr2;
        public Carrier(Rat rat, int bandwidthKhz, boolean tdd, long downlinkKhz, boolean fr2) {
            this.rat = rat; this.bandwidthKhz = bandwidthKhz; this.tdd = tdd; this.downlinkKhz = downlinkKhz; this.fr2 = fr2;
        }
    }

    static final double TDD_DOWNLINK_SHARE = 0.75;
    /**
     * LTE 256QAM TBS per layer per resource block, from the 391.7 Mbit/s of 20 MHz, 4 layers (TS 36.213). Other
     * bandwidths and layer counts are scaled from it, an approximation of the TBS tables (said so in the Terms text).
     */
    static final double LTE_MBPS_PER_RB_LAYER = 391.656 / 100 / 4;

    /** @return Mbit/s, or null when any carrier's bandwidth has no resource-block count to compute with */
    public static Double mbps(List<Carrier> carriers) {
        if (carriers == null || carriers.isEmpty()) return null;
        double sum = 0;
        for (Carrier c : carriers) {
            Double r = c.rat == Rat.LTE ? lte(c) : c.rat == Rat.NR ? nr(c) : null;
            if (r == null) return null;
            sum += r;
        }
        return sum;
    }

    static int layers(Carrier c) { return c.downlinkKhz > 0 && c.downlinkKhz < 1_000_000 ? 2 : 4; }

    static Double lte(Carrier c) {
        int rb;
        switch (c.bandwidthKhz) {
            case 1400: rb = 6; break;
            case 3000: rb = 15; break;
            case 5000: rb = 25; break;
            case 10000: rb = 50; break;
            case 15000: rb = 75; break;
            case 20000: rb = 100; break;
            default: return null;
        }
        double r = LTE_MBPS_PER_RB_LAYER * rb * layers(c);
        return c.tdd ? r * TDD_DOWNLINK_SHARE : r;
    }

    /** TS 38.306 4.1.2 with f = 1, Rmax = 948/1024 and the FR1/FR2 downlink overheads 0.14/0.18. */
    static Double nr(Carrier c) {
        int scsKhz = c.fr2 ? 120 : c.tdd ? 30 : 15;
        Integer prb = nrPrb(scsKhz, c.bandwidthKhz / 1000);
        if (prb == null || c.bandwidthKhz % 1000 != 0) return null;
        int mu = Integer.numberOfTrailingZeros(scsKhz / 15);
        double symbolSeconds = 1e-3 / (14 * (1 << mu));
        double overhead = c.fr2 ? 0.18 : 0.14;
        // Assumed two layers for mmWave, as the Terms text says (handsets commonly take two there).
        int layers = c.fr2 ? 2 : layers(c);
        double r = 1e-6 * layers * 8 * (948.0 / 1024) * (prb * 12) / symbolSeconds * (1 - overhead);
        return c.tdd ? r * TDD_DOWNLINK_SHARE : r;
    }

    /** Maximum transmission bandwidth N_RB (TS 38.101-1 Table 5.3.2-1, TS 38.101-2 Table 5.3.2-1). */
    static Integer nrPrb(int scsKhz, int mhz) {
        int[][] table;
        if (scsKhz == 15) table = new int[][]{{5, 25}, {10, 52}, {15, 79}, {20, 106}, {25, 133}, {30, 160},
                {35, 188}, {40, 216}, {45, 242}, {50, 270}};
        else if (scsKhz == 30) table = new int[][]{{5, 11}, {10, 24}, {15, 38}, {20, 51}, {25, 65}, {30, 78},
                {35, 92}, {40, 106}, {45, 119}, {50, 133}, {60, 162}, {70, 189}, {80, 217}, {90, 245}, {100, 273}};
        else if (scsKhz == 120) table = new int[][]{{50, 32}, {100, 66}, {200, 132}, {400, 264}};
        else return null;
        for (int[] row : table) if (row[0] == mhz) return row[1];
        return null;
    }
}
