package me.aap.fermata.ui.voice;

/** Typed result used by recognition cleanup without coupling it to command implementation. */
public final class VoiceCommandOutcome {
	public enum Status { UNHANDLED, COMPLETED, ASYNC_PENDING, AWAITING_SELECTION }
	public enum PlaybackEffect { PRESERVE, KEEP_PAUSED, REPLACE_PENDING }

	private static final VoiceCommandOutcome UNHANDLED =
			new VoiceCommandOutcome(Status.UNHANDLED, PlaybackEffect.PRESERVE);
	private final Status status;
	private final PlaybackEffect playbackEffect;

	private VoiceCommandOutcome(Status status, PlaybackEffect playbackEffect) {
		this.status = status;
		this.playbackEffect = playbackEffect;
	}

	public static VoiceCommandOutcome unhandled() {
		return UNHANDLED;
	}

	public static VoiceCommandOutcome completed(PlaybackEffect effect) {
		return new VoiceCommandOutcome(Status.COMPLETED, effect);
	}

	public static VoiceCommandOutcome pending(PlaybackEffect effect) {
		return new VoiceCommandOutcome(Status.ASYNC_PENDING, effect);
	}

	public static VoiceCommandOutcome awaitingSelection() {
		return new VoiceCommandOutcome(Status.AWAITING_SELECTION,
				PlaybackEffect.REPLACE_PENDING);
	}

	public Status getStatus() {
		return status;
	}

	public PlaybackEffect getPlaybackEffect() {
		return playbackEffect;
	}

	public boolean isHandled() {
		return status != Status.UNHANDLED;
	}

	public boolean shouldKeepPaused() {
		return playbackEffect == PlaybackEffect.KEEP_PAUSED;
	}
}
