package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.web.audio.WebAudioProfile;

public class StremioWebAudioBridgeTest {
	@Test
	public void isExactOriginAndDocumentStartOnly() {
		assertTrue(StremioWebAudioBridge.isAllowedOrigin("https://web.stremio.com"));
		assertFalse(StremioWebAudioBridge.isAllowedOrigin("https://foo.web.stremio.com"));
		assertFalse(StremioWebAudioBridge.isAllowedOrigin("http://web.stremio.com"));
		assertTrue(StremioWebAudioBridge.isHostedDocument("https://web.stremio.com/#/discover"));
		assertTrue(StremioWebAudioBridge.isHostedDocument("https://web.stremio.com:443/#/discover"));
		assertFalse(StremioWebAudioBridge.isHostedDocument("https://web.stremio.com.evil/#/discover"));
		assertFalse(StremioWebAudioBridge.isHostedDocument("https://web.stremio.com:8443/#/discover"));
		assertFalse(StremioWebAudioBridge.isHostedDocument("http://web.stremio.com/#/discover"));
		assertTrue(StremioWebAudioBridge.supportsBridge(true));
		assertFalse(StremioWebAudioBridge.supportsBridge(false));
	}

	@Test
	public void scriptHasSingleAttachAndExplicitLifecycleGuards() {
		String source = StremioWebAudioBridge.shimSource(37L, WebAudioProfile.unity());
		assertTrue(source.contains("window.top !== window"));
		assertTrue(source.contains("new WeakMap()"));
		assertTrue(source.contains("new WeakSet()"));
		assertTrue(source.contains("Q = Math.SQRT2"));
		assertTrue(source.contains("event.type === 'encrypted'"));
		assertTrue(source.contains("new MutationObserver(schedule)"));
		assertTrue(source.contains("var retainsOwner = function(owner)"));
		assertFalse(source.contains("return playerRoute() && media.isConnected"));
		assertTrue(source.contains("source === owner.sourceKey"));
		assertTrue(source.contains("!media.ended && !media.error"));
		assertTrue(source.contains("owner.terminal"));
		assertTrue(source.contains("owner.context.close()"));
		assertTrue(source.contains("owner.context.state !== 'suspended'"));
		assertFalse(source.contains("var eligibleOwner = function(owner)"));
		assertTrue(source.contains("if (!(profile.m && profile.e)) return;"));
		assertTrue(source.contains("addEventListener('hashchange', schedule)"));
		assertTrue(source.contains("lowshelf"));
		assertTrue(source.contains("highshelf"));
		assertTrue(source.contains("createDynamicsCompressor()"));
		assertTrue(source.contains("dinhDb"));
		assertEquals(1, occurrences(source, "createMediaElementSource("));
	}

	@Test
	public void profileUpdatesAreGenerationBoundAndInPlace() {
		String source = StremioWebAudioBridge.shimSource(9L, WebAudioProfile.unity());
		assertTrue(source.contains("updateProfile"));
		assertTrue(source.contains("profile = next"));
		assertTrue(source.contains("apply(active)"));
		assertTrue(StremioWebAudioBridge.updateProfileSource(9L,
				WebAudioProfile.unity()).contains("updateProfile(9,"));
		assertTrue(StremioWebAudioBridge.teardownSource(9L).contains("teardown(9)"));
	}

	private static int occurrences(String value, String needle) {
		int count = 0, index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
