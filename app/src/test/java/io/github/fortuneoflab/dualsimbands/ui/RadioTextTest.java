package io.github.fortuneoflab.dualsimbands.ui;

import java.util.ArrayList;
import java.util.List;
import io.github.fortuneoflab.dualsimbands.radio.DisplayOverride;
import io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator;
import io.github.fortuneoflab.dualsimbands.radio.LteCell;
import io.github.fortuneoflab.dualsimbands.radio.Provenance;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.RadioStateReducer;
import io.github.fortuneoflab.dualsimbands.radio.Rat;
import org.junit.Test;
import static org.junit.Assert.*;

public class RadioTextTest {
    private static final long NOW = 100_000;

    private static RadioAssessment assess(RadioSnapshot.RegState reg, Rat dataRat, DisplayOverride display,
                                          int[] bandwidths, List<LteCell> lte) {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(reg, reg, Rat.LTE, dataRat, 100, bandwidths, NOW);
        RadioSnapshot.Display d = display == null ? null : new RadioSnapshot.Display(Rat.LTE, display, NOW);
        RadioSnapshot.Cells cells = lte == null ? RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.NO_PERMISSION)
                : new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, lte, null, NOW);
        return RadioStateReducer.reduce(new RadioSnapshot(2, 0, true, svc, d, null, cells, null, -1), NOW, 30_000);
    }

    @Test public void specExampleFiveGIndicatedLteObservedNrUnconfirmed() {
        RadioAssessment a = assess(RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, DisplayOverride.NR_NSA, null, null);
        assertEquals("5G 表示 · 通信方式 LTE · NR 未確認", RadioText.summary(a));
        assertEquals("NR 未確認（5G は表示のみ）", RadioText.nr(a));
        assertEquals("5G 表示（NR_NSA）", RadioText.display(a.displayOverride, a.displayNetwork));
    }

    @Test public void specExampleLteCaIndicatedComponentsUnconfirmed() {
        RadioAssessment a = assess(RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, DisplayOverride.LTE_CA, null, null);
        assertEquals("LTE-CA は表示のみ。PCC/SCC の構成は未確認", RadioText.ca(a.ca));
        assertEquals("4G+ 表示（LTE_CA）", RadioText.display(a.displayOverride, a.displayNetwork));
    }

    @Test public void notConnectedIsDistinctFromUnconfirmedAndUnavailable() {
        RadioAssessment off = assess(RadioSnapshot.RegState.POWER_OFF, Rat.UNKNOWN, DisplayOverride.NONE, null, null);
        assertTrue(RadioText.observedRat(off).startsWith("未接続"));
        assertTrue(RadioText.nr(off).contains("未接続"));
        assertFalse(RadioText.nr(off).contains("未確認"));

        RadioAssessment on = assess(RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, DisplayOverride.NONE, null, null);
        assertTrue(RadioText.nr(on).startsWith("NR 未確認"));
        assertFalse(RadioText.nr(on).contains("未接続"));
        assertTrue(RadioText.cellsUnavailable(on.cellsStatus).contains("取得できません"));
    }

    @Test public void reportedBandwidthsAreLabelledAsRoleUnknown() {
        RadioAssessment a = assess(RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, DisplayOverride.NR_NSA,
                new int[]{20000, 20000, 20000, 20000, 100000}, null);
        assertEquals("20 + 20 + 20 + 20 + 100 MHz（役割・方式は不明）", RadioText.reportedBandwidths(a.ca.reportedBandwidthsKhz));
        assertEquals("複数の帯域幅が報告済み。CA の構成は未確認", RadioText.ca(a.ca));
        assertNull(RadioText.reportedBandwidths(new int[0]));
    }

    @Test public void verifiedCaShowsCountAndTotalOnlyWhenKnown() {
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, 1, 100, new int[]{1}, 1, 1, 1, 20000, -90, -10, -60, 10, NOW));
        cells.add(LteCell.fromRaw(false, 2, 6525, new int[]{21}, 2, 1, 2, 15000, -95, -11, -65, 8, NOW));
        RadioAssessment a = assess(RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, DisplayOverride.LTE_CA, null, cells);
        assertEquals("CA：2 キャリア（合計 35 MHz）", RadioText.ca(a.ca));
        assertEquals(Provenance.VERIFIED, a.servingLte.bandProvenance);
    }

    @Test public void channels() {
        assertEquals("B1 · EARFCN 100 · DL 2120.0 MHz / UL 1930.0 MHz（既定間隔）",
                RadioText.lteChannel(FrequencyCalculator.lte(100, null)));
        assertEquals("B42 · EARFCN 41590 · DL 3400.0 MHz（TDD）",
                RadioText.lteChannel(FrequencyCalculator.lte(41590, null)));
        assertEquals("取得不可", RadioText.lteChannel(FrequencyCalculator.lte(Integer.MAX_VALUE, null)));
        assertEquals("バンド未確定（候補 n48/n77/n78） · NR-ARFCN 643334 · 3650.01 MHz",
                RadioText.nrChannel(FrequencyCalculator.nr(643334, null)));
        assertEquals("n78 · NR-ARFCN 643334 · 3650.01 MHz",
                RadioText.nrChannel(FrequencyCalculator.nr(643334, new int[]{78})));
    }

    @Test public void ages() {
        assertEquals("0秒前", RadioText.age(999));
        assertEquals("59秒前", RadioText.age(59_999));
        assertEquals("1分前", RadioText.age(60_000));
        assertEquals("時刻不明", RadioText.age(-1));
    }

    @Test public void uplinkSitsBesideDownlink() {
        assertEquals("DL 2120.0 / UL 1930.0 MHz", RadioText.lteFrequencies(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.lte(100, null)));
        assertEquals("FDD", RadioText.lteDuplex(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.lte(100, null)));
        assertEquals("2635.0 MHz", RadioText.lteFrequencies(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.lte(41040, null)));
        assertEquals("TDD", RadioText.lteDuplex(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.lte(41040, null)));
        // n77/n78 undecided, both TDD.
        assertEquals("3709.92 MHz", RadioText.nrFrequencies(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.nr(647328, null)));
        assertEquals("DL 777.85 MHz / UL 不明", RadioText.nrFrequencies(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.nr(155570, null)));
        assertEquals("FDD", RadioText.nrDuplex(io.github.fortuneoflab.dualsimbands.radio.FrequencyCalculator.nr(155570, null)));
    }

}
