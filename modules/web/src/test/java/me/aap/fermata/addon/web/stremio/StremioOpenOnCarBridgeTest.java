package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.*;

import org.junit.Test;

public class StremioOpenOnCarBridgeTest {
	@Test
	public void bridgeUsesOnlyTheHostedOriginAndRequiredWebViewFeatures() {
		assertTrue(StremioOpenOnCarBridge.supportsBridge(true, true));
		assertFalse(StremioOpenOnCarBridge.supportsBridge(false, true));
		assertFalse(StremioOpenOnCarBridge.supportsBridge(true, false));
		assertTrue(StremioOpenOnCarBridge.isAllowedOrigin("https://web.stremio.com"));
		assertFalse(StremioOpenOnCarBridge.isAllowedOrigin("https://web.stremio.com:443"));
	}

	@Test
	public void documentBridgeCapturesOnlyAnExplicitHostedPlayerAnchor() {
		String source = StremioOpenOnCarBridge.captureSource(37L);
		assertTrue(source.contains("addEventListener('click', state.capture, true)"));
		assertTrue(source.contains("target.closest('a[href]')"));
		assertTrue(source.contains("url.hash.indexOf('#/player/') !== 0"));
		assertTrue(source.contains("event.preventDefault()"));
		assertTrue(source.contains("event.stopImmediatePropagation()"));
		assertFalse(source.contains("document.querySelectorAll"));
		assertFalse(source.contains("cookie"));
	}

	@Test
	public void documentBridgeCapturesSpaPlayerRouteTransitionsWithoutBroadDomScanning() {
		String source = StremioOpenOnCarBridge.captureSource(37L);
		assertTrue(source.contains("history.pushState"));
		assertTrue(source.contains("history.replaceState"));
		assertTrue(source.contains("addEventListener('hashchange'"));
		assertTrue(source.contains("#/player/"));
		assertFalse(source.contains("MutationObserver"));
	}
}
