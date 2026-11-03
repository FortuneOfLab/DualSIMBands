package io.github.fortuneoflab.dualsimbands.radio;

/** One CellInfoLte entry. Unknown measurements are null; times are elapsedRealtime milliseconds. */
public final class LteCell {
    public final CellRole role;
    public final LteChannel channel;
    public final Integer pci, tac, bandwidthKhz;
    /** Location-adjacent; never log without the separate opt-in. */
    public final Long ci;
    public final Integer rsrp, rsrq, rssi, rssnr;
    /** "440-51"; null when not reported (neighbours usually have none). */
    public final String plmn;
    /** Timing advance (0-1282, LTE TA units); null when not reported. */
    public final Integer ta;
    public final long observedAtMillis;

    public LteCell(CellRole role, LteChannel channel, Integer pci, Integer tac, Long ci, Integer bandwidthKhz,
                   Integer rsrp, Integer rsrq, Integer rssi, Integer rssnr, long observedAtMillis) {
        this(role, channel, pci, tac, ci, bandwidthKhz, rsrp, rsrq, rssi, rssnr, null, null, observedAtMillis);
    }

    public LteCell(CellRole role, LteChannel channel, Integer pci, Integer tac, Long ci, Integer bandwidthKhz,
                   Integer rsrp, Integer rsrq, Integer rssi, Integer rssnr, String plmn, Integer ta, long observedAtMillis) {
        this.role = role; this.channel = channel; this.pci = pci; this.tac = tac; this.ci = ci;
        this.bandwidthKhz = bandwidthKhz; this.rsrp = rsrp; this.rsrq = rsrq; this.rssi = rssi; this.rssnr = rssnr;
        this.plmn = plmn; this.ta = ta;
        this.observedAtMillis = observedAtMillis;
    }

    /** Builds from raw platform integers, discarding sentinels and out-of-range values. */
    public static LteCell fromRaw(boolean registered, int connectionStatus, int earfcn, int[] bands, int pci, int tac,
                                  int ci, int bandwidthKhz, int rsrp, int rsrq, int rssi, int rssnr, long observedAtMillis) {
        return fromRaw(registered, connectionStatus, earfcn, bands, pci, tac, ci, bandwidthKhz, rsrp, rsrq, rssi, rssnr,
                null, null, Measure.UNAVAILABLE, observedAtMillis);
    }

    public static LteCell fromRaw(boolean registered, int connectionStatus, int earfcn, int[] bands, int pci, int tac,
                                  int ci, int bandwidthKhz, int rsrp, int rsrq, int rssi, int rssnr,
                                  String mcc, String mnc, int ta, long observedAtMillis) {
        Integer validCi = Measure.valid(ci, 0, 268435455);
        // Timing advance is kept per timing-advance group and reported for the primary cell: a neighbour's or a
        // secondary (SCC) cell's 0 means "not reported", not "0 m".
        CellRole role = CellRole.from(registered, connectionStatus);
        if (ta == 0 && (role == CellRole.SECONDARY_SERVING || (!role.isServing() && !registered))) ta = Measure.UNAVAILABLE;
        return new LteCell(CellRole.from(registered, connectionStatus),
                FrequencyCalculator.lte(Measure.earfcn(earfcn), bands),
                Measure.valid(pci, 0, 503), Measure.valid(tac, 0, 65535),
                validCi == null ? null : validCi.longValue(),
                Measure.valid(bandwidthKhz, 1400, 20000),
                Measure.valid(rsrp, -140, -43), Measure.valid(rsrq, -34, 3),
                Measure.valid(rssi, -113, -51), Measure.valid(rssnr, -20, 30),
                Measure.plmn(mcc, mnc), Measure.valid(ta, 0, 1282), observedAtMillis);
    }
}
