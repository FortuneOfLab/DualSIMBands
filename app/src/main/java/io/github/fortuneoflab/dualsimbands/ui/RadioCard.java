package io.github.fortuneoflab.dualsimbands.ui;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import io.github.fortuneoflab.dualsimbands.R;
import io.github.fortuneoflab.dualsimbands.radio.CaAssessment;
import io.github.fortuneoflab.dualsimbands.radio.DisplayOverride;
import io.github.fortuneoflab.dualsimbands.radio.LteCell;
import io.github.fortuneoflab.dualsimbands.radio.LteChannel;
import io.github.fortuneoflab.dualsimbands.radio.Mhz;
import io.github.fortuneoflab.dualsimbands.radio.NrCell;
import io.github.fortuneoflab.dualsimbands.radio.NrChannel;
import io.github.fortuneoflab.dualsimbands.radio.Provenance;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.Rat;
import io.github.fortuneoflab.dualsimbands.radio.SubscriptionRepository;
import io.github.fortuneoflab.dualsimbands.ui.graph.CarrierBarView;
import io.github.fortuneoflab.dualsimbands.ui.graph.MeterView;
import io.github.fortuneoflab.dualsimbands.ui.graph.Quality;

/**
 * One SIM, graphic first: state chips, band chips, carrier composition bar, quality meters and
 * recent graphs; the exact wording of every verdict stays one tap away under "詳細".
 * The frame and both toggle buttons persist across refreshes so taps are never lost.
 */
final class RadioCard {
    final View root;
    /** The header: "SIM n" large with the SIM icon on the left, the SIM name over the carrier name on the right. */
    private final LinearLayout head;
    private final TextView slot, name, carrier, traffic;

    /** Generated traffic for the header: the amount or the state, as an icon and a short word; null when off. */
    static final class Traffic {
        final String text, spoken;
        final boolean error;
        Traffic(String text, String spoken, boolean error) { this.text = text; this.spoken = spoken; this.error = error; }
    }
    /** Rebuilt on every bind: the state table and one block per band (carrier). */
    private final LinearLayout stateTable, bands;
    private final CarrierBarView carrierBar;
    private final LinearLayout cells;
    private final MaterialButton cellsToggle;
    private BandMemoryStore memory;

    private final LinearLayout body;
    /** "Use this SIM for data": only on a SIM not used for data. */
    private final MaterialButton dataButton;

    /** @param onData uses this SIM for data (in the app, or through Android's SIM settings) */
    RadioCard(ViewGroup parent, View.OnClickListener onCells, View.OnClickListener onData) {
        LinearLayout card = Ui.card(parent);
        body = card;
        Context c = card.getContext();
        root = (View) card.getParent();
        head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        // Read as one line by screen readers ("eSIM SIM 1 povo au").
        head.setFocusable(true);
        card.addView(head);
        // The SIM icon and the slot number alone, large over the two lines; coloured in bind() by the data SIM.
        slot = Ui.text(head, "", com.google.android.material.R.attr.textAppearanceHeadlineSmall);
        slot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout names = Ui.vertical(c);
        names.setPadding(Ui.dp(c, 14), 0, 0, 0);
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        name = Ui.title(names, "");
        name.setPadding(0, 0, 0, 0);
        carrier = Ui.caption(names, "");
        carrier.setPadding(0, 0, 0, 0);
        names.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Generated traffic at the header's end, on the data SIM's card: the download icon and the amount.
        traffic = Ui.caption(head, "");
        traffic.setPadding(Ui.dp(c, 8), 0, 0, 0);
        traffic.setCompoundDrawablePadding(Ui.dp(c, 4));
        traffic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        traffic.setVisibility(View.GONE);
        dataButton = Ui.outlined(card, "", onData);
        dataButton.setVisibility(View.GONE);
        stateTable = Ui.vertical(c);
        card.addView(stateTable);
        carrierBar = new CarrierBarView(c);
        card.addView(carrierBar);
        bands = Ui.vertical(c);
        card.addView(bands);
        cellsToggle = Ui.textButton(card, "", onCells);
        cells = Ui.vertical(c);
        card.addView(cells);
    }

