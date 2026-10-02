package me.aap.fermata.addon.web.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TikTokWebAddonBindingTest {

	@Test
	public void testAddonProperties() {
		TikTokAddon addon = new TikTokAddon();
		assertEquals(me.aap.fermata.R.id.tiktok_fragment, addon.getAddonId());
		assertEquals("tiktok", addon.getVoiceTarget());
		assertNotNull(addon.createFragment());
		assertTrue(addon.createFragment() instanceof TikTokWebFragment);
	}

	@Test
	public void testFragmentProperties() {
		TikTokWebFragment fragment = new TikTokWebFragment();
		assertEquals(me.aap.fermata.R.id.tiktok_fragment, fragment.getFragmentId());
		assertFalse(fragment.isDesktopVersionSupported());
		assertNotNull(fragment.createWebClient());
		assertTrue(fragment.createWebClient() instanceof TikTokWebClient);
		assertEquals("https://www.tiktok.com/search?q=", fragment.getSearchUrl());
	}

	@Test
	public void testOpenOnCarHandoffPolicies() {
		assertTrue(TikTokOpenOnCarBridge.acceptsRoute("https://www.tiktok.com/foryou"));
		assertTrue(TikTokOpenOnCarBridge.acceptsRoute("https://www.tiktok.com/@user/video/123"));
		assertFalse(TikTokOpenOnCarBridge.acceptsRoute("https://www.tiktok.com/login"));
		assertFalse(TikTokOpenOnCarBridge.acceptsRoute("https://youtube.com"));

		assertEquals("https://www.tiktok.com/@user/video/123",
				TikTokOpenOnCarBridge.normalizeRouteForCar("https://m.tiktok.com/@user/video/123"));
		assertEquals("https://www.tiktok.com/foryou",
				TikTokOpenOnCarBridge.normalizeRouteForCar("https://www.tiktok.com/foryou"));
	}
}
