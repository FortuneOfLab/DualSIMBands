package io.github.fortuneoflab.dualsimbands.history;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import io.github.fortuneoflab.dualsimbands.radio.CaAssessment;
import io.github.fortuneoflab.dualsimbands.radio.LteCell;
import io.github.fortuneoflab.dualsimbands.radio.LteChannel;
import io.github.fortuneoflab.dualsimbands.radio.Mhz;
import io.github.fortuneoflab.dualsimbands.radio.NrCell;
import io.github.fortuneoflab.dualsimbands.radio.NrChannel;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.traffic.TrafficStats;

/**
 * One history row. Values keep their provenance in separate *_source columns so a CSV reader can tell
 * verified data from indications. Subscriber identifiers (ICCID/IMSI/phone number) are never recorded;
 * cell identifiers only with the separate opt-in.
 */
public final class HistorySample {
    /** Set back to 1 before the first release (2026-11-02): the rows of the development versions are not kept. */
    public static final int SCHEMA_VERSION = 1;
    /** A cell measured longer ago than this is not recorded as the serving cell (the age at which lists count as stale). */
    static final long CELL_FRESH_MS = 30_000;
    public static final String[] COLUMNS = {
            "schema_version", "time_local", "time_epoch_ms", "sim_slot", "carrier", "default_data",
            "registration", "display_network", "display_override", "data_rat", "data_rat_source",
            "nr_status", "nr_source", "nr_cell_detected", "nr_signal_reported",
            "ca_status", "ca_source", "ca_evidence", "ca_components", "ca_aggregate_mhz", "reported_bandwidths_mhz",
            "serving_rat", "serving_band", "serving_band_source", "serving_channel", "serving_dl_mhz", "serving_ul_mhz",
            "lte_rsrp_dbm", "lte_rsrq_db", "lte_sinr_db", "nr_ss_rsrp_dbm", "nr_ss_rsrq_db", "nr_ss_sinr_db",
            "cells_status", "cells_age_s", "cells_stale", "lte_cell_count", "nr_cell_count",
            "serving_pci", "serving_tac", "serving_cell_id", "serving_ta",
            "ca_detail", "lat", "lon", "location_accuracy_m", "location_age_s",
            // Schema 2: the generated traffic's results since the data SIM's previous row.
            "traffic_on", "traffic_ok", "traffic_timeouts", "traffic_errors", "traffic_latency_ms_max",
            "traffic_failing_s", "net_validated"};

    /** A position fix for a row. */
    public static final class Position {
        final double lat, lon;
        final Float accuracyMeters;
        final long ageSeconds;
        public Position(double lat, double lon, Float accuracyMeters, long ageSeconds) {
            this.lat = lat; this.lon = lon; this.accuracyMeters = accuracyMeters; this.ageSeconds = ageSeconds;
        }
    }

    public final long epochMillis;
    public final int slot;
    public final String[] values;
    /** Fields that define a state change; signal levels and ages are excluded. */
    public final String signature;
    /** Short line for the History tab. */
    public final String summary;

    private HistorySample(long epochMillis, int slot, String[] values, String signature, String summary) {
        this.epochMillis = epochMillis; this.slot = slot; this.values = values;
        this.signature = signature; this.summary = summary;
    }

    /**
     * @param nowElapsed elapsedRealtime used for the snapshot's timestamps
     * @param includeLocation record the position (separate opt-in); {@code position} null then means no fix yet
     * @param includeIds record the registered cell's TAC, cell ID and TA (separate opt-in, the same set the bands
     *     screen masks): with the network code they identify one cell, whose place public cell databases list, and the
     *     TA the distance from it. The PCI (reused among nearby cells) is always recorded, as it is always shown.
     */
    public static HistorySample of(long epochMillis, TimeZone zone, int slot, String carrier, RadioSnapshot s,
                                   RadioAssessment a, long nowElapsed, boolean includeIds) {
        return of(epochMillis, zone, slot, carrier, s, a, nowElapsed, includeIds, false, null);
    }

    public static HistorySample of(long epochMillis, TimeZone zone, int slot, String carrier, RadioSnapshot s,
                                   RadioAssessment a, long nowElapsed, boolean includeIds,
                                   boolean includeLocation, Position position) {
        return of(epochMillis, zone, slot, carrier, s, a, nowElapsed, includeIds, includeLocation, position, null);
    }

