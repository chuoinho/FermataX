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

	@Test
	public void testOpenOnCarFragmentBehavior() {
		TikTokWebFragment fragment = new TikTokWebFragment();
		me.aap.fermata.auto.OpenOnCarToken token = new me.aap.fermata.auto.OpenOnCarToken(1, 1, 1, 1, 1);

		// Cancelled when not current
		me.aap.fermata.auto.OpenOnCarRequest reqValid = new me.aap.fermata.auto.OpenOnCarRequest(
				me.aap.fermata.auto.OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.tiktok_fragment,
				"https://www.tiktok.com/@user/video/123", token);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.CANCELLED,
				fragment.openOnCar(reqValid, () -> false));

		// Failed when kind is wrong
		me.aap.fermata.auto.OpenOnCarRequest reqWrongKind = new me.aap.fermata.auto.OpenOnCarRequest(
				me.aap.fermata.auto.OpenOnCarKind.STREMIO_PLAYER,
				me.aap.fermata.R.id.tiktok_fragment,
				"https://www.tiktok.com/@user/video/123", token);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.FAILED,
				fragment.openOnCar(reqWrongKind, () -> true));

		// Failed when addon ID is wrong
		me.aap.fermata.auto.OpenOnCarRequest reqWrongAddon = new me.aap.fermata.auto.OpenOnCarRequest(
				me.aap.fermata.auto.OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.web_browser_fragment,
				"https://www.tiktok.com/@user/video/123", token);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.FAILED,
				fragment.openOnCar(reqWrongAddon, () -> true));

		// Failed when URL route is unpersisted/rejected (e.g. login)
		me.aap.fermata.auto.OpenOnCarRequest reqLogin = new me.aap.fermata.auto.OpenOnCarRequest(
				me.aap.fermata.auto.OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.tiktok_fragment,
				"https://www.tiktok.com/login", token);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.FAILED,
				fragment.openOnCar(reqLogin, () -> true));

		// Not ready when web is not attached
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.NOT_READY,
				fragment.openOnCar(reqValid, () -> true));
	}
}
