package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * After a successful write, polls the carrier name Android reports for each applied SIM.
 * This is the name source the status bar typically uses, not a capture of the status bar itself.
 */
final class DisplayCheck {
    private static final int ATTEMPTS = 10;
    private static final long INTERVAL_MS = 1000;

    private DisplayCheck() {}

    static final class Outcome {
        final String text;
        /** At least one custom label is written but not yet shown by Android. */
        final boolean pending;
        Outcome(String text, boolean pending) { this.text = text; this.pending = pending; }
    }

    /** Runs on a worker thread. */
    static Outcome run(Context c) throws InterruptedException {
        SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
        JSONArray profiles = Store.profiles(c);
        List<String> lines = new ArrayList<>();
        boolean pending = false;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            lines.clear();
            pending = false;
            List<SubscriptionInfo> active;
            try { active = sm.getActiveSubscriptionInfoList(); } catch (SecurityException e) { active = null; }
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject p = profiles.optJSONObject(i);
                if (p == null) continue;
                SubscriptionInfo sim = null;
                if (active != null) for (SubscriptionInfo s : active) {
                    try { if (Store.matches(p, s)) { sim = s; break; } } catch (Exception ignored) { }
                }
                if (sim == null) continue;
                CharSequence actual = sim.getCarrierName();
                boolean rename = p.optBoolean("rename", false);
                LabelVerdict.Result r = LabelVerdict.evaluate(rename, p.optString("label", ""), actual);
                if (r == LabelVerdict.Result.DIFFERS) pending = true;
                lines.add(LabelVerdict.describe("SIM " + (sim.getSimSlotIndex() + 1), r, actual));
            }
            if (!pending) break;
            Thread.sleep(INTERVAL_MS);
        }
        if (lines.isEmpty()) return new Outcome("", false);
        StringBuilder b = new StringBuilder(t("\n表示名の確認\n", "\nCarrier name check\n"));
        for (String line : lines) b.append(line).append('\n');
        return new Outcome(b.toString(), pending);
    }
}
