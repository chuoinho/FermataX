package me.aap.fermata.addon.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FermataWebClientTest {
	@Test
	public void rendererRecoveryUsesTheNewestNonScriptNavigationUrl() {
		assertEquals("https://current.example", FermataWebView.selectRecoveryUrl(
				"https://current.example", "https://last.example", "https://addon.example"));
		assertEquals("https://last.example", FermataWebView.selectRecoveryUrl(
				"javascript:play()", "https://last.example", "https://addon.example"));
		assertEquals("https://addon.example", FermataWebView.selectRecoveryUrl(
				null, "JAVASCRIPT:retry()", "https://addon.example"));
		assertEquals(null, FermataWebView.selectRecoveryUrl(
				"javascript:a()", "javascript:b()", "javascript:c()"));
	}

	@Test
	public void transientNetworkFailuresAreEligibleForAutomaticRetry() {
		assertTrue(FermataWebClient.isTransientLoadError("ERR_CONNECTION_TIMED_OUT"));
		assertTrue(FermataWebClient.isTransientLoadError("Host lookup failed"));
		assertTrue(FermataWebClient.isTransientLoadError("Name not resolved"));
		assertTrue(FermataWebClient.isTransientLoadError("Connection reset"));
		assertFalse(FermataWebClient.isTransientLoadError("HTTP 404 Not Found"));
		assertFalse(FermataWebClient.isTransientLoadError(null));
	}

	@Test
	public void automaticRetryBudgetAndDelaysMatchCurrentBehavior() {
		assertTrue(FermataWebClient.canAutoRetry(0));
		assertTrue(FermataWebClient.canAutoRetry(1));
		assertFalse(FermataWebClient.canAutoRetry(2));
		assertEquals(1200L, FermataWebClient.getAutoRetryDelay(1));
		assertEquals(3000L, FermataWebClient.getAutoRetryDelay(2));
	}

	@Test
	public void staleRetryCannotReplaceNewerNavigation() {
		assertTrue(FermataWebClient.shouldRunRetry(4, 4,
				"https://failed.example", "https://failed.example", "https://failed.example"));
		assertTrue(FermataWebClient.shouldRunRetry(4, 4,
				"https://failed.example", null, "https://failed.example"));
		assertFalse(FermataWebClient.shouldRunRetry(4, 5,
				"https://failed.example", "https://new.example", "https://failed.example"));
		assertFalse(FermataWebClient.shouldRunRetry(4, 4,
				"https://failed.example", "https://new.example", "https://other.example"));
	}
}
