package me.aap.fermata.media.net;

import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.AUTHORIZATION_HEADER;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.COOKIE_HEADER;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.CROSS_ORIGIN_REQUESTS;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REQUEST_HEADERS;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REDIRECT_ORIGIN_POLICY;
import static me.aap.fermata.media.net.PlaybackRequestProfile.RedirectPolicy.ALLOW_LISTED_ORIGINS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class PlaybackRequestProfileTest {
	private static final URI TARGET = URI.create("https://media.example.invalid/video/master.m3u8");
	private static final URI CDN = URI.create("https://cdn.example.invalid/video/segment.ts");
	private static final long NOW = 1_000_000L;

	@Test
	public void sameOriginRetainsValidatedHeadersAndCapabilities() throws Exception {
		Map<String, String> source = new LinkedHashMap<>();
		source.put("authorization", "Bearer secret-token");
		source.put("Cookie", "session=secret-cookie");
		source.put("Accept", "video/*\r\nInjected: removed");
		PlaybackRequestProfile profile = profileBuilder().build();
		assertTrue(profile.isRedirectAllowed(TARGET,
				URI.create("https://media.example.invalid/video/redirected.m3u8")));
		assertFalse(profile.isRedirectAllowed(TARGET, CDN));

		PlaybackRequestProfile.ResolvedHeaders resolved = profile.resolveHeaders(
				URI.create("https://media.example.invalid/video/chunk.ts"), NOW,
				reference -> source);
		source.clear();

		assertEquals("Bearer secret-token", resolved.getHeaders().get("Authorization"));
		assertEquals("session=secret-cookie", resolved.getHeaders().get("Cookie"));
		assertEquals("video/*Injected: removed", resolved.getHeaders().get("Accept"));
		assertTrue(resolved.getRequiredEngineCapabilities().containsAll(
				Set.of(REDIRECT_ORIGIN_POLICY, REQUEST_HEADERS,
						AUTHORIZATION_HEADER, COOKIE_HEADER)));
		assertThrows(UnsupportedOperationException.class,
				() -> resolved.getHeaders().put("Accept", "audio/*"));
		assertThrows(UnsupportedOperationException.class,
				() -> profile.getAllowedOrigins().clear());
	}

	@Test
	public void crossOriginRedirectRequiresAllowlistAndStripsSecrets() throws Exception {
		PlaybackRequestProfile profile = profileBuilder()
				.redirectPolicy(ALLOW_LISTED_ORIGINS)
				.allowOrigin(CDN)
				.build();
		assertTrue(profile.isRedirectAllowed(TARGET, CDN));
		assertFalse(profile.isRedirectAllowed(TARGET,
				URI.create("https://unlisted.example.invalid/file.ts")));

		PlaybackRequestProfile.ResolvedHeaders resolved = profile.resolveHeaders(CDN, NOW,
				reference -> Map.of(
						"Authorization", "Bearer must-not-cross",
						"Cookie", "must-not-cross=1",
						"Referer", "https://media.example.invalid/",
						"Origin", "https://media.example.invalid",
						"Accept", "video/*"));

		assertFalse(resolved.getHeaders().containsKey("Authorization"));
		assertFalse(resolved.getHeaders().containsKey("Cookie"));
		assertFalse(resolved.getHeaders().containsKey("Referer"));
		assertFalse(resolved.getHeaders().containsKey("Origin"));
		assertEquals("video/*", resolved.getHeaders().get("Accept"));
		assertTrue(profile.getRequiredEngineCapabilities().contains(CROSS_ORIGIN_REQUESTS));
		assertFalse(resolved.getRequiredEngineCapabilities().contains(AUTHORIZATION_HEADER));
		assertThrows(PlaybackRequestValidationException.class,
				() -> profile.resolveHeaders(
						URI.create("https://unlisted.example.invalid/file.ts"), NOW,
						reference -> Collections.emptyMap()));
	}

	@Test
	public void expiryIsEnforcedAtBoundary() {
		PlaybackRequestProfile profile = profileBuilder().expiresAt(NOW + 10).build();

		assertFalse(profile.isExpired(NOW + 9));
		assertTrue(profile.isExpired(NOW + 10));
		PlaybackRequestValidationException error = assertThrows(
				PlaybackRequestValidationException.class,
				() -> profile.resolveHeaders(TARGET, NOW + 10,
						reference -> Collections.emptyMap()));
		assertFalse(error.getMessage().contains(TARGET.toString()));
	}

	@Test
	public void diagnosticsAndReferenceNeverRenderSensitiveInput() {
		String diagnosticSource = TARGET + "?token=diagnostic-secret";
		PlaybackRequestProfile.HeaderReference reference =
				PlaybackRequestProfile.HeaderReference.of("secure-store:raw-secret-reference");
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(TARGET, diagnosticSource)
				.headerReference(reference)
				.build();

		assertTrue(profile.getDiagnosticIdentity().matches("playback:[0-9a-f]{24}"));
		assertNotEquals(diagnosticSource, profile.getDiagnosticIdentity());
		assertFalse(profile.toString().contains("diagnostic-secret"));
		assertFalse(profile.toString().contains(TARGET.toString()));
		assertFalse(reference.toString().contains(reference.getOpaqueId()));
	}

	@Test
	public void rejectsForbiddenDuplicateOversizedAndUnsupportedHeaders() {
		PlaybackRequestProfile profile = profileBuilder().build();

		for (String name : new String[]{"Host", "Range", "Connection", "Content-Length",
				"Transfer-Encoding", "Keep-Alive"}) {
			assertValidationFails(profile, Map.of(name, "value"));
		}
		assertValidationFails(profile, Map.of("X-Provider-Token", "secret"));
		assertValidationFails(profile, Map.of("A".repeat(65), "value"));
		assertValidationFails(profile, Map.of("Accept", "x".repeat(8 * 1024 + 1)));

		Map<String, String> duplicate = new LinkedHashMap<>();
		duplicate.put("Authorization", "one");
		duplicate.put("authorization", "two");
		assertValidationFails(profile, duplicate);

		Map<String, String> tooMany = new LinkedHashMap<>();
		for (int i = 0; i < 17; i++) tooMany.put("X-Test-" + i, "value");
		PlaybackRequestValidationException countError = assertThrows(
				PlaybackRequestValidationException.class,
				() -> profile.resolveHeaders(TARGET, NOW, reference -> tooMany));
		assertTrue(countError.getMessage().contains("Too many"));
	}

	@Test
	public void engineRequirementsAreImmutableAndCanBeProbed() throws Exception {
		PlaybackRequestProfile.ResolvedHeaders resolved = profileBuilder().build().resolveHeaders(
				TARGET, NOW, reference -> Map.of("Authorization", "Bearer value"));

		assertFalse(resolved.isSupportedBy(Set.of(REDIRECT_ORIGIN_POLICY, REQUEST_HEADERS)));
		assertTrue(resolved.isSupportedBy(Set.of(
				REDIRECT_ORIGIN_POLICY, REQUEST_HEADERS, AUTHORIZATION_HEADER)));
		assertThrows(UnsupportedOperationException.class,
				() -> resolved.getRequiredEngineCapabilities().clear());
	}

	private static PlaybackRequestProfile.Builder profileBuilder() {
		return PlaybackRequestProfile.builder(TARGET, "provider:catalog:item")
				.headerReference(PlaybackRequestProfile.HeaderReference.of("secure-store:headers:1"));
	}

	private static void assertValidationFails(PlaybackRequestProfile profile,
			Map<String, String> headers) {
		assertThrows(PlaybackRequestValidationException.class,
				() -> profile.resolveHeaders(TARGET, NOW, reference -> headers));
	}
}
