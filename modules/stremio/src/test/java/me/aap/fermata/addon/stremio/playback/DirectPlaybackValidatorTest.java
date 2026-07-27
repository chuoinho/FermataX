package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.net.URI;

import org.junit.Test;

import me.aap.fermata.media.net.PlaybackRequestProfile;

public class DirectPlaybackValidatorTest {
	@Test
	public void acceptsFreshHttpHlsAndDashWithMatchingProfiles() throws Exception {
		for (PlaybackDescriptor.TargetKind kind : new PlaybackDescriptor.TargetKind[]{
				PlaybackDescriptor.TargetKind.DIRECT_HTTP,
				PlaybackDescriptor.TargetKind.HLS,
				PlaybackDescriptor.TargetKind.DASH}) {
			PlaybackDescriptor descriptor = descriptor(kind, "https://media.example/video.m3u8",
					"https://media.example/video.m3u8", 2_000L);
			assertSame(descriptor, DirectPlaybackValidator.validate(descriptor, 1_000L));
		}
	}

	@Test
	public void rejectsExpiredMismatchedAndNonHttpTargets() {
		assertThrows(PlaybackDescriptor.ExpiredPlaybackDescriptorException.class,
				() -> DirectPlaybackValidator.validate(descriptor(
						PlaybackDescriptor.TargetKind.DIRECT_HTTP,
						"https://media.example/video.mp4",
						"https://media.example/video.mp4", 999L), 1_000L));
		assertThrows(IllegalStateException.class,
				() -> DirectPlaybackValidator.validate(descriptor(
						PlaybackDescriptor.TargetKind.DIRECT_HTTP,
						"https://media.example/video.mp4",
						"https://other.example/video.mp4", 2_000L), 1_000L));
		assertThrows(IllegalStateException.class,
					() -> DirectPlaybackValidator.validate(descriptor(
							PlaybackDescriptor.TargetKind.DIRECT_HTTP,
							"ftp://media.example/video.mp4",
							null, 2_000L), 1_000L));
	}

	private static PlaybackDescriptor descriptor(PlaybackDescriptor.TargetKind kind,
			String target, String profileTarget, long expiresAt) {
		StremioPlaybackIdentity identity = StremioPlaybackIdentity.scoped(
				"source", "movie", "movie", "movie");
		StreamProvider provider = new StreamProvider(
				"source", "addon.test", "Provider", 0, true);
		PlaybackRequestProfile profile = (profileTarget == null) ? null :
				PlaybackRequestProfile.builder(URI.create(profileTarget), "descriptor")
						.expiresAt(expiresAt).build();
		return new PlaybackDescriptor("descriptor", "selection", identity,
				"source", "Provider", null, null,
				new StremioPlaybackMetadata("Movie", null, 0L), kind, target, profile,
				null, null, 0L, expiresAt, provider);
	}
}
