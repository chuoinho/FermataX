package me.aap.fermata.addon.web.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebAudioCandidatePolicyTest {
	@Test
	public void classifiesBlobAndDataAsSafe() {
		assertEquals(WebAudioCandidatePolicy.SourceSafety.SAFE_BLOB,
				WebAudioCandidatePolicy.evaluateSource("blob:https://example.com/uuid-123", "https://example.com", null));
		assertTrue(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.SAFE_BLOB));

		assertEquals(WebAudioCandidatePolicy.SourceSafety.SAFE_DATA,
				WebAudioCandidatePolicy.evaluateSource("data:audio/mp3;base64,AAAA", "https://example.com", null));
		assertTrue(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.SAFE_DATA));
	}

	@Test
	public void classifiesSameOriginAsSafe() {
		assertEquals(WebAudioCandidatePolicy.SourceSafety.SAFE_SAME_ORIGIN,
				WebAudioCandidatePolicy.evaluateSource("https://example.com/media/audio.mp3", "https://example.com", null));
		assertTrue(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.SAFE_SAME_ORIGIN));
	}

	@Test
	public void classifiesExplicitCorsAsSafe() {
		assertEquals(WebAudioCandidatePolicy.SourceSafety.SAFE_CORS_EXPLICIT,
				WebAudioCandidatePolicy.evaluateSource("https://cdn.other.com/media/audio.mp3", "https://example.com", "anonymous"));
		assertTrue(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.SAFE_CORS_EXPLICIT));

		assertEquals(WebAudioCandidatePolicy.SourceSafety.SAFE_CORS_EXPLICIT,
				WebAudioCandidatePolicy.evaluateSource("https://cdn.other.com/media/audio.mp3", "https://example.com", "use-credentials"));
		assertTrue(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.SAFE_CORS_EXPLICIT));
	}

	@Test
	public void classifiesOpaqueCrossOriginAsUnsafeBypass() {
		assertEquals(WebAudioCandidatePolicy.SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE,
				WebAudioCandidatePolicy.evaluateSource("https://cdn.other.com/media/audio.mp3", "https://example.com", null));
		assertFalse(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE));

		assertEquals(WebAudioCandidatePolicy.SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE,
				WebAudioCandidatePolicy.evaluateSource("file:///android_asset/sample.mp3", "https://example.com", null));
		assertFalse(WebAudioCandidatePolicy.isSafeToAttach(WebAudioCandidatePolicy.SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE));

		assertEquals(WebAudioCandidatePolicy.SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE,
				WebAudioCandidatePolicy.evaluateSource(null, "https://example.com", null));
		assertEquals(WebAudioCandidatePolicy.SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE,
				WebAudioCandidatePolicy.evaluateSource("", "https://example.com", null));
	}
}
