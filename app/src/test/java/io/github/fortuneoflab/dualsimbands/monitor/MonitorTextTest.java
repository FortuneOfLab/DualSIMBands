package io.github.fortuneoflab.dualsimbands.monitor;

import java.util.ArrayList;
import java.util.List;
import io.github.fortuneoflab.dualsimbands.radio.DisplayOverride;
import io.github.fortuneoflab.dualsimbands.radio.LteCell;
import io.github.fortuneoflab.dualsimbands.radio.RadioAssessment;
import io.github.fortuneoflab.dualsimbands.radio.RadioSnapshot;
import io.github.fortuneoflab.dualsimbands.radio.RadioStateReducer;
import io.github.fortuneoflab.dualsimbands.radio.Rat;
import org.junit.Test;
import static org.junit.Assert.*;
import io.github.fortuneoflab.dualsimbands.PrivilegedProbe;

public class MonitorTextTest {
    private static final long NOW = 100_000;

    private static MonitorText.Line line(int slot, String carrier, boolean data, DisplayOverride d, int channel,
                                         List<LteCell> cells) {
        RadioSnapshot.Service svc = new RadioSnapshot.Service(RadioSnapshot.RegState.IN_SERVICE,
                RadioSnapshot.RegState.IN_SERVICE, Rat.LTE, Rat.LTE, channel, null, NOW);
        RadioSnapshot.Cells c = cells == null ? RadioSnapshot.Cells.unavailable(RadioSnapshot.Cells.Status.LOCATION_OFF)
                : new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, cells, null, NOW);
        RadioSnapshot s = new RadioSnapshot(slot + 2, slot, data, svc, new RadioSnapshot.Display(Rat.LTE, d, NOW),
                new RadioSnapshot.Signal(-78, -6, 27, null, null, null, NOW), c, null, -1);
        RadioAssessment a = RadioStateReducer.reduce(s, NOW, 30_000);
        return new MonitorText.Line(slot, carrier, s, a);
    }

    @Test public void dataSimLeadsAndNrStaysUnconfirmed() {
        List<MonitorText.Line> lines = new ArrayList<>();
        lines.add(line(1, "docomo", false, DisplayOverride.NONE, 6525, null));
        lines.add(line(0, "au", true, DisplayOverride.NR_NSA, 100, null));
        MonitorText.Content c = MonitorText.content(lines, 0, true);
        assertEquals("SIM 1 au · 5G · LTE · NR 未確認", c.title);
        assertEquals("B1 2120.0 MHz · RSRP -78 dBm", c.text);
        assertTrue(c.expanded.indexOf("SIM 1 au") < c.expanded.indexOf("SIM 2 docomo"));
        assertTrue(c.expanded.contains("B21 1503.4 MHz"));
        assertTrue(c.expanded.contains("更新 "));
        assertFalse(c.expanded.contains("キャリア"));
    }

    @Test public void screenOffIsStatedNotHidden() {
        List<MonitorText.Line> lines = new ArrayList<>();
        lines.add(line(0, "au", true, DisplayOverride.NONE, 100, null));
        assertTrue(MonitorText.content(lines, 0, false).expanded.contains("画面オフの間は止めています"));
    }

    @Test public void verifiedCaIsShownWithTotal() {
        List<LteCell> cells = new ArrayList<>();
        cells.add(LteCell.fromRaw(true, 1, 100, new int[]{1}, 1, 1, 1, 20000, -80, -6, -60, 20, NOW));
        cells.add(LteCell.fromRaw(false, 2, 1500, new int[]{3}, 2, 1, 2, 20000, -85, -7, -62, 18, NOW));
        List<MonitorText.Line> lines = new ArrayList<>();
        lines.add(line(0, "au", true, DisplayOverride.LTE_CA, 100, cells));
        assertTrue(MonitorText.content(lines, 0, true).expanded.contains("B1 + B3"));
    }

    @Test public void noDataYet() {
        assertEquals("Dual SIM Bands で監視中", MonitorText.content(new ArrayList<>(), 0, true).title);
    }

    /** Each reason the composition is missing has its own words and fix; ready adds nothing. */
    @Test public void blockedReasons() {
        MonitorText.Content c = MonitorText.content(new ArrayList<>(), 0, true, PrivilegedProbe.Block.SHIZUKU_NOT_RUNNING);
        assertTrue(c.text.startsWith("Shizuku 未接続 · "));
        assertTrue(c.expanded.startsWith("キャリア構成：Shizuku 未接続（Shizuku の起動を確認し、このアプリを開き直す）\n"));
        c = MonitorText.content(new ArrayList<>(), 0, true, PrivilegedProbe.Block.SHIZUKU_NOT_GRANTED);
        assertTrue(c.text.startsWith("Shizuku 未許可 · "));
        assertTrue(c.expanded.startsWith("キャリア構成：Shizuku の使用が許可されていません（アプリを開いて許可）"));
        c = MonitorText.content(new ArrayList<>(), 0, true, PrivilegedProbe.Block.PHONE_PERMISSION);
        assertTrue(c.text.startsWith("電話の権限なし · "));
        assertTrue(c.expanded.startsWith("キャリア構成：電話の権限がありません（アプリを開いて許可）"));
        assertFalse(MonitorText.content(new ArrayList<>(), 0, true, PrivilegedProbe.Block.NONE).expanded.contains("キャリア構成："));
    }

    @Test public void missingServingChannelIsNotGuessed() {
        List<MonitorText.Line> lines = new ArrayList<>();
        lines.add(line(0, "au", true, DisplayOverride.NONE, -1, null));
        assertTrue(MonitorText.content(lines, 0, true).text.startsWith("チャネル不明"));
    }
}
