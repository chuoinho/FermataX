package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeFullscreenGate.NO_REQUEST;

/** Serializes browser fullscreen callbacks and the app video presentation. */
final class YoutubeFullscreenCoordinator {
	private static final long BROWSER_ENTRY_TIMEOUT_MS = 1200L;
	private final Host host;
	private final YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
	private State state = State.IDLE;
	private long activeRequest = NO_REQUEST;
	private long generation;
	private boolean appPresentationActive;

	YoutubeFullscreenCoordinator(Host host) {
		this.host = host;
	}

	State getState() {
		return state;
	}

	void requestAutoEntry(String pageUrl, String mediaUrl) {
		long request = gate.requestAutoEntry(pageUrl, mediaUrl);
		if (request == NO_REQUEST) return;
		if (appPresentationActive) return;

		state = State.ENTRY_REQUESTED;
		activeRequest = request;
		long generation = ++this.generation;
		host.post(() -> beginBrowserEntry(generation, request));
	}

	boolean acceptBrowserEntry(long request) {
		if ((request != NO_REQUEST) &&
				((state != State.ENTRY_REQUESTED) || (request != activeRequest))) return false;
		if (!host.canEnterFullscreen() || !gate.acceptsBrowserEntry(request)) return false;

		state = State.BROWSER_ACCEPTED;
		activeRequest = request;
		generation++;
		return true;
	}

	void onBrowserVisibilityChanged(boolean fullScreen) {
		if (fullScreen) {
			if (state != State.BROWSER_ACCEPTED) return;
			state = State.FULLSCREEN;
			generation++;
			appPresentationActive = true;
			host.enterBrowserVideoMode();
			return;
		}

		State previous = state;
		if (previous == State.APP_FULLSCREEN) return;
		if ((previous == State.IDLE) || (previous == State.CANCELLED)) {
			leaveOwnedPresentation();
			return;
		}

		if (previous != State.USER_EXITED) {
			if (host.canEnterFullscreen()) {
				state = State.APP_FULLSCREEN;
				activeRequest = NO_REQUEST;
				generation++;
				appPresentationActive = true;
				host.enterFallbackVideoMode();
				return;
			}

			gate.cancelCurrentPlayback();
			state = State.CANCELLED;
		} else {
			state = State.USER_EXITED;
		}
		activeRequest = NO_REQUEST;
		generation++;
		leaveOwnedPresentation();
	}

	void onUserExit() {
		gate.onUserExit();
		state = State.USER_EXITED;
		activeRequest = NO_REQUEST;
		generation++;
		host.cancelPendingBrowserFullscreen();
		if (!host.isBrowserFullscreen()) leaveOwnedPresentation();
	}

	boolean onPlayerBack(boolean ownsPlayback, boolean appVideoMode,
			boolean browserFullScreen) {
		if (!gate.onUserBack(ownsPlayback, appVideoMode, browserFullScreen)) return false;

		state = State.USER_EXITED;
		activeRequest = NO_REQUEST;
		generation++;
		host.cancelPendingBrowserFullscreen();
		if (browserFullScreen) host.exitBrowserFullscreen();
		else {
			if (appVideoMode) appPresentationActive = true;
			leaveOwnedPresentation();
		}
		return true;
	}

	void cancelPlayback() {
		gate.cancelCurrentPlayback();
		state = State.CANCELLED;
		activeRequest = NO_REQUEST;
		generation++;
		host.cancelPendingBrowserFullscreen();
		if (host.isBrowserFullscreen()) host.exitBrowserFullscreen();
		else leaveOwnedPresentation();
	}

	long grantManualBrowserEntry() {
		return gate.grantManualBrowserEntry();
	}

	void expireManualBrowserEntry(long permit) {
		gate.expireManualBrowserEntry(permit);
	}

	private void beginBrowserEntry(long generation, long request) {
		if (!isCurrent(generation, request)) return;
		if (!host.canEnterFullscreen()) {
			cancelRequestedEntry(generation, request);
			return;
		}
		if (!host.requestBrowserFullscreen(request)) {
			enterFallbackVideoMode(generation, request);
			return;
		}
		host.postDelayed(() -> cancelRequestedEntry(generation, request),
				BROWSER_ENTRY_TIMEOUT_MS);
	}

	private void cancelRequestedEntry(long generation, long request) {
		if (!isCurrent(generation, request)) return;
		if (host.canEnterFullscreen()) {
			enterFallbackVideoMode(generation, request);
			return;
		}
		gate.cancelCurrentPlayback();
		state = State.CANCELLED;
		activeRequest = NO_REQUEST;
		this.generation++;
		host.cancelPendingBrowserFullscreen();
		leaveOwnedPresentation();
	}

	private void enterFallbackVideoMode(long generation, long request) {
		if (!isCurrent(generation, request)) return;
		state = State.APP_FULLSCREEN;
		activeRequest = NO_REQUEST;
		this.generation++;
		host.cancelPendingBrowserFullscreen();
		appPresentationActive = true;
		host.enterFallbackVideoMode();
	}

	private void leaveOwnedPresentation() {
		if (!appPresentationActive) return;
		appPresentationActive = false;
		if (host.ownsPlaybackPresentation()) host.leaveAppVideoMode();
	}

	private boolean isCurrent(long generation, long request) {
		return (generation == this.generation) && (state == State.ENTRY_REQUESTED) &&
				(request == activeRequest);
	}

	enum State {
		IDLE,
		ENTRY_REQUESTED,
		BROWSER_ACCEPTED,
		FULLSCREEN,
		APP_FULLSCREEN,
		USER_EXITED,
		CANCELLED
	}

	interface Host {
		boolean canEnterFullscreen();

		boolean requestBrowserFullscreen(long request);

		void cancelPendingBrowserFullscreen();

		boolean isBrowserFullscreen();

		void exitBrowserFullscreen();

		boolean ownsPlaybackPresentation();

		void enterBrowserVideoMode();

		void enterFallbackVideoMode();

		void leaveAppVideoMode();

		void post(Runnable task);

		void postDelayed(Runnable task, long delayMillis);
	}
}
