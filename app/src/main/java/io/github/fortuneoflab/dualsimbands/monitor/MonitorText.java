package io.github.fortuneoflab.dualsimbands.monitor;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import io.github.fortuneoflab.dualsimbands.PrivilegedProbe;
import io.github.fortuneoflab.dualsimbands.radio.CaAssessment;
import io.github.fortuneoflab.dualsimbands.radio.Mhz;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.ui.RadioText;

/** Notification wording. The default-data SIM leads; a timestamp always shows how fresh the content is. */
public final class MonitorText {
    public static final class Line {
        final int slot;
        final String carrier, mcc;
        final RadioSnapshot snapshot;
        final RadioAssessment assessment;
        public Line(int slot, String carrier, RadioSnapshot snapshot, RadioAssessment assessment) {
            this(slot, carrier, null, snapshot, assessment);
        }
        public Line(int slot, String carrier, String mcc, RadioSnapshot snapshot, RadioAssessment assessment) {
            this.slot = slot; this.carrier = carrier; this.mcc = mcc; this.snapshot = snapshot; this.assessment = assessment;
        }
    }

    public static final class Content {
        public final String title, text, expanded;
        Content(String title, String text, String expanded) { this.title = title; this.text = text; this.expanded = expanded; }
    }

    private MonitorText() {}

    public static Content content(List<Line> lines, long wallMillis, boolean screenOn) {
        return content(lines, wallMillis, screenOn, PrivilegedProbe.Block.NONE);
    }

    /**
     * {@code blocked}: why the composition, wanted here (Automatic, history Always), cannot be read; the rows then carry
     * no verified composition. Each reason says what fixes it: opening the app alone helps only when Shizuku runs but
     * has not sent its binder yet, which cannot be told apart from Shizuku being stopped. Observed on the Pixel 10a
     * (Android 17) tested: no binder arrived when the Shizuku server started, and one arrived once the app came back to
     * the front, not while it stayed open; so the app is to be reopened.
     */
    public static Content content(List<Line> lines, long wallMillis, boolean screenOn, PrivilegedProbe.Block blocked) {
        return content(lines, wallMillis, screenOn, blocked, null);
    }

    /** {@code traffic}: the line for traffic the monitor carries on (time left, amount), or null; leads the expanded text. */
    public static Content content(List<Line> lines, long wallMillis, boolean screenOn, PrivilegedProbe.Block blocked,
                                  String traffic) {
        Content c = withBlock(lines, wallMillis, screenOn, blocked);
        return traffic == null ? c : new Content(c.title, c.text, traffic + "\n" + c.expanded);
    }

    private static Content withBlock(List<Line> lines, long wallMillis, boolean screenOn, PrivilegedProbe.Block blocked) {
        Content c = content0(lines, wallMillis, screenOn);
        String brief, line;
        switch (blocked) {
            case SHIZUKU_NOT_RUNNING:
                brief = t("Shizuku 未接続 · ", "Shizuku not connected · ");
                line = t("キャリア構成：Shizuku 未接続（Shizuku の起動を確認し、このアプリを開き直す）",
                        "Carrier composition: Shizuku not connected (check that Shizuku is running, then reopen this app)");
                break;
            case SHIZUKU_NOT_GRANTED:
                brief = t("Shizuku 未許可 · ", "Shizuku not allowed · ");
                line = t("キャリア構成：Shizuku の使用が許可されていません（アプリを開いて許可）",
                        "Carrier composition: Shizuku is not allowed (open the app to allow it)");
                break;
            case PHONE_PERMISSION:
                brief = t("電話の権限なし · ", "No phone permission · ");
                line = t("キャリア構成：電話の権限がありません（アプリを開いて許可）",
                        "Carrier composition: no phone permission (open the app to allow it)");
                break;
            default: return c;
        }
        return new Content(c.title, brief + c.text, line + "\n" + c.expanded);
    }

    private static Content content0(List<Line> lines, long wallMillis, boolean screenOn) {
        String stamp = t("更新 ", "Updated ") + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(wallMillis))
                + (screenOn ? "" : t("（画面オフの間は止めています）", " (paused while the screen is off)"));
        List<Line> ordered = new ArrayList<>();
        for (Line l : lines) if (l.snapshot != null && l.snapshot.defaultData) ordered.add(l);
        for (Line l : lines) if (!ordered.contains(l)) ordered.add(l);
        if (ordered.isEmpty() || ordered.get(0).assessment == null) {
            return new Content(t("Dual SIM Bands で監視中", "Dual SIM Bands is monitoring"), t("SIMの情報を待っています", "Waiting for SIM information"), t("SIMの情報を待っています\n", "Waiting for SIM information\n") + stamp);
        }
        Line lead = ordered.get(0);
        String title = "SIM " + (lead.slot + 1) + " " + lead.carrier + " · " + state(lead);
        String text = brief(lead);
        StringBuilder b = new StringBuilder();
        for (Line l : ordered) {
            if (l.assessment == null) continue;
            if (b.length() > 0) b.append('\n');
            b.append("SIM ").append(l.slot + 1).append(' ').append(l.carrier)
                    .append(l.snapshot.defaultData ? t("［データ通信］", " [data]") : "").append('\n')
                    .append(state(l)).append('\n')
                    .append(brief(l));
        }
        b.append('\n').append(stamp);
        return new Content(title, text, b.toString());
    }

    /** Same words as the Radio tab's state table: "5G+ · LTE + NR（NSA） · Sub-6". */
    static String state(Line l) {
        return RadioText.displayShort(l.assessment) + " · " + RadioText.connection(l.assessment)
                + " · NR " + RadioText.nrShort(l.assessment, l.mcc);
    }

    /** The bands in use ("B1 + n77") or the serving channel, and the primary signal; nothing guessed. */
    static String brief(Line l) {
        RadioAssessment a = l.assessment;
        StringBuilder b = new StringBuilder();
        List<CaAssessment.Component> active = a.ca.components;
        if (!active.isEmpty()) {
            for (CaAssessment.Component c : active) {
                if (b.length() > 0) b.append(" + ");
                if (c.lte != null) b.append(c.lte.band > 0 ? "B" + c.lte.band : "LTE");
                else b.append(c.nr != null && c.nr.band > 0 ? "n" + c.nr.band : "NR");
            }
        } else if (a.servingLte != null && a.servingLte.hasFrequency()) {
            b.append(a.servingLte.band > 0 ? "B" + a.servingLte.band : "LTE").append(' ')
                    .append(Mhz.format(a.servingLte.downlinkKhz)).append(" MHz");
        } else if (a.servingNr != null && a.servingNr.hasFrequency()) {
            b.append(a.servingNr.band > 0 ? "n" + a.servingNr.band : "NR").append(' ')
                    .append(Mhz.format(a.servingNr.frequencyKhz)).append(" MHz");
        } else {
            b.append(t("チャネル不明", "channel unknown"));
        }
        RadioSnapshot.Signal g = l.snapshot.signal;
        if (g != null && g.lteRsrp != null) b.append(" · RSRP ").append(g.lteRsrp).append(" dBm");
        return b.toString();
    }
}
