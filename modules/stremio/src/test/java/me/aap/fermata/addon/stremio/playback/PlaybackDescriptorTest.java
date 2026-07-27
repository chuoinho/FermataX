package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ExternalStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ProxyHeaders;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.protocol.response.StreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.YoutubeStreamTarget;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.media.net.PlaybackRequestProfile.HeaderReference;

public class PlaybackDescriptorTest {
	private static final long NOW = 1_000_000L;
	private static final StreamProvider PROVIDER =
			new StreamProvider("source-stable", "addon.test", "Fixture Provider", 0, true);
	private static final StremioPlaybackMetadata METADATA = new StremioPlaybackMetadata(
			"Exact title - Episode 2", "https://images.invalid/poster.jpg?token=art-secret", 3_612_345L);
	private static final StreamAggregationRequest REQUEST = new StreamAggregationRequest(
			StremioPlaybackIdentity.canonical("series", "tt1234567", "tt1234567:1:2"),
			"series", "tt1234567", "tt1234567:1:2", METADATA);

	@Test
	public void directDescriptorPreservesMetadataAndKeepsSecretsOutOfIdentityAndText() throws Exception {
		Map<String, String> mutableHeaders = new LinkedHashMap<>();
		mutableHeaders.put("Authorization", "Bearer header-secret");
		mutableHeaders.put("Referer", "https://provider.invalid/tokenized/path");
		AtomicReference<Map<String, String>> registeredHeaders = new AtomicReference<>();
		AtomicReference<String> registeredId = new AtomicReference<>();
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(30_000L,
				(provider, descriptor, headers, expires) -> {
					registeredId.set(descriptor);
					registeredHeaders.set(Map.copyOf(headers));
					assertEquals(NOW + 30_000L, expires);
					return HeaderReference.of("opaque-headers-1");
				});
		String target = "https://cdn.invalid/token-path/master.m3u8?auth=url-secret";
		PlaybackDescriptor descriptor = factory.create(REQUEST, PROVIDER,
				stream("Premium 4K", "Provider label", new DirectStreamTarget(target),
						new ProxyHeaders(mutableHeaders, Map.of())), NOW);
		mutableHeaders.clear();

		assertEquals(PlaybackDescriptor.TargetKind.HLS, descriptor.targetKind());
		assertEquals(target, descriptor.targetValue());
		assertEquals(METADATA, descriptor.metadata());
		assertEquals("Exact title - Episode 2", descriptor.metadata().title());
		assertEquals(3_612_345L, descriptor.metadata().durationMillis());
		assertEquals("Premium 4K", descriptor.streamName());
		assertEquals("Provider label", descriptor.streamTitle());
		assertNotNull(descriptor.requestProfile());
		assertEquals(target, descriptor.requestProfile().getTargetUri().toString());
		assertEquals(HeaderReference.of("opaque-headers-1"),
				descriptor.requestProfile().getHeaderReference());
		assertEquals(registeredId.get(), descriptor.descriptorId());
		assertEquals("Bearer header-secret", registeredHeaders.get().get("Authorization"));

		String diagnosticText = descriptor + " " + descriptor.requestProfile() + " " +
				descriptor.refreshRequest();
		for (String secret : List.of("url-secret", "header-secret", "token-path",
				"tt1234567", "poster.jpg", "Premium 4K", "Provider label")) {
			assertFalse(secret, descriptor.descriptorId().contains(secret));
			assertFalse(secret, descriptor.identity().contentKey().contains(secret));
			assertFalse(secret, descriptor.identity().videoKey().contains(secret));
			assertFalse(secret, diagnosticText.contains(secret));
		}
	}