    /**
     * @param traffic the generated traffic's results for this row, given only for the data SIM's rows (the requests go
     *     over its network); null for the other SIM, whose traffic columns are not_recorded
     */
    public static HistorySample of(long epochMillis, TimeZone zone, int slot, String carrier, RadioSnapshot s,
                                   RadioAssessment a, long nowElapsed, boolean includeIds,
                                   boolean includeLocation, Position position, TrafficStats.Window traffic) {
        String[] v = new String[COLUMNS.length];
        int i = 0;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
        f.setTimeZone(zone);
        v[i++] = String.valueOf(SCHEMA_VERSION);
        v[i++] = f.format(new Date(epochMillis));
        v[i++] = String.valueOf(epochMillis);
        v[i++] = String.valueOf(slot + 1);
        v[i++] = carrier;
        v[i++] = String.valueOf(s.defaultData);
        v[i++] = a.dataReg.name();
        v[i++] = a.displayNetwork == null ? null : a.displayNetwork.name();
        v[i++] = a.displayOverride == null ? null : a.displayOverride.name();
        v[i++] = a.observedDataRat.name();
        v[i++] = a.observedDataRatProvenance.name();
        v[i++] = a.nrStatus.name();
        v[i++] = a.nrProvenance.name();
        v[i++] = String.valueOf(a.nrCellDetected);
        v[i++] = String.valueOf(a.nrSignalReported);
        v[i++] = a.ca.status.name();
        v[i++] = a.ca.provenance.name();
        v[i++] = join(a.ca.evidence.toArray(), "|");
        v[i++] = components(a.ca);
        v[i++] = a.ca.aggregateBandwidthKhz == null ? null : Mhz.format(a.ca.aggregateBandwidthKhz);
        v[i++] = bandwidths(a.ca.reportedBandwidthsKhz);
        LteChannel lte = a.servingLte;
        NrChannel nr = a.servingNr;
        v[i++] = lte != null ? "LTE" : nr != null ? "NR" : null;
        v[i++] = lte != null ? band(lte.band, "B") : nr != null ? band(nr.band, "n") : null;
        v[i++] = lte != null ? lte.bandProvenance.name() : nr != null ? nr.bandProvenance.name() : null;
        v[i++] = lte != null ? num(lte.earfcn) : nr != null ? num(nr.nrArfcn) : null;
        v[i++] = lte != null ? khz(lte.downlinkKhz) : nr != null ? khz(nr.frequencyKhz) : null;
        v[i++] = lte != null ? khz(lte.uplinkKhz) : null;
        RadioSnapshot.Signal g = s.signal;
        v[i++] = g == null ? null : str(g.lteRsrp);
        v[i++] = g == null ? null : str(g.lteRsrq);
        v[i++] = g == null ? null : str(g.lteRssnr);
        v[i++] = g == null ? null : str(g.nrSsRsrp);
        v[i++] = g == null ? null : str(g.nrSsRsrq);
        v[i++] = g == null ? null : str(g.nrSsSinr);
        boolean cellsOk = s.cells.status == RadioSnapshot.Cells.Status.OK;
        v[i++] = s.cells.status.name();
        v[i++] = cellsOk ? String.valueOf(Math.max(0, (nowElapsed - s.cells.observedAtMillis) / 1000)) : null;
        v[i++] = cellsOk ? String.valueOf(a.cellsStale) : null;
        v[i++] = cellsOk ? String.valueOf(s.cells.lte.size()) : null;
        v[i++] = cellsOk ? String.valueOf(s.cells.nr.size()) : null;
        Integer pci = null, tac = null, ta = null; Long id = null;
        // From a stale list these would be another time's cell, written as if current: left unknown, while
        // cells_age_s and cells_stale still show what the list was.
        if (cellsOk && !a.cellsStale) {
            // Only cells of the serving RAT: an NR leg never stands in for a missing LTE cell, as serving_ta's meaning
            // (LTE TA steps or NR's reported value) is told by serving_rat. Without a serving RAT nothing is picked.
            LteCell l = lte != null ? servingLte(s.cells.lte, lte.earfcn) : null;
            NrCell n = lte == null && nr != null ? servingNr(s.cells.nr, nr.nrArfcn) : null;
            // The list's time is its newest cell's: an old serving cell listed with a fresh neighbour is not current.
            if (l != null && nowElapsed - l.observedAtMillis > CELL_FRESH_MS) l = null;
            if (n != null && nowElapsed - n.observedAtMillis > CELL_FRESH_MS) n = null;
            if (l != null) {
                pci = l.pci; tac = l.tac; id = l.ci; ta = l.ta;
            } else if (n != null) {
                pci = n.pci; tac = n.tac; id = n.nci; ta = n.ta;
            }
        }
        v[i++] = str(pci);
        v[i++] = includeIds ? str(tac) : HistoryCsv.NOT_RECORDED;
        v[i++] = includeIds ? (id == null ? null : String.valueOf(id)) : HistoryCsv.NOT_RECORDED;
        // As reported (LTE: TA steps; NR: getTimingAdvanceMicros, unit unsettled), told apart by serving_rat. No distance:
        // on the Pixel 10a about 8 % of LTE rows in Tokyo came to 10 km or more.
        v[i++] = includeIds ? str(ta) : HistoryCsv.NOT_RECORDED;
        v[i++] = detail(a.ca);
        if (!includeLocation) {
            v[i++] = HistoryCsv.NOT_RECORDED; v[i++] = HistoryCsv.NOT_RECORDED;
            v[i++] = HistoryCsv.NOT_RECORDED; v[i++] = HistoryCsv.NOT_RECORDED;
        } else if (position != null) {
            v[i++] = String.format(Locale.ROOT, "%.6f", position.lat);
            v[i++] = String.format(Locale.ROOT, "%.6f", position.lon);
            v[i++] = position.accuracyMeters == null ? null : String.valueOf(Math.round(position.accuracyMeters));
            v[i++] = String.valueOf(position.ageSeconds);
        } else {
            i += 4;
        }
        if (traffic == null) {
            for (int k = 0; k < 7; k++) v[i++] = HistoryCsv.NOT_RECORDED;
        } else {
            v[i++] = String.valueOf(traffic.on);
            if (traffic.on) {
                v[i++] = String.valueOf(traffic.ok);
                v[i++] = String.valueOf(traffic.timeouts);
                v[i++] = String.valueOf(traffic.errors);
                v[i++] = str(traffic.latencyMaxMs);
                // Empty (unknown) is wrong here: the last request answered, so nothing is failing.
                v[i++] = traffic.failingForSeconds == null ? "0" : String.valueOf(traffic.failingForSeconds);
                v[i] = str(traffic.validated);
            } else {
                for (int k = 0; k < 6; k++) v[i++] = HistoryCsv.NOT_RECORDED;
            }
        }

        // The serving cell's PCI too: a handover (a train ride) is a change worth a row, even on the same band.
        // Traffic starting or stopping, and requests starting or ceasing to fail, are worth a row of their own.
        String signature = join(new Object[]{a.dataReg, a.displayOverride, a.displayNetwork, a.observedDataRat,
                a.nrStatus, a.ca.status, v[18], v[20], v[24], s.defaultData, s.cells.status, pci,
                traffic != null && traffic.on, traffic != null && traffic.failing()}, "|");
        String summary = "SIM " + (slot + 1) + " " + carrier + " · "
                + (a.displayOverride == null ? "?" : a.displayOverride.name()) + " · " + a.observedDataRat.name()
                + " · " + a.nrStatus.name() + " · " + (v[22] == null ? "-" : v[22]) + " " + (v[25] == null ? "" : v[25] + "MHz");
        return new HistorySample(epochMillis, slot, v, signature, summary);
    }

