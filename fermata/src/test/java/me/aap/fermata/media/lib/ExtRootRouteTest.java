package me.aap.fermata.media.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;

public class ExtRootRouteTest {
	@Test
	public void legacyConstructorRemainsUnrouted() {
		assertNull(new ExtRoot("external", null).getRouteCapability());
	}

	@Test
	public void externalRootCarriesExplicitAddonRouteCapability() {
		assertEquals(AddonCapability.YOUTUBE,
				new ExtRoot("opaque-id", null, AddonCapability.YOUTUBE).getRouteCapability());
		assertEquals(AddonCapability.PODCAST,
				new ExtRoot("podcast", null, AddonCapability.PODCAST).getRouteCapability());
	}
}
