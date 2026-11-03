package io.github.fortuneoflab.dualsimbands.ui;

import android.content.Context;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.radio.DisplayOverride;
import io.github.fortuneoflab.dualsimbands.radio.LteCell;
import io.github.fortuneoflab.dualsimbands.radio.NrCell;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.Rat;
import io.github.fortuneoflab.dualsimbands.radio.SubscriptionRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two made-up SIMs. The data SIM: EN-DC with LTE B1 + B3 and NR n77 (Sub-6) + n77 below 3.6 GHz (refarmed), as the
 * privileged channel report gives it. The other SIM: idle on LTE B3. Names, cell IDs and areas are invented.
 */
final class DemoData implements Demo.Source {
    private static final int DATA = 101, OTHER = 102;
    private static final int PRIMARY = 1, SECONDARY = 2, NONE = 0;
    private final Context context;

    DemoData(Context context) { this.context = context.getApplicationContext(); }

    @Override public List<SubscriptionRepository.Sim> sims() {
        return Arrays.asList(
                new SubscriptionRepository.Sim(DATA, 0, "Carrier A", "440", "54", "Sample A", true),
                new SubscriptionRepository.Sim(OTHER, 1, "Carrier B", "440", "10", "Sample B", false));
    }

    @Override public Map<Integer, RadioSnapshot> snapshots(long now) {
        Map<Integer, RadioSnapshot> out = new HashMap<>();
        out.put(DATA, data(now));
        out.put(OTHER, other(now));
        return out;
    }

    @Override public RadioCard.Traffic traffic() {
        String amount = "128 KB";
        return new RadioCard.Traffic(amount, context.getString(R.string.radio_traffic_spoken, amount), false);
    }

    private static RadioSnapshot data(long now) {
        RadioSnapshot.Service service = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 100, new int[]{20_000, 15_000}, now);
        RadioSnapshot.Display display = new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NR_ADVANCED, now);
        RadioSnapshot.Signal signal = new RadioSnapshot.Signal(-86, -9, 18, -57, -93, -11, 14, now);
        List<LteCell> lte = new ArrayList<>();
        lte.add(LteCell.fromRaw(true, PRIMARY, 100, new int[]{1}, 105, 4321, 98_765_432, 20_000, -86, -9, -57, 18,
                "440", "54", 4, now));
        lte.add(LteCell.fromRaw(false, SECONDARY, 1500, new int[]{3}, 211, Integer.MAX_VALUE, Integer.MAX_VALUE, 15_000,
                -90, -10, -60, 15, null, null, Integer.MAX_VALUE, now));
        lte.add(LteCell.fromRaw(false, NONE, 100, new int[]{1}, 318, Integer.MAX_VALUE, Integer.MAX_VALUE, 20_000,
                -101, -13, -70, Integer.MAX_VALUE, null, null, Integer.MAX_VALUE, now));
        lte.add(LteCell.fromRaw(false, NONE, 1500, new int[]{3}, 74, Integer.MAX_VALUE, Integer.MAX_VALUE, 15_000,
                -106, -14, -75, Integer.MAX_VALUE, null, null, Integer.MAX_VALUE, now));
        List<NrCell> nr = new ArrayList<>();
        nr.add(NrCell.fromRaw(false, SECONDARY, 647328, new int[]{77}, 603, 0, 0, -93, -11, 14, null, null,
                Integer.MAX_VALUE, now));
        nr.add(NrCell.fromRaw(false, SECONDARY, 628000, new int[]{77}, 330, 0, 0, -97, -12, 11, null, null,
                Integer.MAX_VALUE, now));
        List<RadioSnapshot.PhysicalChannel> channels = Arrays.asList(
                new RadioSnapshot.PhysicalChannel(Rat.LTE, true, 100, 20_000, 1, 105),
                new RadioSnapshot.PhysicalChannel(Rat.LTE, false, 1500, 15_000, 3, 211),
                new RadioSnapshot.PhysicalChannel(Rat.NR, false, 647328, 100_000, 77, 603),
                new RadioSnapshot.PhysicalChannel(Rat.NR, false, 628000, 40_000, 77, 330));
        return new RadioSnapshot(DATA, 0, true, service, display, signal,
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, lte, nr, now), channels, now);
    }

    private static RadioSnapshot other(long now) {
        RadioSnapshot.Service service = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, 1850, new int[]{20_000}, now);
        RadioSnapshot.Display display = new RadioSnapshot.Display(Rat.LTE, DisplayOverride.NONE, now);
        RadioSnapshot.Signal signal = new RadioSnapshot.Signal(-97, -10, 9, -66, null, null, null, now);
        List<LteCell> lte = new ArrayList<>();
        lte.add(LteCell.fromRaw(true, PRIMARY, 1850, new int[]{3}, 77, 5678, 12_345_678, 20_000, -97, -10, -66, 9,
                "440", "10", Integer.MAX_VALUE, now));
        lte.add(LteCell.fromRaw(false, NONE, 6075, new int[]{19}, 152, Integer.MAX_VALUE, Integer.MAX_VALUE, 15_000,
                -103, -12, -71, Integer.MAX_VALUE, null, null, Integer.MAX_VALUE, now));
        List<RadioSnapshot.PhysicalChannel> channels = Arrays.asList(
                new RadioSnapshot.PhysicalChannel(Rat.LTE, true, 1850, 20_000, 3, 77));
        return new RadioSnapshot(OTHER, 1, false, service, display, signal,
                new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, lte, new ArrayList<>(), now), channels, now);
    }
}
