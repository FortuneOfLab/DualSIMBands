package io.github.fortuneoflab.dualsimbands.ui;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import io.github.fortuneoflab.dualsimbands.radio.CaAssessment;
import io.github.fortuneoflab.dualsimbands.radio.CellRole;
import io.github.fortuneoflab.dualsimbands.radio.DisplayOverride;
import io.github.fortuneoflab.dualsimbands.radio.Duplex;
import io.github.fortuneoflab.dualsimbands.radio.LteChannel;
import io.github.fortuneoflab.dualsimbands.radio.Mhz;
import io.github.fortuneoflab.dualsimbands.radio.NrChannel;
import io.github.fortuneoflab.dualsimbands.radio.Provenance;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.Rat;

/**
 * Japanese wording for radio state. Kept free of Android types so the distinctions
 * 未確認 (unconfirmed) / 未接続 (not connected) / 取得不可 (unavailable) are unit-tested.
 */
public final class RadioText {
    private RadioText() {}

    public static String provenance(Provenance p) {
        switch (p) {
            case VERIFIED: return t("確認済み", "verified");
            case INDICATED: return t("表示のみ", "display only");
            case DERIVED: return t("計算値", "calculated");
            case UNSUPPORTED: return t("取得不可", "unavailable");
            default: return t("不明", "unknown");
        }
    }

    public static String registration(RadioSnapshot.RegState s) {
        switch (s) {
            case IN_SERVICE: return t("圏内", "in service");
            case OUT_OF_SERVICE: return t("未接続（圏外）", "not connected (no service)");
            case EMERGENCY_ONLY: return t("未接続（緊急通報のみ）", "not connected (emergency calls only)");
            case POWER_OFF: return t("未接続（電波オフ・機内モード）", "not connected (radio off / airplane mode)");
            default: return t("不明", "unknown");
        }
    }

    public static String rat(Rat r) {
        switch (r) {
            case GSM: return "2G (GSM)";
            case UMTS: return "3G (UMTS)";
            case CDMA: return "CDMA";
            case LTE: return "LTE";
            case NR: return "NR";
            case IWLAN: return t("Wi-Fi通話 (IWLAN)", "Wi-Fi calling (IWLAN)");
            default: return t("不明", "unknown");
        }
    }

    /** Status-bar wording, always labelled as a display value. */
    public static String display(DisplayOverride o, Rat network) {
        if (o == null) return t("未取得", "not read yet");
        String icon;
        switch (o) {
            case LTE_CA: icon = "4G+"; break;
            case LTE_ADVANCED_PRO: icon = "LTE+"; break;
            case NR_NSA: icon = "5G"; break;
            case NR_NSA_MMWAVE: icon = "5G+ (mmWave)"; break;
            case NR_ADVANCED: icon = "5G+"; break;
            case NONE:
                icon = network == Rat.NR ? "5G" : network == Rat.LTE ? "4G / LTE" : rat(network);
                return icon + t(" 表示", " shown");
            default: return t("不明", "unknown");
        }
        return icon + t(" 表示（", " shown (") + o.name() + t("）", ")");
    }

    /** Detail row: the RAT exactly as Android reports it, with the NSA caveat when it applies. */
    public static String observedRat(RadioAssessment a) {
        if (a.observedDataRatProvenance != Provenance.VERIFIED) {
            return a.dataReg == RadioSnapshot.RegState.IN_SERVICE ? t("不明", "unknown") : registration(a.dataReg);
        }
        if (a.nrStatus == RadioAssessment.NrStatus.NSA_VERIFIED)
            return rat(a.observedDataRat) + t("（Androidの報告値。NSAではNR接続中もLTEと報告されます）", " (as Android reports it; in NSA it reports LTE even with NR connected)");
        return rat(a.observedDataRat);
    }

