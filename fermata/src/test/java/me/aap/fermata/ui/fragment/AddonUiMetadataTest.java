package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.AddonInfo;

public class AddonUiMetadataTest {
	@Test
	public void explicitCapabilitiesDrivePresentationWithoutClassNameHeuristics() {
		AddonInfo tv = info("example.UnrelatedOne", "dashboard,navigation,tv");
		AddonInfo youtube = info("example.UnrelatedTwo", "dashboard,navigation,youtube");
		AddonInfo radio = info("example.UnrelatedThree", "dashboard,navigation,radio");
		AddonInfo podcast = info("example.UnrelatedFour", "dashboard,navigation,podcast");
		AddonInfo audiobook = info("example.UnrelatedFive", "dashboard,navigation,audiobook");
		AddonInfo web = info("example.UnrelatedSix", "dashboard,navigation,web");
		AddonInfo felex = info("example.UnrelatedSeven", "dashboard,navigation,felex");

		assertEquals(AddonUiMetadata.Role.TV, AddonUiMetadata.role(tv));
		assertEquals(AddonUiMetadata.Role.YOUTUBE, AddonUiMetadata.role(youtube));
		assertEquals(AddonUiMetadata.Role.RADIO, AddonUiMetadata.role(radio));
		assertEquals(AddonUiMetadata.Role.PODCAST, AddonUiMetadata.role(podcast));
		assertEquals(AddonUiMetadata.Role.AUDIOBOOK, AddonUiMetadata.role(audiobook));
		assertEquals(AddonUiMetadata.Role.WEB, AddonUiMetadata.role(web));
		assertEquals(AddonUiMetadata.Role.FELEX, AddonUiMetadata.role(felex));
		assertEquals(0, AddonUiMetadata.priority(tv));
		assertEquals(1, AddonUiMetadata.priority(youtube));
		assertEquals(2, AddonUiMetadata.priority(radio));
		assertEquals(3, AddonUiMetadata.priority(podcast));
		assertEquals(4, AddonUiMetadata.priority(audiobook));
		assertEquals(5, AddonUiMetadata.priority(web));
		assertEquals(8, AddonUiMetadata.priority(felex));
	}

	@Test
	public void legacyFragmentMetadataKeepsDashboardAndNavigationBehavior() {
		AddonInfo legacy = new AddonInfo("legacy", "legacy.Fragment", 1, 1, 1, 1,
				false, true, true, false, "");

		assertTrue(legacy.hasCapability(AddonCapability.DASHBOARD));
		assertTrue(legacy.hasCapability(AddonCapability.NAVIGATION));
		assertTrue(AddonUiMetadata.isDashboardItem(legacy));
		assertTrue(AddonUiMetadata.isNavigationItem(legacy));
		assertEquals(AddonUiMetadata.Role.GENERIC, AddonUiMetadata.role(legacy));
	}

	@Test
	public void fragmentCanExplicitlyOptOutOfDashboardAndNavigation() {
		AddonInfo hidden = info("example.Hidden", "");

		assertFalse(AddonUiMetadata.isDashboardItem(hidden));
		assertFalse(AddonUiMetadata.isNavigationItem(hidden));
	}

	@Test
	public void dashboardAndNavigationCapabilitiesAreIndependent() {
		AddonInfo dashboardOnly = info("example.DashboardOnly", "dashboard");
		AddonInfo navigationOnly = info("example.NavigationOnly", "navigation");

		assertTrue(AddonUiMetadata.isDashboardItem(dashboardOnly));
		assertFalse(AddonUiMetadata.isNavigationItem(dashboardOnly));
		assertFalse(AddonUiMetadata.isDashboardItem(navigationOnly));
		assertTrue(AddonUiMetadata.isNavigationItem(navigationOnly));
	}

	private static AddonInfo info(String className, String capabilities) {
		return new AddonInfo("module", className, 1, 1, 1, 1,
				false, true, true, false, "", capabilities);
	}
}
