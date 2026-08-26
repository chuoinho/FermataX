package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioWebNavigationPolicyTest {
	@Test
	public void blocksOnlyExternalAppSchemes() {
		assertTrue(StremioWebNavigationPolicy.blocksExternalScheme("intent"));
		assertTrue(StremioWebNavigationPolicy.blocksExternalScheme("STREMIO"));
		assertFalse(StremioWebNavigationPolicy.blocksExternalScheme("https"));
		assertFalse(StremioWebNavigationPolicy.blocksExternalScheme(null));
	}
}