    /**
     * What carries the data. In NSA Android reports LTE (the anchor) even with an active NR leg, so a
     * verified NSA link reads "LTE + NR（NSA）"; an unverified 5G icon never adds NR here.
     */
    public static String connection(RadioAssessment a) {
        if (a.observedDataRatProvenance != Provenance.VERIFIED) {
            return a.dataReg == RadioSnapshot.RegState.IN_SERVICE ? t("不明", "unknown") : registration(a.dataReg);
        }
        if (a.nrStatus == RadioAssessment.NrStatus.NSA_VERIFIED) return t("LTE + NR（NSA）", "LTE + NR (NSA)");
        if (a.nrStatus == RadioAssessment.NrStatus.SA_VERIFIED) return t("NR（SA）", "NR (SA)");
        return rat(a.observedDataRat);
    }

    public static String nr(RadioAssessment a) {
        switch (a.nrStatus) {
            case SA_VERIFIED: return t("NR SA 接続", "NR SA connected");
            case NSA_VERIFIED: return t("NR NSA 接続中", "NR NSA connected");
            case INDICATED_UNCONFIRMED:
                boolean icon = a.displayOverride != null && a.displayOverride.indicatesNr();
                return icon ? t("NR 未確認（5G は表示のみ）", "NR unconfirmed (5G shown only)") : t("NR 未確認（NR の信号報告のみ）", "NR unconfirmed (NR signal reported only)");
            case NOT_INDICATED: return t("NR 未確認（5G の表示なし。NSA 中は観測できない場合あり）", "NR unconfirmed (no 5G shown; may not be observable during NSA)");
            default:
                return a.dataReg == RadioSnapshot.RegState.IN_SERVICE || a.dataReg == RadioSnapshot.RegState.UNKNOWN
                        ? t("判定不可（データ不足）", "undetermined (not enough data)") : t("判定不可（", "undetermined (") + registration(a.dataReg) + t("）", ")");
        }
    }

    public static String ca(CaAssessment ca) {
        switch (ca.status) {
            case VERIFIED_CA: {
                int nr = 0;
                for (CaAssessment.Component c : ca.components) if (c.rat == Rat.NR) nr++;
                if (nr == 0 || ca.aggregateBandwidthKhz != null) {
                    String total = ca.aggregateBandwidthKhz == null ? t("合計帯域幅は不明", "total bandwidth unknown")
                            : t("合計 ", "total ") + mhz(ca.aggregateBandwidthKhz) + " MHz";
                    return t("CA：", "CA: ") + ca.components.size() + t(" キャリア（", " carriers (") + total + t("）", ")");
                }
                // NR bandwidth is often unknown (CellInfoNr has none): keep the LTE total separate, never guess.
                return t("CA：", "CA: ") + part(ca, Rat.LTE, "LTE ", t(" キャリア", " carriers")) + " + " + part(ca, Rat.NR, "NR ", "");
            }
            case VERIFIED_NO_CA: {
                // One carrier per RAT: either a single carrier, or LTE + NR (EN-DC), which is not LTE CA.
                int lte = 0, nr = 0;
                for (CaAssessment.Component c : ca.components) if (c.rat == Rat.LTE) lte++; else nr++;
                String what = lte > 0 && nr > 0 ? "LTE " + lte + " + NR " + nr + t("（EN-DC）・LTE の CA なし", " (EN-DC), no LTE CA") : t("単一キャリア", "single carrier");
                return what;
            }
            case CELLS_ONLY: {
                // The cell list may leave carriers out: what it marks is shown, CA is left open.
                int lte = 0, nr = 0;
                for (CaAssessment.Component c : ca.components) if (c.rat == Rat.LTE) lte++; else nr++;
                String seen = lte > 0 && nr > 0 ? "LTE " + lte + " + NR " + nr : t("1 キャリア", "1 carrier");
                return seen + t("（セル一覧）・ほかのキャリアは未確認", " (cell list); other carriers unconfirmed");
            }
            case INDICATED:
                return ca.evidence.contains(CaAssessment.Evidence.DISPLAY_OVERRIDE)
                        ? t("LTE-CA は表示のみ。PCC/SCC の構成は未確認", "LTE-CA shown only; PCC/SCC composition unconfirmed")
                        : t("複数の帯域幅が報告済み。CA の構成は未確認", "several bandwidths reported; CA composition unconfirmed");
            case NOT_INDICATED: return t("CA の表示なし（未確認）", "no CA shown (unconfirmed)");
            default: return t("判定不可", "undetermined");
        }
    }

