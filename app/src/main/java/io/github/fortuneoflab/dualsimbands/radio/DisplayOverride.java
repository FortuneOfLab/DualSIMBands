package io.github.fortuneoflab.dualsimbands.radio;

/**
 * TelephonyDisplayInfo override network type: what the status bar shows, not what the radio uses.
 * The platform may keep showing 5G after the NR link is released (display hysteresis).
 */
public enum DisplayOverride {
    NONE, LTE_CA, LTE_ADVANCED_PRO, NR_NSA, NR_NSA_MMWAVE, NR_ADVANCED, UNKNOWN;

    /** Maps TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_* values. */
    public static DisplayOverride fromPlatform(int value) {
        switch (value) {
            case 0: return NONE;
            case 1: return LTE_CA;
            case 2: return LTE_ADVANCED_PRO;
            case 3: return NR_NSA;
            case 4: return NR_NSA_MMWAVE;
            case 5: return NR_ADVANCED;
            default: return UNKNOWN;
        }
    }

    public boolean indicatesNr() { return this == NR_NSA || this == NR_NSA_MMWAVE || this == NR_ADVANCED; }
}
