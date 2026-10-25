package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.glass.speedo.TripModePolicy.Mode;

public class TripModePolicyTest {
    @Test public void backgroundPermissionEnablesLocationWithoutLegacyOptIn() {
        assertEquals(Mode.ON, TripModePolicy.evaluate(true, true, true));
    }

    @Test public void locationModeDependsOnPermissionAndBuildSupport() {
        assertEquals(Mode.OFF, TripModePolicy.evaluate(true, true, false));
        assertEquals(Mode.NEEDS_LOCATION_PERMISSION, TripModePolicy.evaluate(false, false, true));
        assertEquals(Mode.NEEDS_LOCATION_PERMISSION, TripModePolicy.evaluate(false, true, true));
        assertEquals(Mode.NEEDS_BACKGROUND_PERMISSION, TripModePolicy.evaluate(true, false, true));
        assertEquals(Mode.ON, TripModePolicy.evaluate(true, true, true));
    }

    @Test public void accessStateAndOneTimePromptFollowThePermissionLevel() {
        assertEquals(TripModePolicy.Access.NOT_GRANTED, TripModePolicy.access(false, false));
        assertEquals(TripModePolicy.Access.WHILE_USING, TripModePolicy.access(true, false));
        assertEquals(TripModePolicy.Access.ALWAYS, TripModePolicy.access(true, true));
        assertTrue(TripModePolicy.shouldOfferBackgroundPrompt(true, false, false, true));
        assertFalse(TripModePolicy.shouldOfferBackgroundPrompt(true, false, true, true));
        assertFalse(TripModePolicy.shouldOfferBackgroundPrompt(true, true, false, true));
        assertFalse(TripModePolicy.shouldOfferBackgroundPrompt(true, false, false, false));
    }
}
