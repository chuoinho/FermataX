package me.aap.fermata.addon.stremio.ui.config;

import static me.aap.fermata.addon.stremio.ui.config.StremioConfigUrlPolicy.Decision.BLOCKED;
import static me.aap.fermata.addon.stremio.ui.config.StremioConfigUrlPolicy.Decision.COMPLETE;
import static me.aap.fermata.addon.stremio.ui.config.StremioConfigUrlPolicy.Decision.NAVIGATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioConfigUrlPolicyTest {
	@Test
	public void keepsNavigationOnConfiguredHttpsOrigin() {
		StremioConfigUrlPolicy policy = new StremioConfigUrlPolicy(
				"https://config.example.com/setup", false);

		assertEquals(NAVIGATE, policy.evaluate("https://config.example.com/account?step=2"));
		assertEquals(NAVIGATE, policy.evaluate("https://CONFIG.EXAMPLE.COM:443/finish#ready"));
		assertEquals(BLOCKED, policy.evaluate("https://cdn.example.com/script.js"));
		assertTrue(policy.isAllowedResource("https://cdn.example.com/script.js", false));
		assertFalse(policy.isAllowedResource("https://cdn.example.com/script.js", true));
		assertEquals(BLOCKED, policy.evaluate("https://config.example.com.evil.test/setup"));
		assertEquals(BLOCKED, policy.evaluate("https://config.example.com:444/setup"));
	}

	@Test
	public void interceptsOnlySameProviderManifestDestinations() {
		StremioConfigUrlPolicy policy = new StremioConfigUrlPolicy(
				"https://config.example.com/setup", false);

		assertEquals(COMPLETE,
				policy.evaluate("https://config.example.com/token/manifest.json?key=secret"));
		assertEquals(COMPLETE,
				policy.evaluate("stremio://config.example.com/token/manifest.json?key=secret"));
		assertEquals(BLOCKED,
				policy.evaluate("stremio://evil.example/token/manifest.json"));
		assertEquals(BLOCKED, policy.evaluate("stremio://config.example.com/token/catalog.json"));
	}

	@Test
	public void blocksDangerousAndAmbiguousSchemes() {
		StremioConfigUrlPolicy policy = new StremioConfigUrlPolicy(
				"https://config.example.com/setup", false);

		assertEquals(BLOCKED, policy.evaluate("file:///data/data/app/shared_prefs/private.xml"));
		assertEquals(BLOCKED, policy.evaluate("content://settings/system"));
		assertEquals(BLOCKED, policy.evaluate("javascript:alert(1)"));
		assertEquals(BLOCKED, policy.evaluate("intent://config.example.com/#Intent;end"));
		assertEquals(BLOCKED, policy.evaluate("custom://config.example.com/setup"));
		assertEquals(BLOCKED, policy.evaluate("https://user:pass@config.example.com/setup"));
		assertFalse(policy.isAllowedResource(
				"stremio://config.example.com/token/manifest.json"));
		assertTrue(policy.isAllowedResource(
				"https://config.example.com/token/manifest.json"));
		assertFalse(policy.isAllowedResource("http://cdn.example.com/app.js", false));
	}

	@Test
	public void cleartextRequiresConsentAndRemainsOriginBound() {
		assertThrows(IllegalArgumentException.class,
				() -> new StremioConfigUrlPolicy("http://192.168.1.10/config", false));
		StremioConfigUrlPolicy policy = new StremioConfigUrlPolicy(
				"http://192.168.1.10:8080/config", true);

		assertEquals(NAVIGATE, policy.evaluate("http://192.168.1.10:8080/next"));
		assertEquals(COMPLETE,
				policy.evaluate("stremio://192.168.1.10:8080/token/manifest.json"));
		assertEquals(BLOCKED, policy.evaluate("https://192.168.1.10:8080/next"));
		assertEquals(BLOCKED, policy.evaluate("http://192.168.1.11:8080/next"));
	}

	@Test
	public void normalizesHostCaseAndTrailingDot() {
		StremioConfigUrlPolicy policy = new StremioConfigUrlPolicy(
				"https://buecher.example.invalid/setup", false);

		assertEquals(NAVIGATE, policy.evaluate("https://BUECHER.EXAMPLE.INVALID./next"));
	}
}
