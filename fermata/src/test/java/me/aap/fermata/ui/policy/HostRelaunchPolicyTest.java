package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostRelaunchPolicyTest {
	@Test
	public void plainMainIntentKeepsInterruptedPresentation() {
		assertFalse(HostRelaunchPolicy.startsNewNavigation("android.intent.action.MAIN", false));
		assertFalse(HostRelaunchPolicy.startsNewNavigation(null, false));
	}

	@Test
	public void contentOrExplicitActionStartsNewNavigation() {
		assertTrue(HostRelaunchPolicy.startsNewNavigation("android.intent.action.MAIN", true));
		assertTrue(HostRelaunchPolicy.startsNewNavigation("android.intent.action.VIEW", false));
		assertTrue(HostRelaunchPolicy.startsNewNavigation("me.app.fermata.PLAY", false));
	}
}
