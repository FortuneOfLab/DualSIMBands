package io.github.fortuneoflab.dualsimbands.radio;

/** Radio access technology reported by ServiceState / TelephonyManager network types. */
public enum Rat {
    UNKNOWN, GSM, UMTS, CDMA, LTE, NR, IWLAN;

    /** Maps TelephonyManager.NETWORK_TYPE_* values (stable public constants). */
    public static Rat fromNetworkType(int type) {
        switch (type) {
            case 1: case 2: case 16: return GSM;                       // GPRS, EDGE, GSM
            case 3: case 8: case 9: case 10: case 15: case 17: return UMTS; // UMTS, HSDPA, HSUPA, HSPA, HSPAP, TD_SCDMA
            case 4: case 5: case 6: case 7: case 12: case 14: return CDMA;
            case 13: case 19: return LTE;                              // LTE, LTE_CA (hidden constant 19)
            case 20: return NR;
            case 18: return IWLAN;
            default: return UNKNOWN;
        }
    }
}
