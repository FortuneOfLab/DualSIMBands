package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.List;

/**
 * Signal of one verified component carrier, attributed only when the source is unambiguous:
 * <ul>
 *   <li>LTE PCC (and an SA NR primary): SignalStrength, which reports the primary serving cell.</li>
 *   <li>The single NR component: SignalStrength's NR entry (in NSA it is the SCG leg). With several NR
 *       components the entry cannot be attributed, so cell matching is used instead.</li>
 *   <li>Otherwise: the one fresh CellInfo cell on the same channel (and the same PCI when both are known),
 *       preferring cells the platform marks as serving.</li>
 * </ul>
 * Anything else stays null; a neighbour's measurement is never moved onto a component by guesswork.
 */
public final class ComponentSignal {
    /** RSRP / RSRQ in dBm / dB; SINR is RSSNR for LTE and SS-SINR for NR (dB). Null when not reported. */
    public final Integer rsrp, rsrq, sinr;
    /** LTE RSSI, dBm; always null for NR, which has no RSSI. */
    public final Integer rssi;
    /** True when {@link #rssi} was computed from RSRP, RSRQ and the bandwidth rather than reported. */
    public final boolean rssiDerived;

    ComponentSignal(Integer rsrp, Integer rsrq, Integer sinr) { this(rsrp, rsrq, sinr, null); }
    ComponentSignal(Integer rsrp, Integer rsrq, Integer sinr, Integer rssi) { this(rsrp, rsrq, sinr, rssi, false); }
    ComponentSignal(Integer rsrp, Integer rsrq, Integer sinr, Integer rssi, boolean rssiDerived) {
        this.rsrp = rsrp; this.rsrq = rsrq; this.sinr = sinr; this.rssi = rssi; this.rssiDerived = rssiDerived;
    }

    /**
     * LTE RSSI from RSRQ = N x RSRP / RSSI (3GPP TS 36.214 5.1.3), N being the resource blocks in the carrier's
     * bandwidth: RSSI = RSRP - RSRQ + 10 log10(N). Checked on the Pixel 10a against the modem's own PCC RSSI
     * (within 1 dB). Null when the bandwidth is not a standard LTE one or a value is missing.
     */
    public static Integer lteRssi(Integer rsrp, Integer rsrq, int bandwidthKhz) {
        int n = resourceBlocks(bandwidthKhz);
        if (rsrp == null || rsrq == null || n <= 0) return null;
        return (int) Math.round(rsrp - rsrq + 10 * Math.log10(n));
    }

    static int resourceBlocks(int bandwidthKhz) {
        switch (bandwidthKhz) {
            case 1400: return 6;
            case 3000: return 15;
            case 5000: return 25;
            case 10000: return 50;
            case 15000: return 75;
            case 20000: return 100;
            default: return -1;
        }
    }

    /** The same signal with a computed RSSI when an LTE carrier reported none. */
    ComponentSignal withDerivedRssi(CaAssessment.Component comp) {
        if (rssi != null || comp.rat != Rat.LTE) return this;
        Integer d = lteRssi(rsrp, rsrq, comp.bandwidthKhz);
        return d == null ? this : new ComponentSignal(rsrp, rsrq, sinr, d, true);
    }

    static ComponentSignal attribute(CaAssessment.Component comp, int nrComponents, RadioSnapshot s,
                                     long nowMillis, long staleAfterMillis) {
        RadioSnapshot.Signal g = s.signal;
        if (comp.rat == Rat.LTE && comp.primary && g != null && g.lteRsrp != null)
            return new ComponentSignal(g.lteRsrp, g.lteRsrq, g.lteRssnr, g.lteRssi);
        if (comp.rat == Rat.NR && nrComponents == 1 && g != null && g.nrSsRsrp != null)
            return new ComponentSignal(g.nrSsRsrp, g.nrSsRsrq, g.nrSsSinr);
        if (s.cells == null || s.cells.status != RadioSnapshot.Cells.Status.OK
                || !CaAnalyzer.fresh(s.cells.observedAtMillis, nowMillis, staleAfterMillis)) return null;
        if (comp.rat == Rat.LTE && comp.lte != null && comp.lte.earfcn >= 0) {
            List<LteCell> hits = new ArrayList<>();
            for (LteCell c : s.cells.lte) {
                if (c.channel == null || c.channel.earfcn != comp.lte.earfcn || c.rsrp == null) continue;
                // Each cell by its own time: the list's is its newest cell's.
                if (!CaAnalyzer.fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
                if (comp.pci != null && c.pci != null && !comp.pci.equals(c.pci)) continue;
                hits.add(c);
            }
            LteCell c = pick(hits, comp.pci);
            return c == null ? null : new ComponentSignal(c.rsrp, c.rsrq, c.rssnr, c.rssi);
        }
        if (comp.rat == Rat.NR && comp.nr != null && comp.nr.nrArfcn >= 0) {
            List<NrCell> hits = new ArrayList<>();
            for (NrCell c : s.cells.nr) {
                if (c.channel == null || c.channel.nrArfcn != comp.nr.nrArfcn || c.ssRsrp == null) continue;
                if (!CaAnalyzer.fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
                if (comp.pci != null && c.pci != null && !comp.pci.equals(c.pci)) continue;
                hits.add(c);
            }
            NrCell c = pick(hits, comp.pci);
            return c == null ? null : new ComponentSignal(c.ssRsrp, c.ssRsrq, c.ssSinr);
        }
        return null;
    }

    /**
     * One serving-marked hit wins. Otherwise only a single hit whose PCI equals the component's: neighbours
     * often share the SCC's frequency, so the channel alone does not identify the cell.
     */
    private static <T> T pick(List<T> hits, Integer pci) {
        T serving = null;
        int servingCount = 0;
        for (T h : hits) if (role(h).isServing()) { serving = h; servingCount++; }
        if (servingCount == 1) return serving;
        if (servingCount == 0 && hits.size() == 1 && pci != null && pci.equals(pciOf(hits.get(0)))) return hits.get(0);
        return null;
    }

    private static CellRole role(Object c) { return c instanceof LteCell ? ((LteCell) c).role : ((NrCell) c).role; }
    private static Integer pciOf(Object c) { return c instanceof LteCell ? ((LteCell) c).pci : ((NrCell) c).pci; }
}
