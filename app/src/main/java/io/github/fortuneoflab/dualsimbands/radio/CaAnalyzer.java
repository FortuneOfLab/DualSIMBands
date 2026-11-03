package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Separates verified component carriers from indications. Detected neighbour cells, even
 * several on different bands, are never treated as components and their bandwidths are never summed.
 */
public final class CaAnalyzer {
    private CaAnalyzer() {}

    public static CaAssessment analyze(RadioSnapshot s, long nowMillis, long staleAfterMillis) {
        int[] reported = s.service == null ? new int[0] : s.service.cellBandwidthsKhz.clone();
        Set<CaAssessment.Evidence> evidence = EnumSet.noneOf(CaAssessment.Evidence.class);
        List<CaAssessment.Component> components = new ArrayList<>();

        // 1. Authorized PhysicalChannelConfig (privileged experiment) names every component with its role.
        if (s.physicalChannels != null && !s.physicalChannels.isEmpty()
                && fresh(s.physicalChannelsAtMillis, nowMillis, staleAfterMillis)) {
            for (RadioSnapshot.PhysicalChannel p : distinct(s.physicalChannels)) {
                int[] reportedBand = p.band > 0 ? new int[]{p.band} : null;
                if (p.rat == Rat.LTE) components.add(new CaAssessment.Component(p.primary, Rat.LTE,
                        FrequencyCalculator.lte(Measure.earfcn(p.channel), reportedBand), null, p.bandwidthKhz, p.pci, null));
                else if (p.rat == Rat.NR) components.add(new CaAssessment.Component(p.primary, Rat.NR, null,
                        FrequencyCalculator.nr(Measure.nrArfcn(p.channel), reportedBand), p.bandwidthKhz, p.pci, null));
            }
            evidence.add(CaAssessment.Evidence.PHYSICAL_CHANNEL_CONFIG);
            return verified(withSignals(components, s, nowMillis, staleAfterMillis), reported, evidence, true);
        }

        // 2. CellInfo connection status, only when the platform marks both a primary and a secondary cell.
        if (s.cells.status == RadioSnapshot.Cells.Status.OK && fresh(s.cells.observedAtMillis, nowMillis, staleAfterMillis)) {
            // Each cell by its own time: the list's is its newest cell's, and an old serving cell listed with a fresh
            // neighbour is no part of the current composition.
            boolean primary = false, secondary = false;
            for (LteCell c : s.cells.lte) {
                if (!fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
                if (c.role == CellRole.PRIMARY_SERVING) primary = true;
                if (c.role == CellRole.SECONDARY_SERVING) secondary = true;
            }
            // A secondary-serving NR cell is the EN-DC leg; it belongs in the composition too.
            for (NrCell c : s.cells.nr) {
                if (!fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) continue;
                if (c.role == CellRole.PRIMARY_SERVING) primary = true;
                if (c.role == CellRole.SECONDARY_SERVING) secondary = true;
            }
            if (primary && secondary) {
                for (LteCell c : s.cells.lte) if (c.role.isServing() && fresh(c.observedAtMillis, nowMillis, staleAfterMillis))
                    components.add(new CaAssessment.Component(c.role == CellRole.PRIMARY_SERVING, Rat.LTE, c.channel, null,
                            c.bandwidthKhz == null ? -1 : c.bandwidthKhz, c.pci, null));
                // CellIdentityNr has no bandwidth field: unknown, never guessed.
                for (NrCell c : s.cells.nr) if (c.role.isServing() && fresh(c.observedAtMillis, nowMillis, staleAfterMillis)) components.add(new CaAssessment.Component(
                        c.role == CellRole.PRIMARY_SERVING, Rat.NR, null, c.channel, -1, c.pci, null));
                evidence.add(CaAssessment.Evidence.CELL_CONNECTION_STATUS);
                return verified(withSignals(components, s, nowMillis, staleAfterMillis), reported, evidence, false);
            }
        }

        if (s.service == null || !s.service.inService()) {
            return new CaAssessment(CaAssessment.Status.UNKNOWN, Provenance.UNKNOWN, components, null, reported, evidence);
        }
        // 3. Indications only: never a PCC/SCC claim.
        boolean idle = idle(s, nowMillis, staleAfterMillis);
        if (!idle && s.display != null && (s.display.override == DisplayOverride.LTE_CA
                || s.display.override == DisplayOverride.LTE_ADVANCED_PRO)) {
            evidence.add(CaAssessment.Evidence.DISPLAY_OVERRIDE);
        }
        // While idle there is no CA; the list is what remains from the last connection.
        if (reported.length > 1 && !idle) evidence.add(CaAssessment.Evidence.SERVICE_STATE_BANDWIDTHS);
        if (!evidence.isEmpty()) {
            return new CaAssessment(CaAssessment.Status.INDICATED, Provenance.INDICATED, components, null, reported, evidence, idle);
        }
        // Absence of an indication is not proof that CA is off.
        return new CaAssessment(CaAssessment.Status.NOT_INDICATED, Provenance.UNKNOWN, components, null, reported, evidence, idle);
    }

    /**
     * One entry per RAT and channel. Two carriers on the same channel cannot be aggregated; the modem has been
     * seen reporting the same B1 twice (e.g. old and new cell around a handover), which would also count its
     * bandwidth twice. The primary wins; otherwise the first report, filled with any value it lacked.
     */
    static List<RadioSnapshot.PhysicalChannel> distinct(List<RadioSnapshot.PhysicalChannel> in) {
        java.util.Map<String, RadioSnapshot.PhysicalChannel> kept = new java.util.LinkedHashMap<>();
        for (RadioSnapshot.PhysicalChannel p : in) {
            String key = p.rat + ":" + p.channel;
            RadioSnapshot.PhysicalChannel old = kept.get(key);
            if (old == null) { kept.put(key, p); continue; }
            RadioSnapshot.PhysicalChannel main = p.primary && !old.primary ? p : old, other = main == p ? old : p;
            kept.put(key, new RadioSnapshot.PhysicalChannel(main.rat, main.primary || other.primary, main.channel,
                    main.bandwidthKhz > 0 ? main.bandwidthKhz : other.bandwidthKhz,
                    main.band > 0 ? main.band : other.band, main.pci != null ? main.pci : other.pci));
        }
        return new ArrayList<>(kept.values());
    }

    /**
     * The same rule for the CellInfo route, where the serving cell may also be listed as a secondary on its own
     * channel. Components whose channel is unknown are all kept. The primary wins, then one with a known width.
     */
    static List<CaAssessment.Component> distinctComponents(List<CaAssessment.Component> in) {
        java.util.Map<String, CaAssessment.Component> kept = new java.util.LinkedHashMap<>();
        int unknown = 0;
        for (CaAssessment.Component c : in) {
            int ch = c.lte != null ? c.lte.earfcn : c.nr != null ? c.nr.nrArfcn : -1;
            String key = ch < 0 ? "?" + unknown++ : c.rat + ":" + ch;
            CaAssessment.Component old = kept.get(key);
            if (old == null || (c.primary && !old.primary)
                    || (c.primary == old.primary && old.bandwidthKhz <= 0 && c.bandwidthKhz > 0)) kept.put(key, c);
        }
        return new ArrayList<>(kept.values());
    }

    private static List<CaAssessment.Component> withSignals(List<CaAssessment.Component> components, RadioSnapshot s,
                                                            long nowMillis, long staleAfterMillis) {
        int nr = 0;
        for (CaAssessment.Component c : components) if (c.rat == Rat.NR) nr++;
        List<CaAssessment.Component> out = new ArrayList<>();
        for (CaAssessment.Component c : components) {
            ComponentSignal sig = ComponentSignal.attribute(c, nr, s, nowMillis, staleAfterMillis);
            out.add(c.withSignal(sig == null ? null : sig.withDerivedRssi(c)));
        }
        return out;
    }

    /**
     * @param complete the source names every carrier (PhysicalChannelConfig). The cell list may not: two carriers of one
     *     RAT there still prove CA, but one per RAT does not rule it out.
     */
    private static CaAssessment verified(List<CaAssessment.Component> components, int[] reported,
                                         Set<CaAssessment.Evidence> evidence, boolean complete) {
        // PCC first, then LTE SCCs, then NR (PhysicalChannelConfig may list them in any order), so the
        // position alone shows the role.
        components = distinctComponents(components);
        java.util.Collections.sort(components, (x, y) -> Integer.compare(order(x), order(y)));
        int lte = 0, nr = 0;
        long sum = 0;
        boolean allKnown = true;
        for (CaAssessment.Component c : components) {
            if (c.rat == Rat.LTE) lte++; else nr++;
            if (c.bandwidthKhz > 0) sum += c.bandwidthKhz; else allKnown = false;
        }
        CaAssessment.Status status = lte > 1 || nr > 1 ? CaAssessment.Status.VERIFIED_CA
                : complete ? CaAssessment.Status.VERIFIED_NO_CA : CaAssessment.Status.CELLS_ONLY;
        return new CaAssessment(status, status == CaAssessment.Status.CELLS_ONLY ? Provenance.INDICATED : Provenance.VERIFIED,
                components, allKnown && !components.isEmpty() ? sum : null, reported, evidence);
    }

    /** Not the data SIM, or a fresh PhysicalChannelConfig report that lists no carrier. */
    static boolean idle(RadioSnapshot s, long nowMillis, long staleAfterMillis) {
        if (!s.defaultData) return true;
        return s.physicalChannels != null && s.physicalChannels.isEmpty()
                && fresh(s.physicalChannelsAtMillis, nowMillis, staleAfterMillis);
    }

    private static int order(CaAssessment.Component c) { return c.primary ? 0 : c.rat == Rat.LTE ? 1 : 2; }

    static boolean fresh(long atMillis, long nowMillis, long staleAfterMillis) {
        return atMillis >= 0 && nowMillis - atMillis <= staleAfterMillis;
    }
}
