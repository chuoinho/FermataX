package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChromePolicyTest {
	@Test
	public void autoTopBackOnlyAppearsOnNonDashboardFramePages() {
		assertTrue(ChromePolicy.isAutoTopBackVisible(true, true, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(false, true, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(true, false, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(true, true, true));
	}
}
