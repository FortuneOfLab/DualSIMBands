package io.github.fortuneoflab.dualsimbands.radio;

/**
 * How a value was obtained. Freshness (STALE) is tracked separately from timestamps,
 * because a verified value can still be old.
 */
public enum Provenance {
    /** Reported directly by a platform API that explicitly exposes this fact. */
    VERIFIED,
    /** A hint such as the status-bar display override; not proof of the radio state. */
    INDICATED,
    /** Exact standard arithmetic on a verified input (e.g. EARFCN to MHz). */
    DERIVED,
    /** Could not be determined from the available data. */
    UNKNOWN,
    /** The platform, permission state or device does not provide this data. */
    UNSUPPORTED
}
