package me.aap.fermata.media.net;

import java.util.Map;

/** Resolves short-lived playback headers without storing their values in media items. */
@FunctionalInterface
public interface PlaybackHeaderResolver {
	Map<String, String> resolve(PlaybackRequestProfile.HeaderReference reference)
			throws PlaybackRequestValidationException;
}
