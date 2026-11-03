package io.github.fortuneoflab.dualsimbands.radio;

/**
 * SignalStrength arrives only when the modem reports it, and while the screen is off (not charging) Android drops
 * signal-strength reports from the modem's indication filter: the last value would then stand for hours. Polled
 * CellInfo still carries fresh measurements of the serving cells, so when SignalStrength is older than
 * {@link #MAX_AGE_MS}, the one primary serving LTE cell and the one serving NR cell with a measurement stand in, each
 * only when its own CellInfo time is newer than the report and within {@link #FRESH_MS} (observed on the Pixel 10a:
 * the screen-off filter, CellInfo updated every poll, and both sources agreeing once the screen came back on).
 */
public final class SignalFallback {
    /** Two cell polls: younger SignalStrength is kept, as it is the platform's own serving-cell report. */
    static final long MAX_AGE_MS = 20_000;
    /** A cell measured longer ago than this does not stand in (the same age at which cell lists count as stale). */
    static final long FRESH_MS = 30_000;

    private SignalFallback() {}

    public static RadioSnapshot.Signal effective(RadioSnapshot.Signal reported, RadioSnapshot.Cells cells, long nowMillis) {
        if (cells == null || cells.status != RadioSnapshot.Cells.Status.OK) return reported;
        if (reported != null && nowMillis - reported.observedAtMillis <= MAX_AGE_MS) return reported;
        long reportedAt = reported == null ? Long.MIN_VALUE : reported.observedAtMillis;
        LteCell l = null;
        int lteCount = 0;
        for (LteCell c : cells.lte) if (c.role == CellRole.PRIMARY_SERVING && c.rsrp != null) { l = c; lteCount++; }
        // The list's time is its newest cell's: a fresh neighbour must not make an old serving cell look fresh.
        if (lteCount != 1 || !newer(l.observedAtMillis, reportedAt, nowMillis)) l = null;
        NrCell n = null;
        int nrCount = 0;
        for (NrCell c : cells.nr) if (c.role.isServing() && c.ssRsrp != null) { n = c; nrCount++; }
        if (nrCount != 1 || !newer(n.observedAtMillis, reportedAt, nowMillis)) n = null;
        // Nothing to stand in: the old report is all there is, and its age is still known from observedAtMillis.
        if (l == null && n == null) return reported;
        long at = l == null ? n.observedAtMillis : n == null ? l.observedAtMillis : Math.min(l.observedAtMillis, n.observedAtMillis);
        return new RadioSnapshot.Signal(l == null ? null : l.rsrp, l == null ? null : l.rsrq, l == null ? null : l.rssnr,
                l == null ? null : l.rssi, n == null ? null : n.ssRsrp, n == null ? null : n.ssRsrq,
                n == null ? null : n.ssSinr, at);
    }

    private static boolean newer(long cellAt, long reportedAt, long nowMillis) {
        return cellAt > reportedAt && nowMillis - cellAt <= FRESH_MS;
    }
}
