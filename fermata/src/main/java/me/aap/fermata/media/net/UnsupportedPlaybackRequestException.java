package me.aap.fermata.media.net;

/** Signals that an engine cannot safely honor a prepared remote request. */
public final class UnsupportedPlaybackRequestException
		extends PlaybackRequestValidationException {
	public UnsupportedPlaybackRequestException(String message) {
		super(message);
	}
}
