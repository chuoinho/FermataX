package me.aap.fermata.engine.vlc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;

public class VlcEngineProviderTest {
	@Test
	public void libVlcReceivesMutableDefensiveOptionsCopy() {
		List<String> source = List.of("--network-caching=60000");
		List<String> copy = VlcEngineProvider.mutableOptions(source);

		assertNotSame(source, copy);
		copy.add("--no-stats");
		assertEquals(List.of("--network-caching=60000"), source);
	}

	@Test
	public void doesNotClaimHeadersOrRedirectPolicyItCannotEnforce() {
		var capabilities = VlcEngineProvider.playbackCapabilities();
		assertFalse(capabilities.contains(
				me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REQUEST_HEADERS));
		assertFalse(capabilities.contains(
				me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.AUTHORIZATION_HEADER));
		assertFalse(capabilities.contains(
				me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REDIRECT_ORIGIN_POLICY));
	}

	@Test
	public void acceptsOnlyTheInternalStremioP2pBridge() {
		VlcEngineProvider provider = new VlcEngineProvider();
		PlaybackRequestProfile safe = PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1:43210/torrent/token/key"), "p2p")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(EngineCapability.P2P_STREAMING)
				.build();
		var capabilities = provider.capabilitiesFor(safe);

		assertNotNull(capabilities);
		assertTrue(capabilities.contains(EngineCapability.P2P_STREAMING));
		assertTrue(capabilities.contains(EngineCapability.REDIRECT_ORIGIN_POLICY));
		assertNull(provider.capabilitiesFor(PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1:43210/private-file"), "local")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(EngineCapability.P2P_STREAMING)
				.build()));
		assertNull(provider.capabilitiesFor(PlaybackRequestProfile.builder(
				URI.create("https://example.invalid/torrent/token/key"), "remote")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(EngineCapability.P2P_STREAMING)
				.build()));
	}
}
