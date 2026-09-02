package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
		String source = StremioWebAudioBridge.shimSource(37L, StremioWebAudioProfile.unity());
		assertTrue(source.contains("window.top !== window"));
		assertTrue(source.contains("new WeakMap()"));
		assertTrue(source.contains("new WeakSet()"));
		assertTrue(source.contains("var Q = Math.SQRT2"));
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
		assertEquals(1, occurrences(source, "createMediaElementSource("));
	}

	@Test
	public void profileUpdatesAreGenerationBoundAndInPlace() {
		String source = StremioWebAudioBridge.shimSource(9L, StremioWebAudioProfile.unity());
		assertTrue(source.contains("updateProfile:function(messageGeneration, value)"));
		assertTrue(source.contains("profile = next; apply(active); schedule(); return true"));
		assertTrue(StremioWebAudioBridge.updateProfileSource(9L,
				StremioWebAudioProfile.unity()).contains("updateProfile(9,"));
		assertTrue(StremioWebAudioBridge.teardownSource(9L).contains("teardown(9)"));
		assertTrue(StremioWebAudioBridge.isCurrentDocumentGeneration(9L, 9L));
		assertFalse(StremioWebAudioBridge.isCurrentDocumentGeneration(10L, 9L));
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
