package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StremioStreamEligibilityPolicyTest {
	@Test
	public void classifiesEveryPlaybackSurfaceFromOnePolicy() {
		assertEquals(StremioStreamEligibilityPolicy.Kind.DIRECT,
				StremioStreamEligibilityPolicy.classify(descriptor(
						PlaybackDescriptor.TargetKind.HLS, "https://example.invalid/video.m3u8")));
		assertEquals(StremioStreamEligibilityPolicy.Kind.UNSUPPORTED,
				StremioStreamEligibilityPolicy.classify(descriptor(
						PlaybackDescriptor.TargetKind.DIRECT_HTTP, null)));
	}

	private static PlaybackDescriptor descriptor(
			PlaybackDescriptor.TargetKind kind, String value) {
		StremioPlaybackIdentity identity = StremioPlaybackIdentity.scoped(
				"source", "movie", "movie", "movie");
		StreamProvider provider = new StreamProvider(
				"source", "addon.test", "Provider", 0, true);
		return new PlaybackDescriptor("descriptor", "selection", identity,
				"source", "Provider", null, null,
				new StremioPlaybackMetadata("Movie", null, 0L), kind, value, null, null, null,
				0L, 1_000L, provider);
	}
}
