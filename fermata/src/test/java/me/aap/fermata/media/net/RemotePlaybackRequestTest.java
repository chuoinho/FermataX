package me.aap.fermata.media.net;

import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.AUTHORIZATION_HEADER;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.COOKIE_HEADER;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REDIRECT_ORIGIN_POLICY;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REQUEST_HEADERS;
import static me.aap.fermata.media.net.PlaybackRequestProfile.RedirectPolicy.ALLOW_LISTED_ORIGINS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
public class RemotePlaybackRequestTest {
	private static final URI TARGET = URI.create("https://origin.invalid/master.m3u8?token=private");
	private static final URI CDN = URI.create("https://cdn.invalid/segment.ts");

	@Test
	public void resolvesSecretsOnlyForTargetOrigin() throws Exception {
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(TARGET, "private-source")
				.headerReference(PlaybackRequestProfile.HeaderReference.of("opaque"))
				.redirectPolicy(ALLOW_LISTED_ORIGINS).allowOrigin(CDN).build();
		RemotePlaybackRequest request = new RemotePlaybackRequest(TARGET,
				profile, ignored -> Map.of("Authorization", "Bearer private",
						"Cookie", "session=private", "Accept", "video/*"));
		Set<PlaybackRequestProfile.EngineCapability> capabilities = Set.of(
				REDIRECT_ORIGIN_POLICY, REQUEST_HEADERS, AUTHORIZATION_HEADER, COOKIE_HEADER,
				PlaybackRequestProfile.EngineCapability.CROSS_ORIGIN_REQUESTS);

		ResolvedRemotePlaybackRequest resolved = request.resolve(1, capabilities);
		Map<String, String> origin = resolved.headersFor(TARGET);
		Map<String, String> redirected = resolved.headersFor(CDN);

		assertEquals("Bearer private", origin.get("Authorization"));
		assertEquals("session=private", origin.get("Cookie"));
		assertFalse(redirected.containsKey("Authorization"));
		assertFalse(redirected.containsKey("Cookie"));
		assertEquals("video/*", redirected.get("Accept"));
		assertFalse(request.toString().contains("origin.invalid"));
		assertFalse(request.toString().contains("private"));
		assertFalse(resolved.toString().contains("origin.invalid"));
		assertFalse(resolved.toString().contains("private"));
	}

	@Test
	public void rejectsEngineThatCannotHonorResolvedHeaders() {
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(TARGET, "source")
				.headerReference(PlaybackRequestProfile.HeaderReference.of("opaque")).build();
		RemotePlaybackRequest request = new RemotePlaybackRequest(TARGET,
				profile, ignored -> Map.of("Authorization", "Bearer private"));

		assertThrows(UnsupportedPlaybackRequestException.class,
				() -> request.resolveHeaders(1, Set.of(REDIRECT_ORIGIN_POLICY, REQUEST_HEADERS)));
	}

	@Test
	public void carriesShortLivedValidatedEndpointWithoutRenderingAddress() throws Exception {
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(TARGET, "source")
				.requireCapability(PlaybackRequestProfile.EngineCapability.ENDPOINT_VALIDATION)
				.build();
		InetAddress pinned = InetAddress.getByName("192.0.2.10");
		RemotePlaybackRequest request = new RemotePlaybackRequest(TARGET, profile, null,
				uri -> new ValidatedPlaybackEndpoint(uri, pinned));
		ResolvedRemotePlaybackRequest resolved = request.resolve(1, Set.of(
				REDIRECT_ORIGIN_POLICY,
				PlaybackRequestProfile.EngineCapability.ENDPOINT_VALIDATION));

		assertEquals(pinned, resolved.validateEndpoint(TARGET).pinnedAddress());
		assertFalse(resolved.validateEndpoint(TARGET).toString().contains("192.0.2.10"));
	}

	@Test
	public void releasesTransportExactlyOnce() {
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(TARGET, "source").build();
		AtomicInteger releases = new AtomicInteger();
		RemotePlaybackRequest request = new RemotePlaybackRequest(TARGET, profile, null, null,
				releases::incrementAndGet);

		request.close();
		request.close();

		assertEquals(1, releases.get());
	}
}
