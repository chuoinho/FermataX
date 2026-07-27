package me.aap.fermata.addon.stremio.playback;

import java.net.URI;

import me.aap.fermata.media.net.PlaybackRequestProfile;

/** Deterministic validation before a direct descriptor reaches a decoder. */
public final class DirectPlaybackValidator {
	private DirectPlaybackValidator() {
	}

	public static PlaybackDescriptor validate(PlaybackDescriptor descriptor, long nowEpochMillis)
			throws PlaybackDescriptor.ExpiredPlaybackDescriptorException {
		if (StremioStreamEligibilityPolicy.classify(descriptor) !=
				StremioStreamEligibilityPolicy.Kind.DIRECT) return descriptor;
		descriptor.requireFresh(nowEpochMillis);
		URI target;
		try {
			target = URI.create(descriptor.targetValue());
		} catch (IllegalArgumentException error) {
			throw new IllegalStateException("Invalid direct playback target", error);
		}
		String scheme = target.getScheme();
		if ((scheme == null) ||
				(!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) ||
				target.getHost() == null) {
			throw new IllegalStateException("Direct playback target must be HTTP(S)");
		}
		PlaybackRequestProfile profile = descriptor.requestProfile();
		if ((profile == null) || !target.equals(profile.getTargetUri())) {
			throw new IllegalStateException("Direct playback profile does not match its target");
		}
		return descriptor;
	}
}
