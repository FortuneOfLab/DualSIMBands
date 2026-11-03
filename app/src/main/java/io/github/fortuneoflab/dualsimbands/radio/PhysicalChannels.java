package io.github.fortuneoflab.dualsimbands.radio;

/** Converts raw PhysicalChannelConfig values. Entries without an explicit PCC/SCC role are dropped. */
public final class PhysicalChannels {
    /** PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING / CONNECTION_SECONDARY_SERVING. */
    static final int PRIMARY = 1, SECONDARY = 2;

    private PhysicalChannels() {}

    /** @return null when the role is unknown or the RAT is neither LTE nor NR */
    public static RadioSnapshot.PhysicalChannel from(int networkType, int connectionStatus, int channel, int bandwidthKhz) {
        return from(networkType, connectionStatus, channel, bandwidthKhz, 0);
    }

    /** @param band PhysicalChannelConfig.getBand(); 0 or a sentinel when not reported */
    public static RadioSnapshot.PhysicalChannel from(int networkType, int connectionStatus, int channel, int bandwidthKhz,
                                                     int band) {
        return from(networkType, connectionStatus, channel, bandwidthKhz, band, Integer.MAX_VALUE);
    }

    /** @param pci PhysicalChannelConfig.getPhysicalCellId(); a sentinel when not reported */
    public static RadioSnapshot.PhysicalChannel from(int networkType, int connectionStatus, int channel, int bandwidthKhz,
                                                     int band, int pci) {
        if (connectionStatus != PRIMARY && connectionStatus != SECONDARY) return null;
        Rat rat = Rat.fromNetworkType(networkType);
        if (rat != Rat.LTE && rat != Rat.NR) return null;
        int ch = rat == Rat.LTE ? Measure.earfcn(channel) : Measure.nrArfcn(channel);
        Integer bw = Measure.valid(bandwidthKhz, 1, 400_000);
        Integer b = Measure.valid(band, 1, 1024);
        Integer id = Measure.valid(pci, 0, rat == Rat.LTE ? 503 : 1007);
        // An all-zero entry (EARFCN 0, 1.4 MHz, PCI 0) is an empty slot, not a carrier: the Pixel 10a's modem listed
        // one as an extra SCC for a few seconds while docomo aggregated B1 + B3 + B19.
        if (rat == Rat.LTE && ch == 0 && (bw == null || bw <= 1400) && (id == null || id == 0)) return null;
        return new RadioSnapshot.PhysicalChannel(rat, connectionStatus == PRIMARY, ch, bw == null ? -1 : bw, b == null ? 0 : b, id);
    }
}
