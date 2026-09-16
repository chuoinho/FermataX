package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.policy.RuntimeHostMode;

public class ReadinessPrefsBuilderTest {
	@Test
	public void readinessIsAvailableOnlyFromThePhoneSettingsHost() {
		assertTrue(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.PHONE));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.AA_PROJECTION));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.MIRROR));
	}

	@Test
	public void closedOrInactiveViewsRejectRefreshCallbacks() {
		assertTrue(ReadinessPrefsBuilder.isRefreshAllowed(() -> true, false));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> false, false));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> true, true));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> {
			throw new IllegalStateException("stale view");
		}, false));
	}
}
