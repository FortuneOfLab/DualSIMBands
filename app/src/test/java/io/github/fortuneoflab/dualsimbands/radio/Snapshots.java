package io.github.fortuneoflab.dualsimbands.radio;

import java.util.ArrayList;
import java.util.List;

/** Test builder for RadioSnapshot. Times are elapsedRealtime-style milliseconds. */
final class Snapshots {
    static final long NOW = 1_000_000L;
    static final long STALE_AFTER = 30_000L;

    RadioSnapshot.Service service = service(Rat.LTE, 100);
    boolean defaultData = true;
    RadioSnapshot.Display display;
    RadioSnapshot.Signal signal;
    RadioSnapshot.Cells.Status cellStatus = RadioSnapshot.Cells.Status.NOT_REQUESTED;
    long cellsAt = NOW;
    final List<LteCell> lte = new ArrayList<>();
    final List<NrCell> nr = new ArrayList<>();
    List<RadioSnapshot.PhysicalChannel> physical;
    long physicalAt = NOW;
    /** When the channels were reported; -1: the same as {@link #physicalAt}. */
    long physicalReportedAt = -1;

    static RadioSnapshot.Service service(Rat dataRat, int channel, int... bandwidthsKhz) {
        return new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE, RadioSnapshot.RegState.IN_SERVICE,
                Rat.LTE, dataRat, channel, bandwidthsKhz, NOW);
    }

    static RadioSnapshot.Service noService(RadioSnapshot.RegState state) {
        return new RadioSnapshot.Service(state, state, Rat.UNKNOWN, Rat.UNKNOWN, -1, new int[0], NOW);
    }

    Snapshots display(DisplayOverride o) { display = new RadioSnapshot.Display(Rat.LTE, o, NOW); return this; }

    Snapshots lteCell(boolean registered, int connection, int earfcn, int bandwidthKhz) {
        cellStatus = RadioSnapshot.Cells.Status.OK;
        lte.add(LteCell.fromRaw(registered, connection, earfcn, null, 1, 1, 1, bandwidthKhz, -90, -10, -60, 10, cellsAt));
        return this;
    }

    Snapshots nrCell(boolean registered, int connection, int arfcn) {
        cellStatus = RadioSnapshot.Cells.Status.OK;
        nr.add(NrCell.fromRaw(registered, connection, arfcn, null, 1, 1, 1L, -95, -11, 5, cellsAt));
        return this;
    }

    Snapshots physical(Rat rat, boolean primary, int channel, int bandwidthKhz) {
        return physical(rat, primary, channel, bandwidthKhz, 0);
    }

    Snapshots physical(Rat rat, boolean primary, int channel, int bandwidthKhz, int band) {
        if (physical == null) physical = new ArrayList<>();
        physical.add(new RadioSnapshot.PhysicalChannel(rat, primary, channel, bandwidthKhz, band));
        return this;
    }

    RadioSnapshot build() {
        RadioSnapshot.Cells cells = cellStatus == RadioSnapshot.Cells.Status.OK
                ? new RadioSnapshot.Cells(cellStatus, lte, nr, cellsAt) : RadioSnapshot.Cells.unavailable(cellStatus);
        long at = physical == null ? -1 : physicalAt;
        return new RadioSnapshot(2, 0, defaultData, service, display, signal, cells, physical, at,
                physical == null || physicalReportedAt < 0 ? at : physicalReportedAt);
    }

    RadioAssessment reduce() { return RadioStateReducer.reduce(build(), NOW, STALE_AFTER); }
}
