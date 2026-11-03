package io.github.fortuneoflab.dualsimbands.traffic;

import org.junit.Test;
import static org.junit.Assert.*;

public class DataSimBytesTest {
    @Test public void countsTheRunWhileTheDataSimStays() {
        DataSimBytes b = new DataSimBytes();
        b.update(6, 0);
        b.update(6, 3000);
        b.update(6, 5000);
        assertEquals(5000, b.bytes());
    }

    @Test public void anotherDataSimStartsFromZero() {
        DataSimBytes b = new DataSimBytes();
        b.update(6, 0);
        b.update(6, 40_000);
        b.update(7, 41_000);
        assertEquals("the first count after the change goes to the new SIM", 1000, b.bytes());
        b.update(7, 45_000);
        assertEquals(5000, b.bytes());
    }

    @Test public void backToTheFirstSimStartsAgainToo() {
        DataSimBytes b = new DataSimBytes();
        b.update(6, 10_000);
        b.update(7, 20_000);
        b.update(6, 21_000);
        assertEquals(1000, b.bytes());
    }

    @Test public void noDataSimForAWhile() {
        DataSimBytes b = new DataSimBytes();
        b.update(6, 10_000);
        b.update(DataSimBytes.NONE, 10_000);
        assertEquals(0, b.bytes());
        b.update(7, 12_000);
        assertEquals(2000, b.bytes());
    }

    @Test public void restartForgetsThePreviousRun() {
        DataSimBytes b = new DataSimBytes();
        b.update(6, 10_000);
        b.update(7, 30_000);
        b.restart();
        assertEquals(0, b.bytes());
        b.update(7, 0);
        b.update(7, 2000);
        assertEquals(2000, b.bytes());
    }
}
