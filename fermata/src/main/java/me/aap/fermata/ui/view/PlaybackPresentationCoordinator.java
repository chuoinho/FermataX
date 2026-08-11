package me.aap.fermata.ui.view;

import me.aap.fermata.ui.policy.PlaybackPresentationReducer;
import me.aap.fermata.ui.policy.PlaybackPresentationReducer.State;
import me.aap.fermata.ui.policy.PlaybackPresentationOwner.Identity;
import me.aap.fermata.ui.policy.PlaybackPresentationOwner.Token;

final class PlaybackPresentationCoordinator {
	private final Host host;
	private State state = PlaybackPresentationReducer.leaveVideo(false);
	private Token owner;
	private long ownerGeneration;
	private long transitionGeneration;

	PlaybackPresentationCoordinator(Host host) {
		this.host = host;
	}

	State getState() {
		return state;
	}

	Token enterVideo(Identity identity, boolean splitMode) {
		return enterVideo(identity, splitMode, true);
	}

	Token enterVideo(Identity identity, boolean splitMode, boolean playing) {
		Token token = claim(identity);
		transition(PlaybackPresentationReducer.enterVideo(splitMode, playing), 0);
		return token;
	}

	void leaveVideo(boolean showAudioPlayerBar) {
		clearOwner();
		transition(PlaybackPresentationReducer.leaveVideo(showAudioPlayerBar), 0);
	}

	boolean leaveVideo(Token token, boolean showAudioPlayerBar) {
		if (!isCurrent(token)) return false;
		clearOwner();
		transition(PlaybackPresentationReducer.leaveVideo(showAudioPlayerBar), 0);
		return true;
	}

	Token getOwner() {
		return owner;
	}

	boolean isCurrent(Token token) {
		return (token != null) && token.equals(owner);
	}

	void toggleControls(int delay) {
		toggleControls(delay, true);
	}

	void toggleControls(int delay, boolean playing) {
		transition(PlaybackPresentationReducer.toggleControls(state, delay, playing), delay);
	}

	void showSeekControls(int delay) {
		showSeekControls(delay, true);
	}

	void showSeekControls(int delay, boolean playing) {
		transition(PlaybackPresentationReducer.showSeekControls(state, delay, playing), delay);
	}

	void showControls(int delay) {
		showControls(delay, true);
	}

	void showControls(int delay, boolean playing) {
		transition(PlaybackPresentationReducer.showControls(state, delay, playing), delay);
	}

	void showControlsPersistent() {
		transition(PlaybackPresentationReducer.showControlsPersistent(state), 0);
	}

	void refreshTimeout(int delay) {
		if (!state.timeoutPending()) return;
		scheduleTimeout(delay);
	}

	void cancel() {
		clearOwner();
		transitionGeneration++;
	}

	void playingChanged(boolean playing, int delay) {
		transition(PlaybackPresentationReducer.playingChanged(state, playing, delay), delay);
	}

	private void transition(State next, int delay) {
		transitionGeneration++;
		state = next;
		host.apply(next);
		if (next.timeoutPending()) scheduleTimeout(delay);
	}

	private void scheduleTimeout(int delay) {
		long generation = ++this.transitionGeneration;
		host.postDelayed(() -> {
			if (generation != this.transitionGeneration) return;
			state = PlaybackPresentationReducer.timeout(state);
			host.apply(state);
		}, Math.max(0, delay));
	}

	private Token claim(Identity identity) {
		if ((owner != null) && owner.identity().equals(identity)) return owner;
		transitionGeneration++;
		return owner = new Token(identity, ++ownerGeneration);
	}

	private void clearOwner() {
		if (owner == null) return;
		owner = null;
		ownerGeneration++;
		transitionGeneration++;
	}

	interface Host {
		void apply(State state);

		void postDelayed(Runnable task, long delay);
	}
}
