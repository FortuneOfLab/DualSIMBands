package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * E-UTRA operating bands and channel numbers, 3GPP TS 36.101 Table 5.7.3-1.
 * Values cross-checked against AOSP AccessNetworkConstants.EutranBandArfcnFrequency
 * (2026-10). Three AOSP rows contradict their own ranges, and 3GPP (N_Offs = the range's first channel) is used
 * instead: B1 UL 18000-18599 (AOSP N_Offs 18800), B14 UL 23280-23379 (AOSP N_Offs 23230) and B88 UL
 * 134232-134281 (AOSP 134231-134280 overlaps the end of B87).
 */
public final class LteBandTable {
    public static final String SOURCE = "3GPP TS 36.101 Table 5.7.3-1";
    /** Highest valid EARFCN (CellIdentityLte.getEarfcn range). */
    public static final int MAX_EARFCN = 262143;

    private static final List<LteBand> BANDS;
    static {
        List<LteBand> b = new ArrayList<>();
        fdd(b, 1, 2110000, 0, 599, 1920000, 18000, 18599);
        fdd(b, 2, 1930000, 600, 1199, 1850000, 18600, 19199);
        fdd(b, 3, 1805000, 1200, 1949, 1710000, 19200, 19949);
        fdd(b, 4, 2110000, 1950, 2399, 1710000, 19950, 20399);
        fdd(b, 5, 869000, 2400, 2649, 824000, 20400, 20649);
        fdd(b, 6, 875000, 2650, 2749, 830000, 20650, 20749);
        fdd(b, 7, 2620000, 2750, 3449, 2500000, 20750, 21449);
        fdd(b, 8, 925000, 3450, 3799, 880000, 21450, 21799);
        fdd(b, 9, 1844900, 3800, 4149, 1749900, 21800, 22149);
        fdd(b, 10, 2110000, 4150, 4749, 1710000, 22150, 22749);
        fdd(b, 11, 1475900, 4750, 4949, 1427900, 22750, 22949);
        fdd(b, 12, 729000, 5010, 5179, 699000, 23010, 23179);
        fdd(b, 13, 746000, 5180, 5279, 777000, 23180, 23279);
        fdd(b, 14, 758000, 5280, 5379, 788000, 23280, 23379);
        fdd(b, 17, 734000, 5730, 5849, 704000, 23730, 23849);
        fdd(b, 18, 860000, 5850, 5999, 815000, 23850, 23999);
        fdd(b, 19, 875000, 6000, 6149, 830000, 24000, 24149);
        fdd(b, 20, 791000, 6150, 6449, 832000, 24150, 24449);
        fdd(b, 21, 1495900, 6450, 6599, 1447900, 24450, 24599);
        fdd(b, 22, 3510000, 6600, 7399, 3410000, 24600, 25399);
        fdd(b, 23, 2180000, 7500, 7699, 2000000, 25500, 25699);
        fdd(b, 24, 1525000, 7700, 8039, 1626500, 25700, 26039);
        fdd(b, 25, 1930000, 8040, 8689, 1850000, 26040, 26689);
        fdd(b, 26, 859000, 8690, 9039, 814000, 26690, 27039);
        fdd(b, 27, 852000, 9040, 9209, 807000, 27040, 27209);
        fdd(b, 28, 758000, 9210, 9659, 703000, 27210, 27659);
        sdl(b, 29, 717000, 9660, 9769);
        fdd(b, 30, 2350000, 9770, 9869, 2305000, 27660, 27759);
        fdd(b, 31, 462500, 9870, 9919, 452500, 27760, 27809);
        sdl(b, 32, 1452000, 9920, 10359);
        tdd(b, 33, 1900000, 36000, 36199);
        tdd(b, 34, 2010000, 36200, 36349);
        tdd(b, 35, 1850000, 36350, 36949);
        tdd(b, 36, 1930000, 36950, 37549);
        tdd(b, 37, 1910000, 37550, 37749);
        tdd(b, 38, 2570000, 37750, 38249);
        tdd(b, 39, 1880000, 38250, 38649);
        tdd(b, 40, 2300000, 38650, 39649);
        tdd(b, 41, 2496000, 39650, 41589);
        tdd(b, 42, 3400000, 41590, 43589);
        tdd(b, 43, 3600000, 43590, 45589);
        tdd(b, 44, 703000, 45590, 46589);
        tdd(b, 45, 1447000, 46590, 46789);
        tdd(b, 46, 5150000, 46790, 54539);
        tdd(b, 47, 5855000, 54540, 55239);
        tdd(b, 48, 3550000, 55240, 56739);
        tdd(b, 49, 3550000, 56740, 58239);
        tdd(b, 50, 1432000, 58240, 59089);
        tdd(b, 51, 1427000, 59090, 59139);
        tdd(b, 52, 3300000, 59140, 60139);
        tdd(b, 53, 2483500, 60140, 60254);
        fdd(b, 65, 2110000, 65536, 66435, 1920000, 131072, 131971);
        fdd(b, 66, 2110000, 66436, 67335, 1710000, 131972, 132671);
        sdl(b, 67, 738000, 67336, 67535);
        fdd(b, 68, 753000, 67536, 67835, 698000, 132672, 132971);
        sdl(b, 69, 2570000, 67836, 68335);
        fdd(b, 70, 1995000, 68336, 68585, 1695000, 132972, 133121);
        fdd(b, 71, 617000, 68586, 68935, 663000, 133122, 133471);
        fdd(b, 72, 461000, 68936, 68985, 451000, 133472, 133521);
        fdd(b, 73, 460000, 68986, 69035, 450000, 133522, 133571);
        fdd(b, 74, 1475000, 69036, 69465, 1427000, 133572, 134001);
        sdl(b, 75, 1432000, 69466, 70315);
        sdl(b, 76, 1427000, 70316, 70365);
        fdd(b, 85, 728000, 70366, 70545, 698000, 134002, 134181);
        fdd(b, 87, 420000, 70546, 70595, 410000, 134182, 134231);
        fdd(b, 88, 422000, 70596, 70645, 412000, 134232, 134281);
        BANDS = Collections.unmodifiableList(b);
    }

    private LteBandTable() {}

    public static List<LteBand> all() { return BANDS; }

    /** EARFCN ranges do not overlap between bands, so a downlink EARFCN maps to at most one band. */
    public static LteBand forDownlinkEarfcn(int earfcn) {
        for (LteBand band : BANDS) if (band.containsDownlink(earfcn)) return band;
        return null;
    }

    public static LteBand byNumber(int number) {
        for (LteBand band : BANDS) if (band.number == number) return band;
        return null;
    }

    private static void fdd(List<LteBand> b, int n, long dl, int dlFirst, int dlLast, long ul, int ulFirst, int ulLast) {
        b.add(new LteBand(n, Duplex.FDD, dl, dlFirst, dlLast, ul, ulFirst, ulLast));
    }
    private static void tdd(List<LteBand> b, int n, long f, int first, int last) {
        b.add(new LteBand(n, Duplex.TDD, f, first, last, f, first, last));
    }
    private static void sdl(List<LteBand> b, int n, long dl, int first, int last) {
        b.add(new LteBand(n, Duplex.SDL, dl, first, last, -1, -1, -1));
    }
}
