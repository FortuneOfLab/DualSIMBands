package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.List;

/**
 * Attributes the one leftover ServiceState bandwidth to the one NR component whose bandwidth CellInfo
 * does not report. Only when it is unambiguous: every LTE component has a known bandwidth, removing
 * those from the reported list leaves exactly one value, and exactly one NR component lacks a bandwidth.
 * The result is an inference ("推定"), never shown as a verified value.
 */
public final class BandwidthInference {
    private BandwidthInference() {}

    /** @return the inferred NR bandwidth in kHz, or -1 when it cannot be attributed unambiguously */
    public static int nrBandwidthKhz(CaAssessment ca) {
        if (ca == null || ca.components.isEmpty() || ca.reportedBandwidthsKhz.length == 0) return -1;
        List<Integer> left = new ArrayList<>();
        for (int k : ca.reportedBandwidthsKhz) left.add(k);
        int unknownNr = 0;
        for (CaAssessment.Component c : ca.components) {
            if (c.rat == Rat.LTE) {
                if (c.bandwidthKhz <= 0) return -1;
                if (!left.remove(Integer.valueOf(c.bandwidthKhz))) return -1; // lists disagree
            } else if (c.rat == Rat.NR) {
                if (c.bandwidthKhz <= 0) unknownNr++;
                else if (!left.remove(Integer.valueOf(c.bandwidthKhz))) return -1;
            }
        }
        return unknownNr == 1 && left.size() == 1 && left.get(0) > 0 ? left.get(0) : -1;
    }
}
