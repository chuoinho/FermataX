package me.aap.fermata.media.net;

import java.net.URI;

/**
 * Validates one playback request immediately before the engine opens it.
 * Implementations may also pin the selected network address for the connection.
 */
@FunctionalInterface
public interface PlaybackEndpointValidator {
	ValidatedPlaybackEndpoint validate(URI uri) throws PlaybackRequestValidationException;
}
