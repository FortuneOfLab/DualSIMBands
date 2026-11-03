package io.github.fortuneoflab.dualsimbands.ui;

import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.SubscriptionRepository;
import java.util.List;
import java.util.Map;

/**
 * Sample SIMs and radio states for screenshots, drawn on the bands screen instead of the device's. Only the demo
 * build (its own application ID, beside the real app) sets {@link #source}; in the other builds it stays null and
 * nothing here runs.
 */
public final class Demo {
    private Demo() {}

    public interface Source {
        List<SubscriptionRepository.Sim> sims();
        /** By subscription ID, observed at {@code nowMillis} (elapsedRealtime), so they stay fresh. */
        Map<Integer, RadioSnapshot> snapshots(long nowMillis);
        /** The data SIM's generated traffic in the header; null for none. */
        RadioCard.Traffic traffic();
    }

    public static volatile Source source;
}