    private static String components(CaAssessment ca) {
        if (ca.components.isEmpty()) return null;
        StringBuilder b = new StringBuilder();
        for (CaAssessment.Component c : ca.components) {
            if (b.length() > 0) b.append('|');
            b.append(c.primary ? "PCC " : "SCC ").append(c.rat.name()).append(' ');
            if (c.lte != null) b.append(band(c.lte.band, "B")).append(' ').append(c.lte.earfcn).append(' ').append(khz(c.lte.downlinkKhz));
            else if (c.nr != null) b.append(band(c.nr.band, "n")).append(' ').append(c.nr.nrArfcn).append(' ').append(khz(c.nr.frequencyKhz));
            if (c.bandwidthKhz > 0) b.append(' ').append(Mhz.format(c.bandwidthKhz)).append("MHz");
        }
        return b.toString();
    }

    /**
     * The serving LTE cell: the primary serving one, else the only registered or serving cell on the serving channel
     * (any channel when that is unknown); null when none or several. A secondary cell listed first is never taken
     * for it, so PCI, TAC, cell ID and TA all come from the same, primary cell.
     */
    public static LteCell servingLte(java.util.List<LteCell> cells, Integer earfcn) {
        for (LteCell c : cells) if (c.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.PRIMARY_SERVING) return c;
        LteCell hit = null;
        for (LteCell c : cells) {
            if (c.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.UNREGISTERED) continue;
            if (earfcn != null && (c.channel == null || c.channel.earfcn != earfcn)) continue;
            if (hit != null) return null;
            hit = c;
        }
        return hit;
    }

