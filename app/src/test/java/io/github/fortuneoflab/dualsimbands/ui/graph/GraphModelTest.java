package io.github.fortuneoflab.dualsimbands.ui.graph;

import org.junit.Test;
import static org.junit.Assert.*;

public class GraphModelTest {
    @Test public void rsrpLevels() {
        assertEquals(Quality.Level.GOOD, Quality.level(Quality.Metric.RSRP, -78));
        assertEquals(Quality.Level.GOOD, Quality.level(Quality.Metric.RSRP, -90));
        assertEquals(Quality.Level.FAIR, Quality.level(Quality.Metric.RSRP, -98));
        assertEquals(Quality.Level.FAIR, Quality.level(Quality.Metric.RSRP, -105));
        assertEquals(Quality.Level.POOR, Quality.level(Quality.Metric.RSRP, -106));
        assertEquals(Quality.Level.UNKNOWN, Quality.level(Quality.Metric.RSRP, null));
    }

    @Test public void sinrAndRsrqLevels() {
        assertEquals(Quality.Level.GOOD, Quality.level(Quality.Metric.SINR, 27));
        assertEquals(Quality.Level.FAIR, Quality.level(Quality.Metric.SINR, 3));
        assertEquals(Quality.Level.POOR, Quality.level(Quality.Metric.SINR, -4));
        assertEquals(Quality.Level.GOOD, Quality.level(Quality.Metric.RSRQ, -6));
        assertEquals(Quality.Level.POOR, Quality.level(Quality.Metric.RSRQ, -16));
    }

    @Test public void fractionIsClampedAndUnknownHasNoBar() {
        assertEquals(0f, Quality.fraction(Quality.Metric.RSRP, -150), 0f);
        assertEquals(1f, Quality.fraction(Quality.Metric.RSRP, -30), 0f);
        assertEquals(0.5f, Quality.fraction(Quality.Metric.RSRP, -92), 0.001f);
        assertEquals(-1f, Quality.fraction(Quality.Metric.RSRP, null), 0f);
        assertEquals("不明", Quality.label(Quality.Level.UNKNOWN));
    }

    @Test public void rssiLevels() {
        assertEquals(Quality.Level.GOOD, Quality.level(Quality.Metric.RSSI, -65));
        assertEquals(Quality.Level.FAIR, Quality.level(Quality.Metric.RSSI, -80));
        assertEquals(Quality.Level.POOR, Quality.level(Quality.Metric.RSSI, -90));
    }
}
