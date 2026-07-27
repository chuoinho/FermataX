package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptorFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackHeaderRegistry;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ProxyHeaders;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.media.net.PlaybackRequestValidationException;

public class StremioDirectPlaybackLeaseTest {
	private static final String SOURCE = "11111111-1111-4111-8111-111111111111";

	@Test
	public void everyManifestSegmentAndRedirectValidationRequiresCurrentProviderLease()
			throws Exception {
		StremioSourceRecord source = source("fingerprint-a", true,
				new NetworkConsent(false, false), 7L);
		AtomicReference<StremioSourceSnapshot> current = new AtomicReference<>(
				new StremioSourceSnapshot(4L, List.of(source), true));
		StremioSourceLease lease = StremioSourceLease.bound(
				4L, source, current::get);
		StreamProvider provider = new StreamProvider(SOURCE, "fixture", "Fixture", 0, true,
				source.networkConsent(), 4L, source.updatedMs(), source.transportFingerprint(), lease);
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(
				new PlaybackHeaderRegistry.HeaderStore(),
				host -> List.of(InetAddress.getByName("8.8.8.8")));
		PlaybackDescriptor descriptor = factory.create(request(), provider,
				stream("https://cdn.example.invalid/master.m3u8"), 1_000L);

		assertEquals(PlaybackDescriptor.TargetKind.HLS, descriptor.targetKind());
		assertNotNull(descriptor.endpointValidator());
		descriptor.endpointValidator().validate(URI.create("https://cdn.example.invalid/master.m3u8"));
		descriptor.endpointValidator().validate(URI.create("https://cdn.example.invalid/video-1.ts"));
		descriptor.endpointValidator().validate(URI.create("https://edge.example.invalid/video-2.ts"));

		current.set(new StremioSourceSnapshot(5L, List.of(source(
				"fingerprint-b", true, new NetworkConsent(false, false), 8L)), true));

		for (String target : List.of(
				"https://cdn.example.invalid/master.m3u8",
				"https://cdn.example.invalid/video-3.ts",
				"https://edge.example.invalid/video-4.ts")) {
			assertThrows(PlaybackRequestValidationException.class,
					() -> descriptor.endpointValidator().validate(URI.create(target)));
		}
		assertEquals(PlaybackDescriptor.TargetKind.UNSUPPORTED,
				factory.create(request(), provider,
						stream("https://cdn.example.invalid/retry.m3u8"), 2_000L).targetKind());
	}

	@Test
	public void disableAndConsentChangeEachRevokeDirectPlaybackLease() throws Exception {
		for (StremioSourceRecord replacement : List.of(
				source("fingerprint-a", false, new NetworkConsent(false, false), 7L),
				source("fingerprint-a", true, new NetworkConsent(true, false), 7L))) {
			StremioSourceRecord source = source("fingerprint-a", true,
					new NetworkConsent(false, false), 7L);
			AtomicReference<StremioSourceSnapshot> current = new AtomicReference<>(
					new StremioSourceSnapshot(4L, List.of(source), true));
			StremioSourceLease lease = StremioSourceLease.bound(4L, source, current::get);
			StreamProvider provider = new StreamProvider(SOURCE, "fixture", "Fixture", 0, true,
					source.networkConsent(), 4L, source.updatedMs(), source.transportFingerprint(), lease);
			PlaybackDescriptor descriptor = new PlaybackDescriptorFactory(
					new PlaybackHeaderRegistry.HeaderStore(),
					host -> List.of(InetAddress.getByName("8.8.8.8")))
					.create(request(), provider, stream("https://cdn.example.invalid/master.m3u8"), 1_000L);

			current.set(new StremioSourceSnapshot(5L, List.of(replacement), true));

			assertThrows(PlaybackRequestValidationException.class,
					() -> descriptor.endpointValidator().validate(
							URI.create("https://cdn.example.invalid/video.ts")));
		}
	}

	private static StreamAggregationRequest request() {
		return new StreamAggregationRequest(
				StremioPlaybackIdentity.canonical("movie", "tt1", "tt1"),
				"movie", "tt1", "tt1", new StremioPlaybackMetadata("Movie", null, 60_000L));
	}

	private static StremioStream stream(String url) {
		return new StremioStream("HD", "Fixture", null, new DirectStreamTarget(url),
				new StreamBehaviorHints(false, null, null, null,
						new ProxyHeaders(Map.of(), Map.of())));
	}

	private static StremioSourceRecord source(String fingerprint, boolean enabled,
			NetworkConsent consent, long updatedMs) {
		return new StremioSourceRecord(SOURCE, fingerprint, "fixture", "Fixture", "1.0",
				"https://provider.example.invalid/manifest.json", "secure:" + SOURCE, enabled, 0,
				"{\"id\":\"fixture\"}", null, null, 0L, 0L, null, 0L, updatedMs,
				consent.allowCleartext(), consent.allowLan());
	}
}
