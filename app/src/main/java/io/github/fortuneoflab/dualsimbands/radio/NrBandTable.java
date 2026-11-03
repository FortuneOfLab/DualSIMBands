package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NR operating bands with a downlink, 3GPP TS 38.104 Table 5.2-1 (FR1) and 5.2-2 (FR2).
 * Uplink-only (SUL) bands are omitted because a downlink NR-ARFCN cannot belong to them.
 * Downlink ranges were cross-checked against AOSP AccessNetworkUtils NR-ARFCN ranges (2026-10).
 */
public final class NrBandTable {
    public static final String SOURCE = "3GPP TS 38.104 Table 5.2-1 / 5.2-2";

    public static final class Band {
        public final int number;
        public final Duplex duplex;
        public final long dlLowKhz, dlHighKhz;
        Band(int number, Duplex duplex, long dlLowKhz, long dlHighKhz) {
            this.number = number; this.duplex = duplex; this.dlLowKhz = dlLowKhz; this.dlHighKhz = dlHighKhz;
        }
        public boolean contains(long khz) { return khz >= dlLowKhz && khz <= dlHighKhz; }
    }

    private static final List<Band> BANDS;
    static {
        List<Band> b = new ArrayList<>();
        add(b, 1, Duplex.FDD, 2110, 2170);
        add(b, 2, Duplex.FDD, 1930, 1990);
        add(b, 3, Duplex.FDD, 1805, 1880);
        add(b, 5, Duplex.FDD, 869, 894);
        add(b, 7, Duplex.FDD, 2620, 2690);
        add(b, 8, Duplex.FDD, 925, 960);
        add(b, 12, Duplex.FDD, 729, 746);
        add(b, 13, Duplex.FDD, 746, 756);
        add(b, 14, Duplex.FDD, 758, 768);
        add(b, 18, Duplex.FDD, 860, 875);
        add(b, 20, Duplex.FDD, 791, 821);
        addKhz(b, 24, Duplex.FDD, 1525000, 1559000);
        add(b, 25, Duplex.FDD, 1930, 1995);
        add(b, 26, Duplex.FDD, 859, 894);
        add(b, 28, Duplex.FDD, 758, 803);
        add(b, 29, Duplex.SDL, 717, 728);
        add(b, 30, Duplex.FDD, 2350, 2360);
        add(b, 34, Duplex.TDD, 2010, 2025);
        add(b, 38, Duplex.TDD, 2570, 2620);
        add(b, 39, Duplex.TDD, 1880, 1920);
        add(b, 40, Duplex.TDD, 2300, 2400);
        add(b, 41, Duplex.TDD, 2496, 2690);
        add(b, 46, Duplex.TDD, 5150, 5925);
        add(b, 48, Duplex.TDD, 3550, 3700);
        add(b, 50, Duplex.TDD, 1432, 1517);
        add(b, 51, Duplex.TDD, 1427, 1432);
        addKhz(b, 53, Duplex.TDD, 2483500, 2495000);
        add(b, 65, Duplex.FDD, 2110, 2200);
        add(b, 66, Duplex.FDD, 2110, 2200);
        add(b, 67, Duplex.SDL, 738, 758);
        add(b, 70, Duplex.FDD, 1995, 2020);
        add(b, 71, Duplex.FDD, 617, 652);
        add(b, 74, Duplex.FDD, 1475, 1518);
        add(b, 75, Duplex.SDL, 1432, 1517);
        add(b, 76, Duplex.SDL, 1427, 1432);
        add(b, 77, Duplex.TDD, 3300, 4200);
        add(b, 78, Duplex.TDD, 3300, 3800);
        add(b, 79, Duplex.TDD, 4400, 5000);
        add(b, 90, Duplex.TDD, 2496, 2690);
        add(b, 91, Duplex.FDD, 1427, 1432);
        add(b, 92, Duplex.FDD, 1432, 1517);
        add(b, 93, Duplex.FDD, 1427, 1432);
        add(b, 94, Duplex.FDD, 1432, 1517);
        add(b, 96, Duplex.TDD, 5925, 7125);
        add(b, 257, Duplex.TDD, 26500, 29500);
        add(b, 258, Duplex.TDD, 24250, 27500);
        add(b, 259, Duplex.TDD, 39500, 43500);
        add(b, 260, Duplex.TDD, 37000, 40000);
        add(b, 261, Duplex.TDD, 27500, 28350);
        BANDS = Collections.unmodifiableList(b);
    }

    private NrBandTable() {}

    public static List<Band> all() { return BANDS; }

    public static Band byNumber(int number) {
        for (Band band : BANDS) if (band.number == number) return band;
        return null;
    }

    /** Every band whose downlink range contains the frequency; several bands may overlap. */
    public static int[] candidates(long khz) {
        List<Integer> out = new ArrayList<>();
        for (Band band : BANDS) if (band.contains(khz)) out.add(band.number);
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    private static void add(List<Band> b, int n, Duplex d, long lowMhz, long highMhz) {
        b.add(new Band(n, d, lowMhz * 1000, highMhz * 1000));
    }
    private static void addKhz(List<Band> b, int n, Duplex d, long lowKhz, long highKhz) {
        b.add(new Band(n, d, lowKhz, highKhz));
    }
}
