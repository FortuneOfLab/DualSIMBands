package io.github.fortuneoflab.dualsimbands.radio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Active subscriptions without ICCID/IMSI. Subscription IDs are runtime identifiers and may change
 * after reboot, SIM swap or eSIM reprovisioning, so they are never used alone as a stable identity.
 */
public final class SubscriptionRepository {
    public static final class Sim {
        public final int subscriptionId, slotIndex, carrierId;
        public final String carrierName, mcc, mnc;
        /** Android's SIM label (Settings > SIM > label and colour); "" when none. */
        public final String displayName;
        /** Android's SIM colour, 0 when none. */
        public final int iconTint;
        public final boolean embedded;
        Sim(SubscriptionInfo info) {
            subscriptionId = info.getSubscriptionId();
            slotIndex = info.getSimSlotIndex();
            carrierId = info.getCarrierId();
            CharSequence name = info.getCarrierName();
            carrierName = name == null ? "" : name.toString();
            mcc = info.getMccString() == null ? "" : info.getMccString();
            mnc = info.getMncString() == null ? "" : info.getMncString();
            embedded = info.isEmbedded();
            CharSequence label = info.getDisplayName();
            displayName = label == null ? "" : label.toString();
            iconTint = info.getIconTint();
        }
        /** A sample SIM (the demo build's screenshots). */
        public Sim(int subscriptionId, int slotIndex, String carrierName, String mcc, String mnc, String displayName,
                   boolean embedded) {
            this.subscriptionId = subscriptionId; this.slotIndex = slotIndex; carrierId = -1;
            this.carrierName = carrierName; this.mcc = mcc; this.mnc = mnc; this.displayName = displayName;
            iconTint = 0; this.embedded = embedded;
        }
    }

    public interface Listener { void onSubscriptionsChanged(List<Sim> sims); }

    private final Context context;
    private final SubscriptionManager manager;
    private SubscriptionManager.OnSubscriptionsChangedListener registered;

    public SubscriptionRepository(Context context) {
        this.context = context.getApplicationContext();
        this.manager = this.context.getSystemService(SubscriptionManager.class);
    }

    public boolean hasPhonePermission() {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Active SIMs ordered by slot; empty without READ_PHONE_STATE. */
    public List<Sim> current() {
        if (!hasPhonePermission()) return Collections.emptyList();
        List<SubscriptionInfo> infos;
        try { infos = manager.getActiveSubscriptionInfoList(); }
        catch (SecurityException e) { return Collections.emptyList(); }
        if (infos == null) return Collections.emptyList();
        List<Sim> sims = new ArrayList<>();
        for (SubscriptionInfo info : infos) sims.add(new Sim(info));
        sims.sort((a, b) -> Integer.compare(a.slotIndex, b.slotIndex));
        return sims;
    }

    /** The user's default data subscription, or INVALID_SUBSCRIPTION_ID (-1). */
    public int defaultDataSubscriptionId() { return SubscriptionManager.getDefaultDataSubscriptionId(); }

    public void start(Executor executor, Listener listener) {
        stop();
        registered = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override public void onSubscriptionsChanged() { listener.onSubscriptionsChanged(current()); }
        };
        manager.addOnSubscriptionsChangedListener(executor, registered);
    }

    public void stop() {
        if (registered != null) manager.removeOnSubscriptionsChangedListener(registered);
        registered = null;
    }
}
