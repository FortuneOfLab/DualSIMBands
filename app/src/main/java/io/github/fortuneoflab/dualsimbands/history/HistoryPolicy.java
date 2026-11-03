package io.github.fortuneoflab.dualsimbands.history;

/**
 * Records state changes, plus one sample a minute for signal trends, plus one each time the device has moved 50 m
 * (when the position is recorded); never more often than every 5 s.
 */
public final class HistoryPolicy {
    public static final long MIN_INTERVAL_MS = 5_000, HEARTBEAT_MS = 60_000;
    public static final double MOVE_METERS = 50;

    private HistoryPolicy() {}

    public static boolean shouldRecord(String lastSignature, long lastAt, String signature, long now) {
        return shouldRecord(lastSignature, lastAt, signature, now, null);
    }

    /** A fix less precise than this is kept in the row but never used to tell that the device moved. */
    public static final float USABLE_ACCURACY_METERS = 50;

    /**
     * The distance to judge a move by, or null when it cannot be told from position noise: both fixes need a known
     * accuracy of {@link #USABLE_ACCURACY_METERS} or better, and the distance has to exceed both radii together
     * (indoors a standing device's fix wanders by tens of metres).
     */
    public static Double usableDistance(double meters, Float accuracyFrom, Float accuracyTo) {
        if (!usable(accuracyFrom) || !usable(accuracyTo)) return null;
        return meters > accuracyFrom + accuracyTo ? meters : null;
    }

    public static boolean usable(Float accuracy) { return accuracy != null && accuracy <= USABLE_ACCURACY_METERS; }

    /** Rows older than this are deleted: the retention in days back from now. */
    public static long cutoff(long nowMillis, int days) { return nowMillis - days * 24L * 60 * 60_000L; }

    /** @param movedMeters distance from the last recorded position ({@link #usableDistance}), or null when unknown */
    public static boolean shouldRecord(String lastSignature, long lastAt, String signature, long now, Double movedMeters) {
        if (lastSignature == null) return true;
        long since = now - lastAt;
        if (since < MIN_INTERVAL_MS) return false;
        return !lastSignature.equals(signature) || since >= HEARTBEAT_MS
                || (movedMeters != null && movedMeters >= MOVE_METERS);
    }
}