    private static String part(CaAssessment ca, Rat rat, String prefix, String unit) {
        int n = 0; long sum = 0; boolean known = true;
        for (CaAssessment.Component c : ca.components) {
            if (c.rat != rat) continue;
            n++;
            if (c.bandwidthKhz > 0) sum += c.bandwidthKhz; else known = false;
        }
        return prefix + n + unit + (known ? t("（合計 ", " (total ") + mhz(sum) + t(" MHz）", " MHz)") : t("（帯域幅不明）", " (bandwidth unknown)"));
    }

    /** "NSA · Sub-6" for a verified NR link, or null when there is nothing verified to describe. */
    public static String nrMode(RadioAssessment a) { return nrMode(a, null); }

    /** @param mcc the subscription's MCC; Japanese networks (440/441) get the 転用5G wording */
    public static String nrMode(RadioAssessment a, String mcc) {
        String mode;
        if (a.nrStatus == RadioAssessment.NrStatus.SA_VERIFIED) mode = "SA";
        else if (a.nrStatus == RadioAssessment.NrStatus.NSA_VERIFIED) mode = "NSA";
        else return null;
        String cls = nrClass(a, mcc);
        return cls == null ? mode : mode + " · " + cls;
    }

    /**
     * "Sub-6" / "転用5G" / "ミリ波" for a verified NR link, or every class in use joined highest first
     * ("Sub-6 + 転用5G"): Android does not say which NR carrier leads an NSA link, so none is picked (seen on the
     * Pixel 10a: n3 listed before n77 made the column say only 転用5G). Null when not verified or no frequency is known.
     */
    public static String nrClass(RadioAssessment a, String mcc) {
        if (a.nrStatus != RadioAssessment.NrStatus.SA_VERIFIED && a.nrStatus != RadioAssessment.NrStatus.NSA_VERIFIED) return null;
        boolean japan = "440".equals(mcc) || "441".equals(mcc);
        java.util.List<NrChannel> channels = new java.util.ArrayList<>();
        if (a.servingNr != null) channels.add(a.servingNr);
        for (CaAssessment.Component c : a.ca.components) if (c.nr != null) channels.add(c.nr);
        // Highest class first: mmWave, then from 3.3 / 3.6 GHz, then below.
        java.util.TreeMap<Integer, String> classes = new java.util.TreeMap<>();
        for (NrChannel ch : channels) {
            if (ch.frequencyRange == 0) continue;
            int rank = ch.frequencyRange == 2 ? 0 : ch.frequencyKhz >= (japan ? 3_600_000L : 3_300_000L) ? 1 : 2;
            classes.put(rank, frequencyClass(ch, japan));
        }
        return classes.isEmpty() ? null : String.join(" + ", classes.values());
    }

    /** Frequency class of an NR carrier, for its colour. */
    public enum NrClass { REFARMED, SUB6, MMWAVE, UNKNOWN }

    /**
     * The same split as {@link #frequencyClass}: FR2 is mmWave; in Japan from 3.6 GHz (elsewhere 3.3 GHz) Sub-6, below
     * that refarmed (named "below 3.3 GHz" outside Japan, coloured the same); unknown without a frequency range.
     */
    public static NrClass nrClassOf(io.github.fortuneoflab.dualsimbands.radio.NrChannel ch, String mcc) {
        if (ch == null || ch.frequencyRange == 0) return NrClass.UNKNOWN;
        if (ch.frequencyRange == 2) return NrClass.MMWAVE;
        boolean japan = "440".equals(mcc) || "441".equals(mcc);
        return ch.frequencyKhz >= (japan ? 3_600_000L : 3_300_000L) ? NrClass.SUB6 : NrClass.REFARMED;
    }