    /** The same rule for NR cells, on the NR-ARFCN. */
    public static NrCell servingNr(java.util.List<NrCell> cells, Integer nrArfcn) {
        for (NrCell c : cells) if (c.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.PRIMARY_SERVING) return c;
        NrCell hit = null;
        for (NrCell c : cells) {
            if (c.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.UNREGISTERED) continue;
            if (nrArfcn != null && (c.channel == null || c.channel.nrArfcn != nrArfcn)) continue;
            if (hit != null) return null;
            hit = c;
        }
        return hit;
    }

    /**
     * The verified components as JSON, one object each, for a viewer that draws the same blocks as the bands
     * screen: cc (PCC/SCC), rat, band, arfcn, dl_mhz, bw_mhz, pci, rsrp, rsrq, sinr, rssi (with rssi_derived when
     * computed). Unreported values are left out; null when no component is verified.
     */
    public static String detail(CaAssessment ca) {
        if (ca.components.isEmpty()) return null;
        org.json.JSONArray out = new org.json.JSONArray();
        try {
            for (CaAssessment.Component c : ca.components) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("cc", c.primary ? "PCC" : "SCC");
                o.put("rat", c.rat.name());
                if (c.lte != null) {
                    o.put("band", band(c.lte.band, "B"));
                    if (c.lte.earfcn >= 0) o.put("arfcn", c.lte.earfcn);
                    o.putOpt("dl_mhz", khz(c.lte.downlinkKhz));
                } else if (c.nr != null) {
                    o.put("band", band(c.nr.band, "n"));
                    if (c.nr.nrArfcn >= 0) o.put("arfcn", c.nr.nrArfcn);
                    o.putOpt("dl_mhz", khz(c.nr.frequencyKhz));
                }
                if (c.bandwidthKhz > 0) o.put("bw_mhz", Mhz.format(c.bandwidthKhz));
                o.putOpt("pci", c.pci);
                if (c.signal != null) {
                    o.putOpt("rsrp", c.signal.rsrp);
                    o.putOpt("rsrq", c.signal.rsrq);
                    o.putOpt("sinr", c.signal.sinr);
                    o.putOpt("rssi", c.signal.rssi);
                    if (c.signal.rssi != null && c.signal.rssiDerived) o.put("rssi_derived", true);
                }
                out.put(o);
            }
        } catch (org.json.JSONException e) {
            return null;
        }
        return out.toString();
    }

    private static String bandwidths(int[] khz) {
        if (khz == null || khz.length == 0) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < khz.length; i++) b.append(i == 0 ? "" : "+").append(Mhz.format(khz[i]));
        return b.toString();
    }

    private static String band(int band, String prefix) { return band <= 0 ? null : prefix + band; }
    private static String num(int v) { return v < 0 ? null : String.valueOf(v); }
    private static String khz(long v) { return v < 0 ? null : Mhz.format(v); }
    private static String str(Object v) { return v == null ? null : String.valueOf(v); }

    private static String join(Object[] parts, String sep) {
        if (parts.length == 0) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) b.append(i == 0 ? "" : sep).append(parts[i]);
        return b.toString();
    }
}
