package me.aap.fermata.ui.policy;

public final class PlaybackPresentationReducer {
	private PlaybackPresentationReducer() {
	}

	public static State enterVideo(boolean splitMode) {
		return splitMode
				? new State(true, true, true, false, false, false)
				: new State(true, false, false, true, false, false);
	}

	public static State leaveVideo(boolean showAudioPlayerBar) {
		return new State(false, false, showAudioPlayerBar, false, false, false);
	}

	public static State toggleControls(State current, int delay) {
		if (!current.videoMode) return current;
		if (current.splitMode) return enterVideo(true);
		if (current.controlsVisible) {
			return new State(true, false, false, true, false, false);
		}
		return showControls(current, delay, false);
	}

	public static State showSeekControls(State current, int delay) {
		return showControls(current, delay, true);
	}

	public static State showControlsPersistent(State current) {
		if (!current.videoMode) return current;
		return current.splitMode ? enterVideo(true)
				: new State(true, false, true, false, false, false);
	}

	public static State timeout(State current) {
		if (!current.videoMode) return current;
		return current.splitMode ? enterVideo(true)
				: new State(true, false, false, true, false, false);
	}

	private static State showControls(State current, int delay, boolean seekMode) {
		if (!current.videoMode) return current;
		if (current.splitMode) return enterVideo(true);
		if (delay <= 0) return new State(true, false, false, true, false, false);
		return new State(true, false, true, false, true, seekMode);
	}

	public record State(boolean videoMode, boolean splitMode, boolean controlsVisible,
			boolean barsHidden, boolean timeoutPending, boolean seekMode) {
	}
}