    /**
     * 3GPP calls all of FR1 "sub-6 GHz", but in Japanese usage "Sub-6" means the 3.3 GHz+ bands
     * (n77/n78/n79); calling n28 (700 MHz) "Sub-6" reads as wrong, so lower FR1 is labelled by frequency.
     */
    static String frequencyClass(io.github.fortuneoflab.dualsimbands.radio.NrChannel ch, boolean japan) {
        if (ch.frequencyRange == 2) return t("ミリ波", "mmWave");
        // In Japan every NR carrier below 3.6 GHz reuses spectrum first licensed for LTE (n28, n3, n40, n41,
        // the 3.4-3.6 GHz B42 range): commonly called 転用5G. New 5G spectrum starts at 3.6 GHz (n77/n78/n79).
        if (japan) return ch.frequencyKhz >= 3_600_000L ? "Sub-6" : t("転用5G", "Refarmed");
        return ch.frequencyKhz >= 3_300_000L ? "Sub-6" : t("3.3 GHz 未満", "below 3.3 GHz");
    }

    /** " · RSRP -95 dBm · RSRQ -11 dB · SINR 8 dB" for a component, or "" when nothing is attributed. */
    public static String signal(io.github.fortuneoflab.dualsimbands.radio.ComponentSignal g) {
        if (g == null) return "";
        StringBuilder b = new StringBuilder();
        if (g.rsrp != null) b.append(" · RSRP ").append(g.rsrp).append(" dBm");
        if (g.rsrq != null) b.append(" · RSRQ ").append(g.rsrq).append(" dB");
        if (g.sinr != null) b.append(" · SINR ").append(g.sinr).append(" dB");
        return b.toString();
    }

