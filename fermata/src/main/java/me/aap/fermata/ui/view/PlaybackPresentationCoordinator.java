package me.aap.fermata.ui.view;

import me.aap.fermata.ui.policy.PlaybackPresentationReducer;
import me.aap.fermata.ui.policy.PlaybackPresentationReducer.State;

final class PlaybackPresentationCoordinator {
	private final Host host;
	private State state = PlaybackPresentationReducer.leaveVideo(false);
	private long generation;

	PlaybackPresentationCoordinator(Host host) {
		this.host = host;
	}

	State getState() {
		return state;
	}

	void enterVideo(boolean splitMode) {
		transition(PlaybackPresentationReducer.enterVideo(splitMode), 0);
	}

	void leaveVideo(boolean showAudioPlayerBar) {
		transition(PlaybackPresentationReducer.leaveVideo(showAudioPlayerBar), 0);
	}

	void toggleControls(int delay) {
		transition(PlaybackPresentationReducer.toggleControls(state, delay), delay);
	}

	void showSeekControls(int delay) {
		transition(PlaybackPresentationReducer.showSeekControls(state, delay), delay);
	}

	void showControlsPersistent() {
		transition(PlaybackPresentationReducer.showControlsPersistent(state), 0);
	}

	void refreshTimeout(int delay) {
		if (!state.timeoutPending()) return;
		scheduleTimeout(delay);
	}

	void cancel() {
		generation++;
	}

	private void transition(State next, int delay) {
		generation++;
		state = next;
		host.apply(next);
		if (next.timeoutPending()) scheduleTimeout(delay);
	}

	private void scheduleTimeout(int delay) {
		long generation = ++this.generation;
		host.postDelayed(() -> {
			if (generation != this.generation) return;
			state = PlaybackPresentationReducer.timeout(state);
			host.apply(state);
		}, Math.max(0, delay));
	}

	interface Host {
		void apply(State state);

		void postDelayed(Runnable task, long delay);
	}
}
