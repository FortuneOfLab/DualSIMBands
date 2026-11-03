package io.github.fortuneoflab.dualsimbands.radio;

/** Role of an observed cell. Only the serving roles are reported explicitly by the platform. */
public enum CellRole {
    /** CellInfo.CONNECTION_PRIMARY_SERVING. */
    PRIMARY_SERVING,
    /** CellInfo.CONNECTION_SECONDARY_SERVING (e.g. a CA secondary cell or NSA NR leg). */
    SECONDARY_SERVING,
    /** isRegistered() without an explicit connection status. */
    REGISTERED,
    /** Not registered and no serving status: a neighbour or an unattributed component. Not "disconnected". */
    UNREGISTERED;

    static final int CONNECTION_NONE = 0, CONNECTION_PRIMARY = 1, CONNECTION_SECONDARY = 2;

    public static CellRole from(boolean registered, int connectionStatus) {
        if (connectionStatus == CONNECTION_PRIMARY) return PRIMARY_SERVING;
        if (connectionStatus == CONNECTION_SECONDARY) return SECONDARY_SERVING;
        return registered ? REGISTERED : UNREGISTERED;
    }

    public boolean isServing() { return this == PRIMARY_SERVING || this == SECONDARY_SERVING; }
}
