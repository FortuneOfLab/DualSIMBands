package io.github.fortuneoflab.dualsimbands.radio;

/**
 * Which cell lists to take. Registering for cell-info changes delivers at once the list the platform last announced,
 * and with the screen off that one is not refreshed by polling: on the Pixel 10a it was 12 minutes old with nothing
 * polling, and rows recorded from such a list carried hours-old PCI, band and composition. A list older than the one
 * held, or than {@link #STALE_MS}, is therefore not taken; the next poll brings a current one within seconds.
 */
public final class CellsFilter {
    /** The age at which cell lists count as stale everywhere else (history, the bands screen, the monitor). */
    static final long STALE_MS = 30_000;

    private CellsFilter() {}

    public static boolean take(RadioSnapshot.Cells held, RadioSnapshot.Cells next, long nowMillis) {
        // An error or a missing permission is news in itself.
        if (next.status != RadioSnapshot.Cells.Status.OK) return true;
        if (nowMillis - next.observedAtMillis > STALE_MS) return false;
        return held == null || held.status != RadioSnapshot.Cells.Status.OK || next.observedAtMillis >= held.observedAtMillis;
    }
}
