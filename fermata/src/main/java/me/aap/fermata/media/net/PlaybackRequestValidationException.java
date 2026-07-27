package me.aap.fermata.media.net;

/** Indicates that a playback request profile or its resolved headers are unsafe to use. */
public class PlaybackRequestValidationException extends Exception {
	public PlaybackRequestValidationException(String message) {
		super(message);
	}

	public PlaybackRequestValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
