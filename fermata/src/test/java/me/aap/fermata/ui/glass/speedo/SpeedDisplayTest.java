package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.glass.speedo.TripEngine.Signal;

public class SpeedDisplayTest {

    @Test
    public void showsZeroWhileSearchingInsteadOfADash() {
        SpeedDisplay d = new SpeedDisplay();
        assertEquals(0f, d.show(-1, Signal.WAITING), 0);
    }

    @Test
    public void keepsTheLastNumberWhenTheEngineLosesSpeed() {
        SpeedDisplay d = new SpeedDisplay();
        assertEquals(54f, d.show(54, Signal.TRACKING), 0);
        assertEquals("fixes without speed", 54f, d.show(-1, Signal.TRACKING), 0);
        assertEquals("stale after 8 s", 54f, d.show(-1, Signal.STALE), 0);
        assertEquals(60f, d.show(60, Signal.TRACKING), 0);
    }

    @Test
    public void nothingToMeasureWithShowsNoNumber() {
        SpeedDisplay d = new SpeedDisplay();
        d.show(54, Signal.TRACKING);
        for (Signal s : new Signal[]{Signal.GPS_OFF, Signal.PERMISSION, Signal.ERROR, Signal.PAUSED,
                Signal.BACKGROUND_BLOCKED}) {
            assertEquals(s.name(), -1f, d.show(-1, s), 0);
        }
        assertEquals("no stale value after GPS comes back", 0f, d.show(-1, Signal.WAITING), 0);
    }

    @Test
    public void zeroHasHysteresisAgainstStopJitter() {
        SpeedDisplay d = new SpeedDisplay();
        assertEquals(0f, d.show(0, Signal.TRACKING), 0);
        // Doppler noise at a red light stays below the exit threshold.
        for (float noise : new float[]{1.4f, 2.9f, 0.5f, 3.4f, 2.2f}) {
            assertEquals("noise " + noise, 0f, d.show(noise, Signal.TRACKING), 0);
        }
        assertEquals("moving off", 4f, d.show(4f, Signal.TRACKING), 0);
        assertEquals("creeping stays visible", 2.5f, d.show(2.5f, Signal.TRACKING), 0);
        assertEquals("stopping", 0f, d.show(1.9f, Signal.TRACKING), 0);
    }
}
