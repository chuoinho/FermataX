package me.aap.fermata.addon.web;

import android.webkit.WebViewClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.addon.web.yt.YoutubeWebClient;

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
	public void transientNetworkFailuresPreferStableErrorCodes() {
		assertTrue(FermataWebClient.isTransientLoadError(
				WebViewClient.ERROR_HOST_LOOKUP, "localized message"));
		assertTrue(FermataWebClient.isTransientLoadError(503, "Service Unavailable"));
		assertFalse(FermataWebClient.isTransientLoadError(404, "Not Found"));
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

	@Test
	public void subresourceErrorsDoNotLogOrConsumeDiagnosticsQuota() {
		AtomicInteger legacyLogs = new AtomicInteger();
		AtomicInteger diagnostics = new AtomicInteger();

		FermataWebClient.dispatchResourceFailure(false,
				legacyLogs::incrementAndGet, diagnostics::incrementAndGet);

		assertEquals(0, legacyLogs.get());
		assertEquals(0, diagnostics.get());
	}

	@Test
	public void mainFrameErrorsStillLogAndRecordDiagnostics() {
		AtomicInteger legacyLogs = new AtomicInteger();
		AtomicInteger diagnostics = new AtomicInteger();

		FermataWebClient.dispatchResourceFailure(true,
				legacyLogs::incrementAndGet, diagnostics::incrementAndGet);

		assertEquals(1, legacyLogs.get());
		assertEquals(1, diagnostics.get());
	}

	@Test
	public void youtubeHostCheckRejectsLookalikesAndTvSurface() {
		assertTrue(FermataWebClient.isYoutubeHost("youtube.com"));
		assertTrue(FermataWebClient.isYoutubeHost("m.youtube.com"));
		assertTrue(FermataWebClient.isYoutubeHost("YOUTU.BE"));
		assertFalse(FermataWebClient.isYoutubeHost("evilyoutube.com"));
		assertFalse(FermataWebClient.isYoutubeHost("youtube.com.evil.example"));
		assertFalse(FermataWebClient.isYoutubeHost("tv.youtube.com"));
	}

	@Test
	public void policyBoundMainFrameNavigationRejectsDisallowedRedirects() {
		FermataWebClient client = new FermataWebClient();
		client.setExternalNavigationPolicy(uri -> {
			if (!"allowed.example".equals(uri.getHost()))
				throw new me.aap.fermata.addon.external.ExternalNavigationPolicyException("blocked");
		});
		assertTrue(client.isExternalNavigationAllowed("https://allowed.example/start"));
		assertFalse(client.isExternalNavigationAllowed("https://private.example/redirect"));
	}

	@Test
	public void transientExternalPagesAreNeverPersistedAsBrowserLastUrl() {
		assertFalse(FermataWebView.shouldPersistPage(true, false,
				"https://provider.example/private"));
		assertFalse(FermataWebView.shouldPersistPage(false, true, "about:blank"));
		assertTrue(FermataWebView.shouldPersistPage(false, false,
				"https://browser.example/normal"));
	}

	@Test
	public void rendererRecoveryKeepsTheSpecializedWebClientWithoutReflection() {
		FermataWebClient base = new FermataWebClient();
		FermataWebClient youtube = new YoutubeWebClient();
		FermataWebClient unsupported = new FermataWebClient() {};

		assertEquals(FermataWebClient.class, base.createReplacement().getClass());
		assertEquals(YoutubeWebClient.class, youtube.createReplacement().getClass());
		assertEquals(FermataWebClient.class, unsupported.createReplacement().getClass());
	}

	@Test
	public void rendererRecoveryKeepsExternalNavigationPolicy() {
		FermataWebClient client = new YoutubeWebClient();
		client.setExternalNavigationPolicy(uri -> {
			if (!"allowed.example".equals(uri.getHost()))
				throw new me.aap.fermata.addon.external.ExternalNavigationPolicyException("blocked");
		});

		FermataWebClient replacement = client.createReplacement();
		assertTrue(replacement.isExternalNavigationAllowed("https://allowed.example/video"));
		assertFalse(replacement.isExternalNavigationAllowed("https://blocked.example/video"));
	}
}
