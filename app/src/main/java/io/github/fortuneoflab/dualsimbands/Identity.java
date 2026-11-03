package io.github.fortuneoflab.dualsimbands;

/** Fail closed when a stored subscription no longer matches the active SIM. */
public final class Identity {
    private Identity() {}
    public static boolean matches(int savedId, int savedCarrier, String savedMcc, String savedMnc,
                                  int activeId, int activeCarrier, String activeMcc, String activeMnc) {
        if (savedId < 0 || activeId != savedId) return false;
        return sameCarrier(savedCarrier, savedMcc, savedMnc, activeCarrier, activeMcc, activeMnc);
    }
    /**
     * Carrier ID plus MCC/MNC. Carrier ID alone is not enough: on the reference device both the au and
     * docomo subscriptions report carrier ID 2106. Used only to offer an explicit re-assignment.
     */
    public static boolean sameCarrier(int savedCarrier, String savedMcc, String savedMnc,
                                      int activeCarrier, String activeMcc, String activeMnc) {
        return savedCarrier == activeCarrier && equal(savedMcc, activeMcc) && equal(savedMnc, activeMnc);
    }
    private static boolean equal(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }
}
