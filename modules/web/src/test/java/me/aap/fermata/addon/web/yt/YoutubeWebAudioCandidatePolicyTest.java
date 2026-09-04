package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeWebAudioCandidatePolicy.SourceKind.BLOB_MSE;
import static me.aap.fermata.addon.web.yt.YoutubeWebAudioCandidatePolicy.SourceKind.DIRECT_HTTP;
import static me.aap.fermata.addon.web.yt.YoutubeWebAudioCandidatePolicy.SourceKind.DIRECT_HTTPS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeWebAudioCandidatePolicyTest {
	@Test
	public void acceptsOnlyThePhysicallyProvenBlobMsePath() {
		assertEquals(BLOB_MSE, YoutubeWebAudioCandidatePolicy.classifySource("blob:opaque"));
		assertEquals(DIRECT_HTTP, YoutubeWebAudioCandidatePolicy.classifySource("http://media"));
		assertEquals(DIRECT_HTTPS, YoutubeWebAudioCandidatePolicy.classifySource("HTTPS://media"));
		assertTrue(YoutubeWebAudioCandidatePolicy.isEligible(candidate()));
		assertFalse(YoutubeWebAudioCandidatePolicy.isEligible(candidate(DIRECT_HTTPS, false, false)));
		assertFalse(YoutubeWebAudioCandidatePolicy.isEligible(candidate(BLOB_MSE, true, false)));
		assertFalse(YoutubeWebAudioCandidatePolicy.isEligible(candidate(BLOB_MSE, false, true)));
	}

	@Test
	public void sourceClaimRemainsOnePerMediaElementAcrossAClipTransition() {
		assertTrue(YoutubeWebAudioCandidatePolicy.reusesClaimedElement("blob:first", "blob:next"));
		assertFalse(YoutubeWebAudioCandidatePolicy.reusesClaimedElement("blob:first", "https://media"));
	}

	private static YoutubeWebAudioCandidatePolicy.Candidate candidate() {
		return candidate(BLOB_MSE, false, false);
	}

	private static YoutubeWebAudioCandidatePolicy.Candidate candidate(
			YoutubeWebAudioCandidatePolicy.SourceKind source, boolean mediaKeys, boolean encrypted) {
		return new YoutubeWebAudioCandidatePolicy.Candidate(true, true, true, true,
				false, false, 4, mediaKeys, encrypted, source);
	}
}
