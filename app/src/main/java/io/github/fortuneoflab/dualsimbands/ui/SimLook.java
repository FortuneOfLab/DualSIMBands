package io.github.fortuneoflab.dualsimbands.ui;

import android.content.Context;
import androidx.core.content.ContextCompat;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.radio.SubscriptionRepository;

/**
 * Name and colour for a SIM card. The name is Android's SIM label alone (decision of 2026-11-01: the carrier name after
 * it was dropped), or the carrier name Android reports when there is no label. The colour is either the network
 * operator's (MCC-MNC; MVNOs and sub-brands share their host network's code, so they get the host's colour) or
 * Android's SIM colour.
 */
final class SimLook {
    private SimLook() {}

    static String name(SubscriptionRepository.Sim sim) { return name(sim.displayName, sim.carrierName); }

    /** The label, or the carrier name when the label is empty; spaces around dropped (a typed label can end with one). */
    static String name(String label, String carrier) {
        label = label.trim();
        return label.isEmpty() ? carrier.trim() : label;
    }

    /** Whether the label and the carrier name are the same name (spaces around and letter case aside). */
    static boolean sameName(String label, String carrier) {
        return label != null && carrier != null && label.trim().equalsIgnoreCase(carrier.trim());
    }

    static int color(Context c, SubscriptionRepository.Sim sim) {
        if (AppPrefs.simLookAndroid(c) && sim.iconTint != 0) return sim.iconTint;
        return ContextCompat.getColor(c, networkColor(sim.mcc, sim.mnc));
    }

    /** Japanese network operators by MCC-MNC; anything else is neutral. */
    static int networkColor(String mcc, String mnc) {
        if (!"440".equals(mcc) && !"441".equals(mcc)) return R.color.chart_unknown;
        switch (mcc + "-" + mnc) {
            case "440-10": return R.color.sim_docomo;
            case "440-50": case "440-51": case "440-52": case "440-53": case "440-54": return R.color.sim_kddi;
            case "440-20": case "440-21": return R.color.sim_softbank;
            case "440-11": return R.color.sim_rakuten;
            default: return R.color.chart_unknown;
        }
    }
}
