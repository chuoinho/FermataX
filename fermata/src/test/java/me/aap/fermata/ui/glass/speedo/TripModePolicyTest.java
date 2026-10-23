package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.glass.speedo.TripModePolicy.Mode;

public class TripModePolicyTest {
    @Test public void offUnlessEnabledAndSupported() {
        assertEquals(Mode.OFF, TripModePolicy.evaluate(false, true, true, true));
        assertEquals(Mode.OFF, TripModePolicy.evaluate(true, true, true, false));
    }

    @Test public void enabledNeedsForegroundThenBackgroundPermission() {
        assertEquals(Mode.NEEDS_LOCATION_PERMISSION, TripModePolicy.evaluate(true, false, false, true));
        assertEquals(Mode.NEEDS_LOCATION_PERMISSION, TripModePolicy.evaluate(true, false, true, true));
        assertEquals(Mode.NEEDS_BACKGROUND_PERMISSION, TripModePolicy.evaluate(true, true, false, true));
        assertEquals(Mode.ON, TripModePolicy.evaluate(true, true, true, true));
    }
}