	@Test
	public void detectsDirectFormatsAndRepresentsExternalTargetsWithoutGuessing() {
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory((a, b, c, d) -> null);
		PlaybackDescriptor http = factory.create(REQUEST, PROVIDER,
				stream(new DirectStreamTarget("https://cdn.invalid/video.mp4")), NOW);
		PlaybackDescriptor dash = factory.create(REQUEST, PROVIDER,
				stream(new DirectStreamTarget("https://cdn.invalid/manifest.MPD?x=1")), NOW);
		PlaybackDescriptor external = factory.create(REQUEST, PROVIDER,
				stream(new ExternalStreamTarget("https://external.invalid/watch/movie")), NOW);
		PlaybackDescriptor infoHash = factory.create(REQUEST, PROVIDER,
				stream(new InfoHashStreamTarget("0123456789abcdef", 2, List.of("tracker-secret"))), NOW);
		PlaybackDescriptor invalidDirect = factory.create(REQUEST, PROVIDER,
				stream(new DirectStreamTarget("ftp://private.invalid/video.mp4")), NOW);

		assertEquals(PlaybackDescriptor.TargetKind.DIRECT_HTTP, http.targetKind());
		assertEquals(PlaybackDescriptor.TargetKind.DASH, dash.targetKind());
		assertThrows(IllegalArgumentException.class, () -> factory.create(REQUEST, PROVIDER,
				stream(new YoutubeStreamTarget("video-id-secret")), NOW));
		assertEquals(PlaybackDescriptor.TargetKind.UNSUPPORTED, external.targetKind());
		assertEquals(PlaybackDescriptor.UnsupportedReason.EXTERNAL_URL_HANDLER_UNAVAILABLE,
				external.unsupportedReason());
		assertEquals(PlaybackDescriptor.TargetKind.TORRENT, infoHash.targetKind());
		assertNull(infoHash.unsupportedReason());
		assertNull(infoHash.targetValue());
		assertEquals(PlaybackDescriptor.UnsupportedReason.UNSUPPORTED_DIRECT_SCHEME,
				invalidDirect.unsupportedReason());
		assertNull(external.requestProfile());
		assertFalse(infoHash.toString().contains("0123456789abcdef"));
		assertFalse(external.toString().contains("external.invalid"));
	}

	@Test
	public void externalTargetsRemainUnavailableUntilWebTransportCanPinEveryRequest()
			throws Exception {
		var resolver = (me.aap.fermata.addon.stremio.net.AddressResolver) host ->
				List.of(InetAddress.getByName(host.equals("private.example") ?
						"127.0.0.1" : "8.8.8.8"));
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(
				(a, b, c, d) -> null, resolver);

		PlaybackDescriptor external = factory.create(REQUEST, PROVIDER,
				stream(new ExternalStreamTarget("https://public.example.invalid/watch/movie")), NOW);
		assertEquals(PlaybackDescriptor.TargetKind.UNSUPPORTED, external.targetKind());
		assertEquals(PlaybackDescriptor.UnsupportedReason.EXTERNAL_URL_HANDLER_UNAVAILABLE,
				external.unsupportedReason());
		assertNull(external.targetValue());
		assertNull(external.endpointValidator());
		for (String target : List.of(
				"https://private.example.invalid/watch/movie",
				"https://user:pass@public.example/watch/movie")) {
			PlaybackDescriptor blocked = factory.create(REQUEST, PROVIDER,
					stream(new ExternalStreamTarget(target)), NOW);
			assertEquals(PlaybackDescriptor.TargetKind.UNSUPPORTED, blocked.targetKind());
			assertNull(blocked.targetValue());
		}
		for (String transientTarget : List.of(
				"https://public.example.invalid/watch/movie?token=secret",
				"https://public.example.invalid/watch/aB12cd")) {
			assertEquals(PlaybackDescriptor.TargetKind.UNSUPPORTED,
					factory.create(REQUEST, PROVIDER,
							stream(new ExternalStreamTarget(transientTarget)), NOW).targetKind());
		}
	}

	@Test
	public void descriptorIsStableShortLivedAndRequiresRefetchAfterExpiry() throws Exception {
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(5_000L,
				(a, b, c, d) -> HeaderReference.of("opaque"));
		StremioStream stream = stream(new DirectStreamTarget("https://cdn.invalid/video.mp4"));
		PlaybackDescriptor first = factory.create(REQUEST, PROVIDER, stream, NOW);
		PlaybackDescriptor second = factory.create(REQUEST, PROVIDER, stream, NOW + 1_000L);

		assertEquals(first.descriptorId(), second.descriptorId());
		assertEquals(first.identity(), second.identity());
		assertFalse(first.isExpired(NOW + 4_999L));
		first.requireFresh(NOW + 4_999L);
		PlaybackDescriptor.ExpiredPlaybackDescriptorException expired = assertThrows(
				PlaybackDescriptor.ExpiredPlaybackDescriptorException.class,
				() -> first.requireFresh(NOW + 5_000L));
		assertEquals(first.identity(), expired.refreshRequest().identity());
		assertEquals(PROVIDER.sourceUuid(), expired.refreshRequest().providerSourceUuid());
		assertEquals(first.selectionFingerprint(),
				expired.refreshRequest().selectionFingerprint());
		assertEquals(first.descriptorId(), expired.refreshRequest().previousDescriptorId());

		PlaybackDescriptor differentTarget = factory.create(REQUEST, PROVIDER,
				stream(new DirectStreamTarget("https://cdn.invalid/other.mp4")), NOW);
		assertNotEquals(first.descriptorId(), differentTarget.descriptorId());
		assertEquals(first.selectionFingerprint(), differentTarget.selectionFingerprint());
		assertThrows(UnsupportedOperationException.class,
				() -> stream.behaviorHints().proxyHeaders().request().put("Cookie", "x"));
	}

