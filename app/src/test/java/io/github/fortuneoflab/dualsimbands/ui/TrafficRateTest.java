package io.github.fortuneoflab.dualsimbands.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrafficRateTest {
    @Test public void anHourAtEachInterval() {
        assertEquals("5", AppPrefs.trafficMbPerHour(2));
        assertEquals("1", AppPrefs.trafficMbPerHour(10));
        assertEquals("0.4", AppPrefs.trafficMbPerHour(30));
        assertEquals("0.2", AppPrefs.trafficMbPerHour(60));
    }

    @Test public void everyChoiceHasALabel() {
        for (int s : AppPrefs.TRAFFIC_INTERVALS) assertFalse(AppPrefs.trafficMbPerHour(s).isEmpty());
    }
}
