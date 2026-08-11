package me.aap.fermata.ui.policy;

public final class PlaybackPresentationReducer {
	private PlaybackPresentationReducer() {
	}

	public static State enterVideo(boolean splitMode) {
		return enterVideo(splitMode, true);
	}

	public static State enterVideo(boolean splitMode, boolean playing) {
		return splitMode
				? new State(true, true, true, false, false, false)
				: playing ? new State(true, false, false, true, false, false) :
				new State(true, false, true, false, false, false);
	}

	public static State leaveVideo(boolean showAudioPlayerBar) {
		return new State(false, false, showAudioPlayerBar, false, false, false);
	}

	public static State toggleControls(State current, int delay) {
		return toggleControls(current, delay, true);
	}

	public static State toggleControls(State current, int delay, boolean playing) {
		if (!current.videoMode) return current;
		if (current.splitMode) return enterVideo(true);
		if (!playing) return showControlsPersistent(current);
		if (current.controlsVisible) {
			return new State(true, false, false, true, false, false);
		}
		return showControls(current, delay, false, true);
	}

	public static State showSeekControls(State current, int delay) {
		return showSeekControls(current, delay, true);
	}

	public static State showSeekControls(State current, int delay, boolean playing) {
		return showControls(current, delay, true, playing);
	}

	public static State showControls(State current, int delay) {
		return showControls(current, delay, true);
	}

	public static State showControls(State current, int delay, boolean playing) {
		return showControls(current, delay, false, playing);
	}

	public static State showControlsPersistent(State current) {
		if (!current.videoMode) return current;
		return current.splitMode ? enterVideo(true)
				: new State(true, false, true, false, false, false);
	}

	public static State timeout(State current) {
		if (!current.videoMode || !current.timeoutPending) return current;
		return current.splitMode ? enterVideo(true)
				: new State(true, false, false, true, false, false);
	}

	public static State playingChanged(State current, boolean playing, int delay) {
		if (!current.videoMode || current.splitMode) return current;
		if (!playing) return showControlsPersistent(current);
		return current.controlsVisible ? showControls(current, delay, current.seekMode, true) : current;
	}

	private static State showControls(State current, int delay, boolean seekMode,
			boolean playing) {
		if (!current.videoMode) return current;
		if (current.splitMode) return enterVideo(true);
		if (!playing) return new State(true, false, true, false, false, seekMode);
		if (delay <= 0) return new State(true, false, false, true, false, false);
		return new State(true, false, true, false, true, seekMode);
	}

	public record State(boolean videoMode, boolean splitMode, boolean controlsVisible,
			boolean barsHidden, boolean timeoutPending, boolean seekMode) {
	}
}
