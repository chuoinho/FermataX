package me.aap.fermata.addon;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AddonInfoTest {
	@Test
	public void resolverSchemesAreNormalizedAndOptional() {
		AddonInfo info = new AddonInfo("web", "example.WebAddon", 1, 1, 1,
				1, false, true, true, true, "", "youtube", "youtube", " YouTube, Podcast ");

		assertTrue(info.hasResolverScheme("youtube"));
		assertTrue(info.hasResolverScheme(" PODCAST "));
		assertFalse(info.hasResolverScheme("radio"));
	}

	@Test
	public void legacyConstructorHasNoResolverSchemes() {
		AddonInfo info = new AddonInfo("legacy", "example.Legacy", 1, 1, 1,
				1, false, true, true, true, "", "dashboard,navigation", "");

		assertFalse(info.hasResolverScheme("youtube"));
	}
}
