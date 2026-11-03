package io.github.fortuneoflab.dualsimbands.radio;

import java.util.Collections;
import java.util.List;

/**
 * Everything observed for one subscription at one moment. Each part carries its own time
 * (elapsedRealtime ms) because callbacks arrive independently. A null part was never received.
 */
public final class RadioSnapshot {
    public enum RegState {
        IN_SERVICE, OUT_OF_SERVICE, EMERGENCY_ONLY, POWER_OFF, UNKNOWN;
        /** Maps ServiceState.STATE_* values. */
        public static RegState fromPlatform(int v) {
            switch (v) {
                case 0: return IN_SERVICE;
                case 1: return OUT_OF_SERVICE;
                case 2: return EMERGENCY_ONLY;
                case 3: return POWER_OFF;
                default: return UNKNOWN;
            }
        }
    }

    /** ServiceState: public, location-sensitive fields may be redacted without location permission. */
    public static final class Service {
        public final RegState voiceReg, dataReg;
        public final Rat voiceRat, dataRat;
        /** Primary serving cell channel (EARFCN for LTE, NR-ARFCN for NR), -1 when unknown. */
        public final int channelNumber;
        /** Component bandwidths reported by the platform; RAT and role of each entry are not given. */
        public final int[] cellBandwidthsKhz;
        public final long observedAtMillis;
        public Service(RegState voiceReg, RegState dataReg, Rat voiceRat, Rat dataRat, int channelNumber,
                       int[] cellBandwidthsKhz, long observedAtMillis) {
            this.voiceReg = voiceReg; this.dataReg = dataReg; this.voiceRat = voiceRat; this.dataRat = dataRat;
            this.channelNumber = channelNumber;
            this.cellBandwidthsKhz = cellBandwidthsKhz == null ? new int[0] : cellBandwidthsKhz.clone();
            this.observedAtMillis = observedAtMillis;
        }
        public boolean inService() { return dataReg == RegState.IN_SERVICE || voiceReg == RegState.IN_SERVICE; }
    }

    /** TelephonyDisplayInfo: what the status bar shows. */
    public static final class Display {
        public final Rat network;
        public final DisplayOverride override;
        public final long observedAtMillis;
        public Display(Rat network, DisplayOverride override, long observedAtMillis) {
            this.network = network; this.override = override; this.observedAtMillis = observedAtMillis;
        }
    }

    /**
     * SignalStrength serving-cell measurements, pushed only on change. NR entries may appear during NSA
     * but do not prove an active leg. When SignalStrength is old, the serving cells' CellInfo measurements
     * ({@link SignalFallback}); {@code observedAtMillis} is then the CellInfo time.
     */
    public static final class Signal {
        public final Integer lteRsrp, lteRsrq, lteRssnr, nrSsRsrp, nrSsRsrq, nrSsSinr;
        /** LTE RSSI, dBm; null when not reported (NR has no RSSI). */
        public final Integer lteRssi;
        public final long observedAtMillis;
        public Signal(Integer lteRsrp, Integer lteRsrq, Integer lteRssnr, Integer nrSsRsrp, Integer nrSsRsrq,
                      Integer nrSsSinr, long observedAtMillis) {
            this(lteRsrp, lteRsrq, lteRssnr, null, nrSsRsrp, nrSsRsrq, nrSsSinr, observedAtMillis);
        }
        public Signal(Integer lteRsrp, Integer lteRsrq, Integer lteRssnr, Integer lteRssi, Integer nrSsRsrp,
                      Integer nrSsRsrq, Integer nrSsSinr, long observedAtMillis) {
            this.lteRssi = lteRssi;
            this.lteRsrp = lteRsrp; this.lteRsrq = lteRsrq; this.lteRssnr = lteRssnr;
            this.nrSsRsrp = nrSsRsrp; this.nrSsRsrq = nrSsRsrq; this.nrSsSinr = nrSsSinr;
            this.observedAtMillis = observedAtMillis;
        }
    }

    public static final class Cells {
        public enum Status { OK, NO_PERMISSION, LOCATION_OFF, ERROR, NOT_REQUESTED }
        public final Status status;
        public final List<LteCell> lte;
        public final List<NrCell> nr;
        public final long observedAtMillis;
        public Cells(Status status, List<LteCell> lte, List<NrCell> nr, long observedAtMillis) {
            this.status = status;
            this.lte = lte == null ? Collections.emptyList() : Collections.unmodifiableList(lte);
            this.nr = nr == null ? Collections.emptyList() : Collections.unmodifiableList(nr);
            this.observedAtMillis = observedAtMillis;
        }
        public static Cells unavailable(Status status) { return new Cells(status, null, null, -1); }
    }

    /** A component carrier from an authorized PhysicalChannelConfig source (Phase 4 experiment). */
    public static final class PhysicalChannel {
        public final Rat rat;
        public final boolean primary;
        /** EARFCN for LTE, NR-ARFCN for NR. */
        public final int channel;
        /** -1 when unknown. */
        public final int bandwidthKhz;
        /** Band reported by PhysicalChannelConfig.getBand(); 0 when not reported. */
        public final int band;
        /** Physical cell ID; null when not reported. */
        public final Integer pci;
        public PhysicalChannel(Rat rat, boolean primary, int channel, int bandwidthKhz) {
            this(rat, primary, channel, bandwidthKhz, 0);
        }
        public PhysicalChannel(Rat rat, boolean primary, int channel, int bandwidthKhz, int band) {
            this(rat, primary, channel, bandwidthKhz, band, null);
        }
        public PhysicalChannel(Rat rat, boolean primary, int channel, int bandwidthKhz, int band, Integer pci) {
            this.rat = rat; this.primary = primary; this.channel = channel; this.bandwidthKhz = bandwidthKhz;
            this.band = band; this.pci = pci;
        }
    }

    public final int subscriptionId, slotIndex;
    public final boolean defaultData;
    public final Service service;
    public final Display display;
    public final Signal signal;
    public final Cells cells;
    /** Null when no authorized source is available; empty when the source reported none. */
    public final List<PhysicalChannel> physicalChannels;
    /** Until when the channels count as current: a live listener's last report is refreshed while it stays registered. */
    public final long physicalChannelsAtMillis;
    /** When the channels were actually reported; what decides whether they are newer than the cell list. */
    public final long physicalChannelsReportedAtMillis;

    public RadioSnapshot(int subscriptionId, int slotIndex, boolean defaultData, Service service, Display display,
                         Signal signal, Cells cells, List<PhysicalChannel> physicalChannels, long physicalChannelsAtMillis) {
        this(subscriptionId, slotIndex, defaultData, service, display, signal, cells, physicalChannels,
                physicalChannelsAtMillis, physicalChannelsAtMillis);
    }

    public RadioSnapshot(int subscriptionId, int slotIndex, boolean defaultData, Service service, Display display,
                         Signal signal, Cells cells, List<PhysicalChannel> physicalChannels, long physicalChannelsAtMillis,
                         long physicalChannelsReportedAtMillis) {
        this.subscriptionId = subscriptionId; this.slotIndex = slotIndex; this.defaultData = defaultData;
        this.service = service; this.display = display; this.signal = signal;
        this.cells = cells == null ? Cells.unavailable(Cells.Status.NOT_REQUESTED) : cells;
        this.physicalChannels = physicalChannels == null ? null : Collections.unmodifiableList(physicalChannels);
        this.physicalChannelsAtMillis = physicalChannelsAtMillis;
        this.physicalChannelsReportedAtMillis = physicalChannelsReportedAtMillis;
    }
}