    /** ServiceState bandwidth list; RAT and role per entry are not known. */
    public static String reportedBandwidths(int[] khz) {
        if (khz == null || khz.length == 0) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < khz.length; i++) {
            if (i > 0) b.append(" + ");
            b.append(mhz(khz[i]));
        }
        return b.append(t(" MHz（役割・方式は不明）", " MHz (role and RAT unknown)")).toString();
    }

    public static String lteChannel(LteChannel c) {
        if (c == null || c.earfcn < 0) return t("取得不可", "unavailable");
        StringBuilder b = new StringBuilder();
        b.append(c.band == 0 ? t("バンド不明", "band unknown") : "B" + c.band).append(" · EARFCN ").append(c.earfcn);
        if (!c.hasFrequency()) return b.append(t(" · 周波数不明", " · frequency unknown")).toString();
        b.append(" · DL ").append(Mhz.format(c.downlinkKhz)).append(" MHz");
        switch (c.uplink) {
            case DERIVED_DEFAULT_SEPARATION: b.append(" / UL ").append(Mhz.format(c.uplinkKhz)).append(t(" MHz（既定間隔）", " MHz (default spacing)")); break;
            case TDD_SHARED: b.append(t("（TDD）", " (TDD)")); break;
            case NONE_SDL: b.append(t("（SDL）", " (SDL)")); break;
            default: b.append(t(" / UL 不明", " / UL unknown"));
        }
        if (c.bandMismatch) b.append(t(" ※報告バンドと不一致", " *differs from the reported band"));
        return b.toString();
    }

    /**
     * Frequencies for a band block, uplink beside downlink: "DL 2120.0 / UL 1930.0 MHz" (FDD),
     * a single "2635.0 MHz" for TDD and SDL (the channel line names the duplex).
     */
    public static String lteFrequencies(LteChannel c) {
        if (c == null || !c.hasFrequency()) return t("周波数不明", "frequency unknown");
        String dl = Mhz.format(c.downlinkKhz);
        switch (c.uplink) {
            case DERIVED_DEFAULT_SEPARATION: return "DL " + dl + " / UL " + Mhz.format(c.uplinkKhz) + " MHz";
            case TDD_SHARED: case NONE_SDL: return dl + " MHz";
            default: return c.duplex == Duplex.FDD ? "DL " + dl + t(" MHz / UL 不明", " MHz / UL unknown") : dl + " MHz";
        }
    }

    /** Duplex tag for the channel line. */
    public static String lteDuplex(LteChannel c) {
        if (c == null) return null;
        switch (c.uplink) {
            case DERIVED_DEFAULT_SEPARATION: return "FDD";
            case TDD_SHARED: return "TDD";
            case NONE_SDL: return "SDL";
            default: return c.duplex == Duplex.FDD ? "FDD" : null;
        }
    }

    /** NR has no reported uplink frequency: FDD shows "UL 不明" next to the downlink. */
    public static String nrFrequencies(NrChannel c) {
        if (c == null || !c.hasFrequency()) return t("周波数不明", "frequency unknown");
        String dl = Mhz.format(c.frequencyKhz);
        Duplex d = nrDuplexType(c);
        if (d == null) return dl + " MHz";
        switch (d) {
            case TDD: case SDL: return dl + " MHz";
            default: return "DL " + dl + t(" MHz / UL 不明", " MHz / UL unknown");
        }
    }

    public static String nrDuplex(NrChannel c) {
        Duplex d = nrDuplexType(c);
        return d == null ? null : d.name();
    }

    /**
     * NR duplex from the band (TS 38.104). With the band undecided it is still known when every candidate
     * shares it (n77/n78 are both TDD); null otherwise.
     */
    static Duplex nrDuplexType(NrChannel c) {
        if (c == null) return null;
        int[] bands = c.band > 0 ? new int[]{c.band} : c.candidateBands;
        Duplex d = null;
        for (int n : bands) {
            io.github.fortuneoflab.dualsimbands.radio.NrBandTable.Band b = io.github.fortuneoflab.dualsimbands.radio.NrBandTable.byNumber(n);
            if (b == null || (d != null && d != b.duplex)) return null;
            d = b.duplex;
        }
        return d;
    }

    public static String nrChannel(NrChannel c) {
        if (c == null || c.nrArfcn < 0) return t("取得不可", "unavailable");
        StringBuilder b = new StringBuilder();
        if (c.band != 0) b.append("n").append(c.band);
        else if (c.candidateBands.length > 1) b.append(t("バンド未確定（候補 ", "band undecided (candidates ")).append(join(c.candidateBands)).append(t("）", ")"));
        else b.append(t("バンド不明", "band unknown"));
        b.append(" · NR-ARFCN ").append(c.nrArfcn);
        if (c.hasFrequency()) b.append(" · ").append(Mhz.format(c.frequencyKhz)).append(" MHz");
        if (c.bandMismatch) b.append(t(" ※報告バンドと不一致", " *differs from the reported band"));
        return b.toString();
    }

    public static String role(CellRole r) {
        switch (r) {
            case PRIMARY_SERVING: return t("主サービング", "primary serving");
            case SECONDARY_SERVING: return t("副サービング", "secondary serving");
            case REGISTERED: return t("登録セル", "registered cell");
            default: return t("未登録（近隣または役割不明）", "not registered (neighbor or role unknown)");
        }
    }

    public static String cellsUnavailable(RadioSnapshot.Cells.Status s) {
        switch (s) {
            case NO_PERMISSION: return t("周辺セルを取得できません（位置情報の権限がありません）", "Cannot read neighboring cells (no location permission)");
            case LOCATION_OFF: return t("周辺セルを取得できません（位置情報サービスがオフです）", "Cannot read neighboring cells (location services are off)");
            case ERROR: return t("周辺セルを取得できません（モデムがエラーを返しました）", "Cannot read neighboring cells (the modem returned an error)");
            case NOT_REQUESTED: return t("周辺セルを取得しています…", "Reading neighboring cells…");
            default: return null;
        }
    }

    public static String age(long ageMillis) {
        if (ageMillis < 0) return t("時刻不明", "time unknown");
        long s = ageMillis / 1000;
        if (s < 60) return s + t("秒前", "s ago");
        if (s < 3600) return (s / 60) + t("分前", " min ago");
        return (s / 3600) + t("時間前", " h ago");
    }

    /** "5G 表示 · 通信方式 LTE · NR 未確認" style one-liner for the card header and notifications. */
    public static String summary(RadioAssessment a) {
        StringBuilder b = new StringBuilder();
        if (a.displayOverride != null) b.append(display(a.displayOverride, a.displayNetwork).replaceAll(" ?[（(].*[）)]", ""));
        else b.append(t("表示 未取得", "display not read yet"));
        b.append(" · ");
        b.append(a.observedDataRatProvenance == Provenance.VERIFIED ? t("通信方式 ", "network ") + connection(a) : registration(a.dataReg));
        b.append(" · ");
        switch (a.nrStatus) {
            case SA_VERIFIED: b.append("NR SA"); break;
            case NSA_VERIFIED: b.append(t("NR 接続中", "NR connected")); break;
            case UNKNOWN: b.append(t("NR 判定不可", "NR undetermined")); break;
            default: b.append(t("NR 未確認", "NR unconfirmed"));
        }
        return b.toString();
    }

    public static String mhz(long khz) {
        return khz % 1000 == 0 ? String.valueOf(khz / 1000) : Mhz.format(khz);
    }

    /** "約 940 Mbps" below 1 Gbit/s, "約 2.1 Gbps" from there. */
    /** "~2.1 Gbps": in English in every language, like the "Total BW" and "Peak" around it (user's choice). */
    public static String rate(double mbps) {
        return mbps < 1000 ? "~" + Math.round(mbps) + " Mbps"
                : "~" + String.format(java.util.Locale.ROOT, "%.1f", mbps / 1000) + " Gbps";
    }

    public static String dbm(Integer v) { return v == null ? "—" : v + " dBm"; }
    public static String db(Integer v) { return v == null ? "—" : v + " dB"; }

    private static String join(int[] bands) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bands.length; i++) b.append(i == 0 ? "" : "/").append("n").append(bands[i]);
        return b.toString();
    }

    /** Status-bar indicator as shown in the state table: 4G / 4G+ / 5G / 5G+ ("+" dropped while idle). */
    public static String displayShort(RadioAssessment a) {
        DisplayOverride o = a.displayOverride;
        if (o == null) return "?";
        // While idle the "+" (CA / advanced NR) is left over from the last connection: show only 4G / 5G.
        if (a.ca.idle) {
            switch (o) {
                case LTE_CA: case LTE_ADVANCED_PRO: return "4G";
                case NR_NSA: case NR_NSA_MMWAVE: case NR_ADVANCED: return "5G";
                default: break;
            }
        }
        switch (o) {
            case LTE_CA: return "4G+";
            case LTE_ADVANCED_PRO: return "LTE+";
            case NR_NSA: return "5G";
            case NR_NSA_MMWAVE: case NR_ADVANCED: return "5G+";
            case NONE: return a.displayNetwork == Rat.NR ? "5G" : a.displayNetwork == Rat.LTE ? "4G" : rat(a.displayNetwork);
            default: return "?";
        }
    }

    /**
     * A 5G hint while the NR leg is known not to be added: no carrier is active (not the data SIM, or an
     * empty PhysicalChannelConfig), or PhysicalChannelConfig lists the active carriers and none is NR
     * (LTE only, on an NSA anchor). Shown as "待受中" (amber) rather than "未確認" (grey).
     */
    public static boolean standby(RadioAssessment a) {
        if (a.nrStatus != RadioAssessment.NrStatus.INDICATED_UNCONFIRMED) return false;
        if (a.ca.idle) return true;
        if (!a.ca.evidence.contains(CaAssessment.Evidence.PHYSICAL_CHANNEL_CONFIG)) return false;
        for (CaAssessment.Component comp : a.ca.components) if (comp.rat == Rat.NR) return false;
        return true;
    }

    /** NR column: Sub-6 / 転用5G / ミリ波 when connected, otherwise 待受中 / 未確認 / —. */
    public static String nrShort(RadioAssessment a, String mcc) {
        String cls = nrClass(a, mcc);
        return cls != null ? cls : nrChip(a);
    }

    private static String nrChip(RadioAssessment a) {
        switch (a.nrStatus) {
            // The column header already says "NR"; verified is the unmarked default, only doubt is labelled.
            case SA_VERIFIED: return t("接続中", "connected");
            case NSA_VERIFIED: return t("接続中", "connected");
            case INDICATED_UNCONFIRMED: return standby(a) ? t("待受中", "standby") : t("未確認", "unconfirmed");
            case NOT_INDICATED: return "—";
            default: return t("判定不可", "undetermined");
        }
    }

}
