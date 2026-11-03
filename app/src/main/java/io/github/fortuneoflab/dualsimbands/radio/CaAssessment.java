package io.github.fortuneoflab.dualsimbands.radio;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Carrier-aggregation state. Components are listed only when an authoritative source names them. */
public final class CaAssessment {
    public enum Status {
        /** Two or more component carriers named by an authoritative source. */
        VERIFIED_CA,
        /** An authoritative source names exactly one carrier per RAT. */
        VERIFIED_NO_CA,
        /**
         * The cell list marks one serving cell per RAT. It may leave carriers out (seen on the Pixel 10a: one n77 listed
         * while PhysicalChannelConfig named two), so CA is neither confirmed nor ruled out; the listed cells are real.
         */
        CELLS_ONLY,
        /** 4G+ display or several reported bandwidths; PCC/SCC composition unknown. */
        INDICATED,
        NOT_INDICATED,
        /** No service or no data. */
        UNKNOWN
    }

    public enum Evidence {
        PHYSICAL_CHANNEL_CONFIG, CELL_CONNECTION_STATUS, DISPLAY_OVERRIDE, SERVICE_STATE_BANDWIDTHS
    }

    public static final class Component {
        public final boolean primary;
        public final Rat rat;
        /** Exactly one of lte / nr is set. */
        public final LteChannel lte;
        public final NrChannel nr;
        /** -1 when unknown. */
        public final int bandwidthKhz;
        /** Physical cell ID when reported; used only to match the component to a measured cell. */
        public final Integer pci;
        /** Attributed signal, or null when no unambiguous source exists. */
        public final ComponentSignal signal;
        Component(boolean primary, Rat rat, LteChannel lte, NrChannel nr, int bandwidthKhz) {
            this(primary, rat, lte, nr, bandwidthKhz, null, null);
        }
        Component(boolean primary, Rat rat, LteChannel lte, NrChannel nr, int bandwidthKhz, Integer pci, ComponentSignal signal) {
            this.primary = primary; this.rat = rat; this.lte = lte; this.nr = nr; this.bandwidthKhz = bandwidthKhz;
            this.pci = pci; this.signal = signal;
        }
        Component withSignal(ComponentSignal s) { return new Component(primary, rat, lte, nr, bandwidthKhz, pci, s); }
    }

    public final Status status;
    public final Provenance provenance;
    /** Only authoritative components; never merely detected cells. */
    public final List<Component> components;
    /** Sum of verified component bandwidths when every one is known; otherwise null. */
    public final Long aggregateBandwidthKhz;
    /** ServiceState.getCellBandwidths(): indicated only, RAT and role per entry unknown. */
    public final int[] reportedBandwidthsKhz;
    public final Set<Evidence> evidence;

    /**
     * No carrier is active now: the SIM is not the data SIM, or PhysicalChannelConfig is being received and
     * is empty (the data SIM is not exchanging data). The bandwidth list and CA icon are then leftovers
     * from the last connection and are not used.
     */
    public final boolean idle;

    CaAssessment(Status status, Provenance provenance, List<Component> components, Long aggregateBandwidthKhz,
                 int[] reportedBandwidthsKhz, Set<Evidence> evidence) {
        this(status, provenance, components, aggregateBandwidthKhz, reportedBandwidthsKhz, evidence, false);
    }

    CaAssessment(Status status, Provenance provenance, List<Component> components, Long aggregateBandwidthKhz,
                 int[] reportedBandwidthsKhz, Set<Evidence> evidence, boolean idle) {
        this.idle = idle;
        this.status = status; this.provenance = provenance;
        this.components = Collections.unmodifiableList(components);
        this.aggregateBandwidthKhz = aggregateBandwidthKhz;
        this.reportedBandwidthsKhz = reportedBandwidthsKhz;
        this.evidence = Collections.unmodifiableSet(evidence.isEmpty() ? EnumSet.noneOf(Evidence.class) : EnumSet.copyOf(evidence));
    }
}