	@Test
	public void directPlaybackAppliesProviderNetworkConsentAndRestrictsRedirectOrigin()
			throws Exception {
		AtomicInteger resolutions = new AtomicInteger();
		var privateResolver = (me.aap.fermata.addon.stremio.net.AddressResolver) host -> {
			resolutions.incrementAndGet();
			if (host.equals("127.0.0.1")) return List.of(InetAddress.getLoopbackAddress());
			return List.of(InetAddress.getByName("192.168.1.20"));
		};
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(
				(a, b, c, d) -> null, privateResolver);
		PlaybackDescriptor blocked = factory.create(REQUEST, PROVIDER,
				stream(new DirectStreamTarget("https://media.lan/video.m3u8")), NOW);
		assertEquals(PlaybackDescriptor.TargetKind.UNSUPPORTED, blocked.targetKind());
		assertEquals(PlaybackDescriptor.UnsupportedReason.NETWORK_POLICY_REJECTED,
				blocked.unsupportedReason());

		StreamProvider allowedProvider = new StreamProvider("source-lan", "addon.test",
				"LAN provider", 0, true, new NetworkConsent(false, true));
		PlaybackDescriptor allowed = factory.create(REQUEST, allowedProvider,
				stream(new DirectStreamTarget("https://media.lan/video.m3u8")), NOW);
		factory.create(REQUEST, allowedProvider,
				stream(new DirectStreamTarget("https://media.lan/second.m3u8")), NOW);
		assertEquals(PlaybackDescriptor.TargetKind.HLS, allowed.targetKind());
		assertTrue(allowed.requestProfile().isRedirectAllowed(
				URI.create("https://media.lan/video.m3u8"),
				URI.create("https://media.lan/next.m3u8")));
		assertTrue(allowed.requestProfile().isRedirectAllowed(
				URI.create("https://media.lan/video.m3u8"),
				URI.create("https://127.0.0.1/next.m3u8")));
		assertNotNull(allowed.endpointValidator());
		assertEquals(InetAddress.getByName("192.168.1.20"), allowed.endpointValidator()
				.validate(URI.create("https://media.lan/next.m3u8")).pinnedAddress());
		assertThrows(me.aap.fermata.media.net.PlaybackRequestValidationException.class,
				() -> allowed.endpointValidator().validate(
						URI.create("https://127.0.0.1/next.m3u8")));
		// Strict and LAN-enabled decisions differ; the second LAN target reuses its origin decision.
		assertEquals(4, resolutions.get());
	}

	@Test
	public void directCleartextPlaybackRequiresProviderConsent() throws Exception {
		var publicResolver = (me.aap.fermata.addon.stremio.net.AddressResolver) host ->
				List.of(InetAddress.getByName("8.8.8.8"));
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(
				(a, b, c, d) -> null, publicResolver);
		PlaybackDescriptor blocked = factory.create(REQUEST, PROVIDER,
				stream(new DirectStreamTarget("http://media.example.invalid/video.mp4")), NOW);
		assertEquals(PlaybackDescriptor.UnsupportedReason.NETWORK_POLICY_REJECTED,
				blocked.unsupportedReason());

		StreamProvider allowedProvider = new StreamProvider("source-http", "addon.test",
				"HTTP provider", 0, true, new NetworkConsent(true, false));
		assertEquals(PlaybackDescriptor.TargetKind.DIRECT_HTTP,
				factory.create(REQUEST, allowedProvider,
						stream(new DirectStreamTarget("http://media.example.invalid/video.mp4")), NOW)
						.targetKind());
	}

	private static StremioStream stream(StreamTarget target) {
		return stream(null, null, target, ProxyHeaders.EMPTY);
	}

	private static StremioStream stream(
			String name, String title, StreamTarget target, ProxyHeaders headers) {
		return new StremioStream(name, title, null, target,
				new StreamBehaviorHints(false, null, null, null, headers));
	}
}
