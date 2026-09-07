package me.aap.fermata.addon.web.stremio;

import static me.aap.fermata.addon.web.stremio.StremioWebAudioCandidatePolicy.SourceKind.BLOB_MSE;
import static me.aap.fermata.addon.web.stremio.StremioWebAudioCandidatePolicy.SourceKind.DIRECT_HTTP;
import static me.aap.fermata.addon.web.stremio.StremioWebAudioCandidatePolicy.SourceKind.DIRECT_HTTPS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class StremioWebAudioCandidatePolicyTest {
	@Test
	public void classifiesOnlyBlobAsTheSupportedSourceClass() {
		assertEquals(BLOB_MSE, StremioWebAudioCandidatePolicy.classifySource("blob:opaque"));
		assertEquals(DIRECT_HTTP, StremioWebAudioCandidatePolicy.classifySource("http://media"));
		assertEquals(DIRECT_HTTPS, StremioWebAudioCandidatePolicy.classifySource("HTTPS://media"));
	}

	@Test
	public void requiresEveryProvenSafeEligibilityGate() {
		assertTrue(StremioWebAudioCandidatePolicy.isEligible(candidate()));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(false, true, true, true,
				false, false, 4, false, false, BLOB_MSE, 1)));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(true, true, true, true,
				false, false, 4, false, false, DIRECT_HTTP, 1)));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(true, true, true, true,
				false, false, 4, false, false, DIRECT_HTTPS, 1)));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(true, true, true, true,
				false, false, 4, true, false, BLOB_MSE, 1)));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(true, true, true, true,
				false, false, 4, false, true, BLOB_MSE, 1)));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(true, false, true, true,
				false, false, 4, false, false, BLOB_MSE, 1)));
		assertFalse(StremioWebAudioCandidatePolicy.isEligible(with(true, true, true, false,
				true, false, 4, false, false, BLOB_MSE, 1)));
	}

	@Test
	public void selectsOneClearActiveCandidateAndFailsClosedOnAmbiguity() {
		var lower = with(true, true, true, true, false, false, 4, false, false, BLOB_MSE, 10);
		var higher = with(true, true, true, true, false, false, 4, false, false, BLOB_MSE, 11);
		assertSame(higher, StremioWebAudioCandidatePolicy.select(List.of(lower, higher)));
		assertNull(StremioWebAudioCandidatePolicy.select(List.of(lower,
				with(true, true, true, true, false, false, 4, false, false, BLOB_MSE, 10))));
	}

	@Test
	public void detectsMaterialSourceReplacement() {
		assertFalse(StremioWebAudioCandidatePolicy.sourceChanged("blob:first", "blob:first"));
		assertTrue(StremioWebAudioCandidatePolicy.sourceChanged("blob:first", "blob:next"));
	}

	@Test
	public void retainsAnAttachedGraphAcrossPauseAndShortStallOnly() {
		var paused = with(true, true, true, false, true, false, 4, false, false, BLOB_MSE, 1);
		var stalled = with(true, true, true, false, false, false, 0, false, false, BLOB_MSE, 1);
		assertTrue(StremioWebAudioCandidatePolicy.retainsAttachedOwner(paused, true, false));
		assertTrue(StremioWebAudioCandidatePolicy.retainsAttachedOwner(stalled, true, false));
		assertFalse(StremioWebAudioCandidatePolicy.retainsAttachedOwner(
				with(true, true, true, true, false, true, 4, false, false, BLOB_MSE, 1),
				true, false));
		assertFalse(StremioWebAudioCandidatePolicy.retainsAttachedOwner(candidate(), false, false));
		assertFalse(StremioWebAudioCandidatePolicy.retainsAttachedOwner(candidate(), true, true));
		assertTrue(StremioWebAudioCandidatePolicy.retainsAttachedOwner(candidate(), true, false));
		assertFalse(StremioWebAudioCandidatePolicy.retainsAttachedOwner(
				with(true, true, true, true, false, false, 4, false, false, DIRECT_HTTPS, 1),
				true, false));
	}

	private static StremioWebAudioCandidatePolicy.Candidate candidate() {
		return with(true, true, true, true, false, false, 4, false, false, BLOB_MSE, 1);
	}

	private static StremioWebAudioCandidatePolicy.Candidate with(boolean mainDocument,
			boolean connected, boolean visible, boolean advancing, boolean paused, boolean ended,
			int readyState, boolean mediaKeys, boolean encrypted,
			StremioWebAudioCandidatePolicy.SourceKind source, long rank) {
		return new StremioWebAudioCandidatePolicy.Candidate(mainDocument, connected, visible,
				advancing, paused, ended, readyState, mediaKeys, encrypted, source, rank);
	}
}
