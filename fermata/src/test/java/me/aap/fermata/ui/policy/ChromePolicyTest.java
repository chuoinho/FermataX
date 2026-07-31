package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChromePolicyTest {
	@Test
	public void autoTopBackOnlyAppearsOnNonDashboardFramePages() {
		assertTrue(ChromePolicy.isAutoTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, true, false, false));
		assertTrue(ChromePolicy.isAutoTopBackVisible(RuntimeHostMode.MIRROR, true, false, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(RuntimeHostMode.PHONE, true, false, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, false, false, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, true, true, false));
		assertFalse(ChromePolicy.isAutoTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, true, false, true));
	}
}