    /** @param traffic the traffic generator's status line, given only for the data SIM; null hides it */
    /** @param inAppData whether the data button switches in the app; otherwise it opens Android's SIM settings */
    void bind(SubscriptionRepository.Sim sim, RadioSnapshot s, RadioAssessment a, Traffic generated,
              boolean showCells, boolean showIds, boolean inAppData) {
        Context c = root.getContext();
        // The SIM's colour as a stripe down the card's left edge.
        android.graphics.drawable.LayerDrawable stripe = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(SimLook.color(c, sim))});
        stripe.setLayerGravity(0, android.view.Gravity.START | android.view.Gravity.FILL_VERTICAL);
        stripe.setLayerWidth(0, Ui.dp(c, 6));
        body.setBackground(stripe);
        // eSIM or physical: the SIM numbers follow Android's slots and can change when SIMs are switched.
        // The data SIM keeps its place; its SIM icon and number are dark, the other SIM's light (dark theme: bright
        // and dim), instead of a word or a mark after the name. The other SIM's is the same colour at 40 % opacity:
        // the theme's outline colour was nearly as light as the text on the Pixel 10a (dynamic colour).
        boolean dataSim = s != null && s.defaultData;
        int onSurface = Ui.color(c, com.google.android.material.R.attr.colorOnSurface);
        int slotColor = dataSim ? onSurface : androidx.core.graphics.ColorUtils.setAlphaComponent(onSurface, 0x66);
        slot.setText(String.valueOf(sim.slotIndex + 1));
        slot.setTextColor(slotColor);
        Ui.simIcon(slot, sim.embedded, slotColor);
        slot.setCompoundDrawablePadding(Ui.dp(c, 4));
        // Heard as "eSIM SIM 1", not "eSIM 1".
        slot.setContentDescription(c.getString(sim.embedded ? R.string.radio_kind_esim : R.string.radio_kind_physical)
                + " " + c.getString(R.string.radio_sim_slot, sim.slotIndex + 1));
        String label = SimLook.name(sim), carrierName = sim.carrierName == null ? "" : sim.carrierName.trim();
        name.setText(label);
        // The carrier name Android reports; "—" when there is none. The same name as the label is shown once (the
        // name line alone, centred beside "SIM n").
        boolean same = SimLook.sameName(label, carrierName);
        carrier.setText(carrierName.isEmpty() ? "—" : carrierName);
        carrier.setVisibility(same ? View.GONE : View.VISIBLE);
        head.setContentDescription(slot.getContentDescription() + " " + label + (same ? "" : " " + carrierName)
                + (dataSim ? " " + c.getString(R.string.radio_data_sim_spoken) : ""));
        if (generated != null) head.setContentDescription(head.getContentDescription() + " " + generated.spoken);
        // Android's own record of the data SIM, so the button does not come and go with the snapshot.
        // (The demo build's sample SIMs are not Android's: their snapshot says which is the data SIM.)
        boolean androidData = Demo.source != null ? dataSim
                : android.telephony.SubscriptionManager.getDefaultDataSubscriptionId() == sim.subscriptionId;
        dataButton.setVisibility(androidData ? View.GONE : View.VISIBLE);
        dataButton.setText(inAppData ? R.string.radio_use_for_data : R.string.radio_use_for_data_settings);
        traffic.setVisibility(generated == null ? View.GONE : View.VISIBLE);
        if (generated != null) {
            traffic.setText(generated.text);
            int tint = Ui.color(c, generated.error ? androidx.appcompat.R.attr.colorError
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            traffic.setTextColor(tint);
            android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(c, R.drawable.ic_traffic);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(tint);
                int size = Math.round(traffic.getTextSize() * 1.3f);
                icon.setBounds(0, 0, size, size);
            }
            traffic.setCompoundDrawablesRelative(icon, null, null, null);
        }
        memory = new BandMemoryStore(c, sim.mcc, sim.mnc);
        mcc = sim.mcc;
        stateTable.removeAllViews(); bands.removeAllViews();
        cells.removeAllViews();
        boolean ready = s != null && a != null;
        int vis = ready ? View.VISIBLE : View.GONE;
        for (View v : new View[]{carrierBar, cellsToggle}) v.setVisibility(vis);
        if (!ready) { Ui.caption(bands, c.getString(R.string.radio_waiting)); return; }
        long now = SystemClock.elapsedRealtime();
        learnBands(s, a);

        // State table: what the status bar shows | what Android reports for data | the NR verdict.
        List<Ui.Cell[]> state = new ArrayList<>();
        state.add(new Ui.Cell[]{
                new Ui.Cell(RadioText.displayShort(a), 0, true),
                new Ui.Cell(RadioText.connection(a), 0, true),
                // NSA/SA is already in the 通信方式 column, so a verified link shows only its class. Several classes
                // stay on one line when they fit; word joiners keep "転用5G" whole, so a wrap falls only at a space.
                new Ui.Cell(RadioText.nrShort(a, sim.mcc).replace("転用5G", "転\u2060用\u20605G"),
                        // "—" (no hint at all) needs no status dot.
                        a.nrStatus == RadioAssessment.NrStatus.NOT_INDICATED ? 0
                                : ContextCompat.getColor(c, RadioText.standby(a) ? R.color.status_warning
                                : a.nrStatus == RadioAssessment.NrStatus.INDICATED_UNCONFIRMED ? R.color.chart_unknown
                                : statusColor(a.nrProvenance)), true)});
        android.widget.TableLayout st = Ui.table(stateTable, new String[]{c.getString(R.string.radio_col_display),
                c.getString(R.string.radio_col_rat), c.getString(R.string.radio_col_nr)}, state);
        // Fixed proportions instead of content-driven widths, so both SIM cards put each column at the same x.
        // The 表示 column holds only "5G+" and the like, so the NR column gets the room for "Sub-6 + 転用5G".
        Ui.columnWeights(st, 0.6f, 1.5f, 1.4f);

        // Serving-cell bandwidth when no composition is known (registered cell, or a single reported value).
        io.github.fortuneoflab.dualsimbands.radio.ServingBandwidth.Result sbw = io.github.fortuneoflab.dualsimbands.radio.ServingBandwidth.of(s, a);
        // "See the bar" only when the bar has widths: an idle SIM's list is not drawn (seen on the Pixel 10a: povo on
        // n28 SA, data over Wi-Fi, showed the hint with no bar).
        boolean barHasWidths = !a.ca.components.isEmpty() || (!a.ca.idle && a.ca.reportedBandwidthsKhz.length > 0);
        String servingBw = sbw == null ? c.getString(barHasWidths ? R.string.radio_bw_unattributed : R.string.radio_bw_unknown)
                : sbw.provenance == Provenance.VERIFIED ? RadioText.mhz(sbw.khz) + " MHz"
                : c.getString(R.string.radio_bw_reported, RadioText.mhz(sbw.khz));
        // NR bandwidth is not in CellInfo; infer it only when the reported list leaves exactly one value.
        int nrInferred = io.github.fortuneoflab.dualsimbands.radio.BandwidthInference.nrBandwidthKhz(a.ca);

        // Composition bar: verified roles in colour, otherwise grey "role unknown" bandwidths.
        List<CarrierBarView.Segment> segs = new ArrayList<>();
        if (!a.ca.components.isEmpty()) {
            // Widths are real bandwidths; an inferred NR width is labelled, an unknown one is an outline slot.
            for (CaAssessment.Component comp : a.ca.components) {
                Integer rsrp = comp.signal == null ? null : comp.signal.rsrp;
                if (comp.bandwidthKhz > 0) {
                    segs.add(new CarrierBarView.Segment(comp.bandwidthKhz, comp.rat.name(), bandLabel(comp),
                            RadioText.mhz(comp.bandwidthKhz) + " MHz").withSignal(rsrp).withColor(color(c, comp.rat, comp.nr)));
                } else if (comp.rat == Rat.NR && nrInferred > 0) {
                    segs.add(new CarrierBarView.Segment(nrInferred, comp.rat.name(), bandLabel(comp),
                            c.getString(R.string.radio_bw_inferred, RadioText.mhz(nrInferred))).withSignal(rsrp)
                            .withColor(color(c, comp.rat, comp.nr)));
                } else {
                    segs.add(new CarrierBarView.Segment(0, comp.rat.name(), bandLabel(comp), c.getString(R.string.radio_bw_unknown), true)
                            .withSignal(rsrp).withColor(color(c, comp.rat, comp.nr)));
                }
            }
        } else {
            // An idle SIM cannot aggregate: its list is left over from the last connection, so it is not drawn.
            int[] reported = a.ca.idle ? new int[0] : a.ca.reportedBandwidthsKhz;
            boolean nrServing = a.servingLte == null;
            String servingBand = nrServing ? bandLabel(a.servingNr) : bandLabel(a.servingLte);
            // The serving cell's own (verified) width, when exactly one listed value equals it.
            int match = sbw != null && sbw.provenance == Provenance.VERIFIED && reported.length > 1
                    ? io.github.fortuneoflab.dualsimbands.radio.ServingBandwidth.matchIndex(reported, sbw.khz) : -1;
            Integer servingRsrp = s.signal == null ? null : nrServing ? s.signal.nrSsRsrp : s.signal.lteRsrp;
            if (sbw != null && reported.length <= 1) {
                // One carrier, attributed: draw it in its RAT colour with its band.
                segs.add(new CarrierBarView.Segment(sbw.khz, nrServing ? "NR" : "LTE", servingBand, servingBw)
                        .withSignal(servingRsrp).withColor(color(c, nrServing ? Rat.NR : Rat.LTE, a.servingNr)));
            } else {
                for (int i = 0; i < reported.length; i++) {
                    int k = reported[i];
                    segs.add(i == match ? new CarrierBarView.Segment(k, nrServing ? "NR" : "LTE", servingBand, servingBw)
                            .withSignal(servingRsrp).withColor(color(c, nrServing ? Rat.NR : Rat.LTE, a.servingNr))
                            : new CarrierBarView.Segment(k, null, RadioText.mhz(k) + " MHz", null));
                }
            }
        }
        carrierBar.set(segs);
        carrierBar.setVisibility(segs.isEmpty() ? View.GONE : View.VISIBLE);
        // Observed only: every component's width was reported. Not a speed; one carrier needs no total.
        String total = null;
        if (!a.ca.components.isEmpty() && a.ca.aggregateBandwidthKhz != null) {
            // The theoretical peak is an estimate under fixed assumptions (PeakRate), explained in the settings terms.
            Double peak = io.github.fortuneoflab.dualsimbands.radio.PeakRate.mbps(peakCarriers(a.ca.components));
            String rate = peak == null ? null : c.getString(R.string.radio_peak_label, RadioText.rate(peak));
            if (a.ca.components.size() > 1) {
                String bw = c.getString(R.string.radio_bw_total, RadioText.mhz(a.ca.aggregateBandwidthKhz));
                total = rate == null ? bw : bw + " · " + rate;
            } else {
                // One carrier: its width is already under the bar, so only the estimate.
                total = rate;
            }
        }
        carrierBar.setTotal(total);

        // One block per band, like Hidden Band Info: verified components, otherwise the serving channel.
        RadioSnapshot.Signal g = s.signal;
        if (!a.ca.components.isEmpty()) {
            for (CaAssessment.Component comp : a.ca.components) {
                String bwText = comp.bandwidthKhz > 0 ? RadioText.mhz(comp.bandwidthKhz) + " MHz"
                        : comp.rat == Rat.NR && nrInferred > 0 ? c.getString(R.string.radio_bw_inferred, RadioText.mhz(nrInferred))
                        : c.getString(R.string.radio_bw_unknown);
                String ch = comp.lte != null ? "EARFCN " + orDash(comp.lte.earfcn < 0 ? null : comp.lte.earfcn)
                        : "NR-ARFCN " + orDash(comp.nr == null || comp.nr.nrArfcn < 0 ? null : comp.nr.nrArfcn);
                ComponentSignalView sig = comp.signal == null ? null
                        : new ComponentSignalView(comp.signal.rsrp, comp.signal.rsrq, comp.signal.sinr, comp.signal.rssi,
                                comp.signal.rssiDerived);
                // The shell-registered listener gets PCI sanitised (-1): take it, and PLMN/TAC/CI/TA, from this
                // app's own cell list.
                Object cell = componentCell(s, comp, now);
                Integer pci = comp.pci != null ? comp.pci : pciOf(cell);
                // Sorted PCC first; the band name (B… / n…) tells LTE from NR, so no role label.
                bandBlock(c, bandLabel(comp), badgeColor(c, comp.rat, comp.nr),
                        comp.lte != null ? RadioText.lteFrequencies(comp.lte) : RadioText.nrFrequencies(comp.nr), bwText, ch, pci,
                        comp.lte != null ? RadioText.lteDuplex(comp.lte) : RadioText.nrDuplex(comp.nr), idLine(cell, showIds), sig);
            }
        } else if (a.servingLte != null) {
            LteCell cell = servingCell(s, a.servingLte, now);
            bandBlock(c, bandLabel(a.servingLte), badgeColor(c, Rat.LTE, null),
                    RadioText.lteFrequencies(a.servingLte), servingBw,
                    "EARFCN " + orDash(a.servingLte.earfcn < 0 ? null : a.servingLte.earfcn), cell == null ? null : cell.pci,
                    RadioText.lteDuplex(a.servingLte), idLine(cell, showIds),
                    g == null || g.lteRsrp == null ? null : new ComponentSignalView(g.lteRsrp, g.lteRsrq, g.lteRssnr, g.lteRssi, false));
        } else if (a.servingNr != null) {
            NrCell cell = servingNrCell(s, a.servingNr, now);
            bandBlock(c, bandLabel(a.servingNr), badgeColor(c, Rat.NR, a.servingNr),
                    RadioText.nrFrequencies(a.servingNr), servingBw,
                    "NR-ARFCN " + orDash(a.servingNr.nrArfcn < 0 ? null : a.servingNr.nrArfcn), cell == null ? null : cell.pci,
                    RadioText.nrDuplex(a.servingNr), idLine(cell, showIds),
                    g == null || g.nrSsRsrp == null ? null : new ComponentSignalView(g.nrSsRsrp, g.nrSsRsrq, g.nrSsSinr, null, false));
        }



        if (s.cells.status == RadioSnapshot.Cells.Status.OK) {
            int count = neighbourCount(s, now);
            cellsToggle.setVisibility(View.VISIBLE);
            cellsToggle.setText(c.getString(showCells ? R.string.radio_cells_hide : R.string.radio_cells_show, count));
            if (showCells) {
                if (count == 0) Ui.caption(cells, c.getString(R.string.radio_cells_empty));
                else cellTable(c, s, now, showIds);
            }
        } else {
            cellsToggle.setVisibility(View.GONE);
            Ui.caption(cells, RadioText.cellsUnavailable(s.cells.status));
        }
    }

    /** PeakRate inputs; null when a carrier's duplex is unknown, so no estimate is made. */
    private static List<io.github.fortuneoflab.dualsimbands.radio.PeakRate.Carrier> peakCarriers(List<CaAssessment.Component> comps) {
        List<io.github.fortuneoflab.dualsimbands.radio.PeakRate.Carrier> out = new ArrayList<>();
        for (CaAssessment.Component comp : comps) {
            if (comp.lte != null) {
                String d = RadioText.lteDuplex(comp.lte);
                if (d == null) return null;
                out.add(new io.github.fortuneoflab.dualsimbands.radio.PeakRate.Carrier(Rat.LTE, comp.bandwidthKhz,
                        d.equals("TDD"), comp.lte.downlinkKhz, false));
            } else if (comp.nr != null) {
                io.github.fortuneoflab.dualsimbands.radio.Duplex d = RadioText.nrDuplexType(comp.nr);
                if (d == null) return null;
                out.add(new io.github.fortuneoflab.dualsimbands.radio.PeakRate.Carrier(Rat.NR, comp.bandwidthKhz,
                        d == io.github.fortuneoflab.dualsimbands.radio.Duplex.TDD, comp.nr.frequencyKhz, comp.nr.frequencyRange == 2));
            } else return null;
        }
        return out;
    }

    /** Signal values shown in a band block; null fields are not reported. */
    private static final class ComponentSignalView {
        final Integer rsrp, rsrq, sinr, rssi;
        final boolean rssiDerived;
        ComponentSignalView(Integer rsrp, Integer rsrq, Integer sinr, Integer rssi, boolean rssiDerived) {
            this.rsrp = rsrp; this.rsrq = rsrq; this.sinr = sinr; this.rssi = rssi; this.rssiDerived = rssiDerived;
        }
    }

    /** Badge + frequency/bandwidth, a channel line, then that band's own meters. */
    private void bandBlock(Context c, String band, int badgeColor, String freq, String bw, String channel,
                           Integer pci, String duplex, List<String> ids, ComponentSignalView sig) {
        LinearLayout block = Ui.vertical(c);
        int pad = Ui.dp(c, 8);
        block.setPadding(0, pad, 0, pad);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(c);
        badge.setText(band);
        // White, or near-black on a light badge (the light-blue LTE badge in the dark theme).
        badge.setTextColor(androidx.core.graphics.ColorUtils.calculateContrast(0xFFFFFFFF, badgeColor) >= 4.5
                ? 0xFFFFFFFF : 0xFF1A1A1A);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        badge.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2));
        // One width for every badge (sized for "n258"), so the frequency text after it starts at the same x.
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setMinWidth(Math.round(badge.getPaint().measureText("n258")) + Ui.dp(c, 20));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Ui.dp(c, 12));
        bg.setColor(badgeColor);
        badge.setBackground(bg);
        head.addView(badge);
        TextView line = new TextView(c);
        // After the "BW" label, "帯域幅不明" would repeat the word: just "不明".
        String bwShown = bw.equals(c.getString(R.string.radio_bw_unknown)) ? c.getString(R.string.radio_unknown_short) : bw;
        line.setText(c.getString(R.string.radio_band_head, freq, bwShown) + (duplex == null ? "" : " · " + duplex));
        line.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        line.setTextColor(Ui.color(c, com.google.android.material.R.attr.colorOnSurface));
        line.setPadding(Ui.dp(c, 10), 0, 0, 0);
        head.addView(line);
        block.addView(head);
        List<String> items = new ArrayList<>();
        items.add(channel);
        items.add("PCI " + orDash(pci));
        items.addAll(ids);
        idGrid(c, block, items);
        // Every row on every band, "—" when not reported (Android gives no RSSI for NR): the rows never come and go, so
        // the card keeps its height (seen on the Pixel 10a: SINR coming and going every second moved the card below).
        Integer rsrp = sig == null ? null : sig.rsrp, rsrq = sig == null ? null : sig.rsrq,
                rssi = sig == null ? null : sig.rssi, sinr = sig == null ? null : sig.sinr;
        meter(block, "RSRP", Quality.Metric.RSRP, rsrp);
        meter(block, "RSRQ", Quality.Metric.RSRQ, rsrq);
        MeterView m = new MeterView(c, false);
        m.set("RSSI", Quality.Metric.RSSI, rssi, rssi != null && sig.rssiDerived);
        block.addView(m);
        meter(block, "SINR", Quality.Metric.SINR, sinr);
        if (bands.getChildCount() > 0) {
            View rule = new View(c);
            rule.setBackgroundColor(Ui.color(c, com.google.android.material.R.attr.colorOutlineVariant));
            bands.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1) / 2)));
        }
        bands.addView(block);
    }

    /** PCI of the one serving-marked cell on the component's channel; null when absent or not unique. */
    /** The one serving-marked cell on the component's channel (and PCI, when known); null when not unique. */
    private static Object componentCell(RadioSnapshot s, CaAssessment.Component comp, long now) {
        if (s.cells.status != RadioSnapshot.Cells.Status.OK) return null;
        Object hit = null;
        int hits = 0;
        if (comp.lte != null) for (LteCell cell : s.cells.lte) {
            if (!fresh(cell.observedAtMillis, now)) continue;
            if (!cell.role.isServing() || cell.channel == null || cell.channel.earfcn != comp.lte.earfcn) continue;
            if (comp.pci != null && cell.pci != null && !comp.pci.equals(cell.pci)) continue;
            hit = cell; hits++;
        }
        if (comp.nr != null) for (NrCell cell : s.cells.nr) {
            if (!fresh(cell.observedAtMillis, now)) continue;
            if (!cell.role.isServing() || cell.channel == null || cell.channel.nrArfcn != comp.nr.nrArfcn) continue;
            if (comp.pci != null && cell.pci != null && !comp.pci.equals(cell.pci)) continue;
            hit = cell; hits++;
        }
        return hits == 1 ? hit : null;
    }

    private static Integer pciOf(Object cell) {
        return cell instanceof LteCell ? ((LteCell) cell).pci : cell instanceof NrCell ? ((NrCell) cell).pci : null;
    }

    /** The registered NR cell on an SA serving channel; null when not unique. */
    private static NrCell servingNrCell(RadioSnapshot s, NrChannel ch, long now) {
        if (s.cells.status != RadioSnapshot.Cells.Status.OK) return null;
        NrCell hit = null;
        for (NrCell cell : s.cells.nr) {
            if (!fresh(cell.observedAtMillis, now)) continue;
            if (!cell.role.isServing() && cell.role != io.github.fortuneoflab.dualsimbands.radio.CellRole.REGISTERED) continue;
            if (cell.channel == null || cell.channel.nrArfcn != ch.nrArfcn) continue;
            if (hit != null) return null;
            hit = cell;
        }
        return hit;
    }

    /**
     * "LABEL value" items two per row: muted labels at fixed widths, values in the body colour, so every block
     * puts each column at the same x.
     */
    private static void idGrid(Context c, LinearLayout parent, List<String> items) {
        int muted = Ui.color(c, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int body = Ui.color(c, com.google.android.material.R.attr.colorOnSurface);
        android.graphics.Paint probe = new android.graphics.Paint();
        probe.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12,
                c.getResources().getDisplayMetrics()));
        int gap = Ui.dp(c, 12);
        // Left column labels: EARFCN / NR-ARFCN, PLMN, CI / NCI; right: PCI, TAC, TA (or whatever falls there).
        int[] labelW = {Math.round(probe.measureText("NR-ARFCN")) + gap, Math.round(probe.measureText("PLMN")) + gap};
        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            // A little more room under the band badge line than between the grid rows.
            row.setPadding(0, Ui.dp(c, i == 0 ? 6 : 2), 0, Ui.dp(c, 2));
            for (int k = 0; k < 2; k++) {
                LinearLayout half = new LinearLayout(c);
                half.setOrientation(LinearLayout.HORIZONTAL);
                half.setGravity(android.view.Gravity.CENTER_VERTICAL);
                if (i + k < items.size()) {
                    String item = items.get(i + k);
                    int sp = item.indexOf(' ');
                    TextView label = new TextView(c);
                    label.setText(sp < 0 ? item : item.substring(0, sp));
                    label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
                    label.setTextColor(muted);
                    label.setMinWidth(labelW[k]);
                    half.addView(label);
                    TextView value = new TextView(c);
                    value.setText(sp < 0 ? "" : item.substring(sp + 1));
                    speakMasked(value);
                    value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                    value.setTextColor(body);
                    half.addView(value);
                }
                row.addView(half, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            parent.addView(row);
        }
    }

    /**
     * The primary cell's TA when the modem reports none: on the Pixel 10a the data SIM's primary cell went without one
     * in about a third of the rows, even while connected, so the row stays and says so rather than disappearing.
     * Secondary and neighbouring cells are not given one, as they normally have no TA.
     */
    private static String taUnknown() { return t("不明", "unknown"); }

    private static List<String> idLine(Object cell, boolean showIds) {
        List<String> parts = new ArrayList<>();
        if (cell instanceof LteCell) {
            LteCell l = (LteCell) cell;
            if (l.plmn != null) parts.add("PLMN " + l.plmn);
            if (l.tac != null) parts.add("TAC " + masked(showIds, l.tac));
            if (l.ci != null) parts.add("CI " + masked(showIds, l.ci));
            if (l.ta != null) parts.add("TA " + masked(showIds, l.ta));
            else if (l.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.PRIMARY_SERVING) parts.add("TA " + taUnknown());
        } else if (cell instanceof NrCell) {
            NrCell n = (NrCell) cell;
            if (n.plmn != null) parts.add("PLMN " + n.plmn);
            if (n.tac != null) parts.add("TAC " + masked(showIds, n.tac));
            if (n.nci != null) parts.add("NCI " + masked(showIds, n.nci));
            if (n.ta != null) parts.add("TA " + masked(showIds, n.ta));
            else if (n.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.PRIMARY_SERVING) parts.add("TA " + taUnknown());
        }
        return parts;
    }

    /** The registered cell on the serving channel, for its PCI; null when it is not unique. */
    private static LteCell servingCell(RadioSnapshot s, LteChannel ch, long now) {
        if (s.cells.status != RadioSnapshot.Cells.Status.OK) return null;
        LteCell hit = null;
        for (LteCell cell : s.cells.lte) {
            if (!fresh(cell.observedAtMillis, now)) continue;
            if (!cell.role.isServing() && cell.role != io.github.fortuneoflab.dualsimbands.radio.CellRole.REGISTERED) continue;
            if (cell.channel == null || cell.channel.earfcn != ch.earfcn) continue;
            if (hit != null) return null;
            hit = cell;
        }
        return hit;
    }

    /** Remember explicitly reported NR bands that resolve an n77/n78-style ambiguity. */
    private void learnBands(RadioSnapshot s, RadioAssessment a) {
        if (a.servingNr != null) memory.learn(a.servingNr);
        for (CaAssessment.Component comp : a.ca.components) if (comp.nr != null) memory.learn(comp.nr);
        if (s.cells.status == RadioSnapshot.Cells.Status.OK) for (NrCell cell : s.cells.nr) memory.learn(cell.channel);
    }

    /**
     * Neighbouring cells only: measured but not connected (CellInfo without a serving or registered mark).
     * The serving cells are the band blocks above. Strongest first.
     */
    private void cellTable(Context c, RadioSnapshot s, long now, boolean showIds) {
        final class Row { final Integer rsrp; final Ui.Cell[] cells; Row(Integer r, Ui.Cell[] c) { rsrp = r; cells = c; } }
        List<Row> rows = new ArrayList<>();
        int lteColor = color(c, Rat.LTE, null);
        for (LteCell cell : s.cells.lte) {
            if (cell.role != io.github.fortuneoflab.dualsimbands.radio.CellRole.UNREGISTERED || !fresh(cell.observedAtMillis, now)) continue;
            String freq = cell.channel.hasFrequency() ? Mhz.format(cell.channel.downlinkKhz) + " MHz" : t("不明", "unknown");
            rows.add(new Row(cell.rsrp, new Ui.Cell[]{new Ui.Cell(bandLabel(cell.channel), lteColor, false), new Ui.Cell(freq),
                    new Ui.Cell(cell.channel.earfcn < 0 ? "—" : String.valueOf(cell.channel.earfcn)),
                    new Ui.Cell(ids(cell.pci, showIds, cell.tac, "CI", cell.ci)
                            + extra(cell.ta == null ? null : "TA " + masked(showIds, cell.ta))),
                    rsrp(c, cell.rsrp)}));
        }
        for (NrCell cell : s.cells.nr) {
            if (cell.role != io.github.fortuneoflab.dualsimbands.radio.CellRole.UNREGISTERED || !fresh(cell.observedAtMillis, now)) continue;
            String freq = cell.channel.hasFrequency() ? Mhz.format(cell.channel.frequencyKhz) + " MHz" : t("不明", "unknown");
            rows.add(new Row(cell.ssRsrp, new Ui.Cell[]{new Ui.Cell(bandLabel(cell.channel), color(c, Rat.NR, cell.channel), false), new Ui.Cell(freq),
                    new Ui.Cell(cell.channel.nrArfcn < 0 ? "—" : String.valueOf(cell.channel.nrArfcn)),
                    new Ui.Cell(ids(cell.pci, showIds, cell.tac, "NCI", cell.nci)
                            + extra(cell.ta == null ? null : "TA " + masked(showIds, cell.ta))),
                    rsrp(c, cell.ssRsrp)}));
        }
        // Strongest first; cells without a reported RSRP last.
        java.util.Collections.sort(rows, (x, y) -> x.rsrp == null ? (y.rsrp == null ? 0 : 1)
                : y.rsrp == null ? -1 : Integer.compare(y.rsrp, x.rsrp));
        List<Ui.Cell[]> table = new ArrayList<>();
        for (Row r : rows) table.add(r.cells);
        android.widget.TableLayout t = Ui.table(cells, new String[]{c.getString(R.string.radio_col_band),
                c.getString(R.string.radio_col_freq), "ARFCN", "PCI", c.getString(R.string.radio_col_rsrp)}, table);
        // Fixed shares, so both SIM cards' tables put each column at the same x. Everything after the band is a
        // number, right-aligned so the digits line up; PCI may carry TAC/CI/TA lines below it.
        for (int r = 0; r < t.getChildCount(); r++) {
            if (!(t.getChildAt(r) instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) t.getChildAt(r);
            for (int k = 0; k < row.getChildCount(); k++) if (row.getChildAt(k) instanceof TextView) speakMasked((TextView) row.getChildAt(k));
        }
        Ui.columnWeights(t, 0.9f, 1.5f, 1.1f, 1.1f, 1f);
        Ui.alignEnd(t, 1, 2, 3, 4);
        Ui.centerHeader(t);
    }

    private static int neighbourCount(RadioSnapshot s, long now) {
        int n = 0;
        for (LteCell cell : s.cells.lte)
            if (cell.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.UNREGISTERED && fresh(cell.observedAtMillis, now)) n++;
        for (NrCell cell : s.cells.nr)
            if (cell.role == io.github.fortuneoflab.dualsimbands.radio.CellRole.UNREGISTERED && fresh(cell.observedAtMillis, now)) n++;
        return n;
    }

    /**
     * A cell measured within the last 30 s (the age at which cell lists count as stale everywhere else). Judged per
     * cell: a list's time is its newest cell's, so an old cell listed with a fresh one would otherwise lend its PCI,
     * TAC, cell ID and TA to the current carrier, or stand among the neighbours as if current.
     */
    private static boolean fresh(long observedAtMillis, long now) { return now - observedAtMillis <= 30_000; }

    /** TA under the PCI, only when reported (neighbours usually report none). */
    private static String extra(String ta) {
        return ta == null ? "" : "\n" + ta;
    }

    private static String ids(Integer pci, boolean showIds, Integer tac, String idName, Object id) {
        StringBuilder b = new StringBuilder(orDash(pci));
        if (tac != null) b.append("\nTAC ").append(masked(showIds, tac));
        if (id != null) b.append("\n").append(idName).append(' ').append(masked(showIds, id));
        return b.toString();
    }

    /**
     * Cell ID, TAC and TA, which together point at a place, are masked rather than left out while the ID setting
     * is off: the layout stays the same, so a screenshot can be shared as it is.
     */
    static String masked(boolean show, Object value) { return show ? String.valueOf(value) : MASK; }
    static final String MASK = "••••";

    /** Screen readers hear "hidden" for a masked value, not the bullet symbols. */
    static String spoken(String text) { return text.replace(MASK, t("非表示", "hidden")); }

    private static void speakMasked(TextView v) {
        String text = v.getText().toString();
        v.setContentDescription(text.contains(MASK) ? spoken(text) : null);
    }

    /** Value plus a quality dot; the number states the value, the legend below names the dot colours. */
    private static Ui.Cell rsrp(Context c, Integer v) { return rsrp(c, v, t("不明", "unknown")); }

    private static Ui.Cell rsrp(Context c, Integer v, String missing) {
        Quality.Level l = Quality.level(Quality.Metric.RSRP, v);
        int dot = l == Quality.Level.UNKNOWN ? 0 : ContextCompat.getColor(c, MeterView.statusColorRes(l));
        return new Ui.Cell(v == null ? missing : String.valueOf(v), dot, false);
    }

    private static void meter(LinearLayout parent, String name, Quality.Metric metric, Integer v) {
        MeterView m = new MeterView(parent.getContext(), false);
        m.set(name, metric, v);
        parent.addView(m);
    }

    /** Verified = good green; indicated = amber; anything else neutral. Always paired with text. */
    private static int statusColor(Provenance p) {
        switch (p) {
            case VERIFIED: return R.color.status_good;
            case INDICATED: return R.color.status_warning;
            default: return R.color.chart_unknown;
        }
    }

    /** LTE blue; NR by frequency class: refarmed green, Sub-6 orange (also when the class is unknown), mmWave magenta. */
    private int color(Context c, Rat rat, NrChannel nr) {
        if (rat != Rat.NR) return ContextCompat.getColor(c, R.color.chart_lte);
        switch (RadioText.nrClassOf(nr, mcc)) {
            case REFARMED: return ContextCompat.getColor(c, R.color.chart_nr_refarmed);
            case MMWAVE: return ContextCompat.getColor(c, R.color.chart_nr_mmwave);
            default: return ContextCompat.getColor(c, R.color.chart_nr);
        }
    }

    /** The band badge's colour: the same hue as {@link #color}; its label is white or near-black, whichever reads (4.5:1 or more). */
    private int badgeColor(Context c, Rat rat, NrChannel nr) {
        if (rat != Rat.NR) return ContextCompat.getColor(c, R.color.badge_lte);
        switch (RadioText.nrClassOf(nr, mcc)) {
            case REFARMED: return ContextCompat.getColor(c, R.color.badge_nr_refarmed);
            case MMWAVE: return ContextCompat.getColor(c, R.color.badge_nr_mmwave);
            default: return ContextCompat.getColor(c, R.color.badge_nr);
        }
    }

    /** The SIM's MCC, for the Japanese split between refarmed NR and Sub-6. */
    private String mcc;

    private String bandLabel(CaAssessment.Component comp) {
        return comp.lte != null ? bandLabel(comp.lte) : bandLabel(comp.nr);
    }
    private static String bandLabel(LteChannel ch) { return ch == null || ch.band <= 0 ? "LTE ?" : "B" + ch.band; }
    private String bandLabel(NrChannel ch) {
        if (ch == null) return "NR ?";
        if (ch.band > 0) return "n" + ch.band;
        // A band the platform reported earlier for this carrier + NR-ARFCN; labelled as such.
        int remembered = memory == null ? 0 : memory.lookup(ch);
        if (remembered > 0) return "n" + remembered + root.getContext().getString(R.string.radio_band_remembered);
        if (ch.candidateBands.length > 1) {
            StringBuilder b = new StringBuilder("n");
            for (int i = 0; i < ch.candidateBands.length; i++) b.append(i == 0 ? "" : "/").append(ch.candidateBands[i]);
            return b.append("?").toString();
        }
        return "NR ?";
    }
    private static String orDash(Object v) { return v == null ? "—" : String.valueOf(v); }
}
