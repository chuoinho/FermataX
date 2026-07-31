package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeFullscreenGate.NO_REQUEST;

import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebClient.DiagnosticsObserver;
import me.aap.fermata.addon.web.FermataWebClient.DiagnosticsSnapshot;
import me.aap.fermata.addon.web.FermataWebClient.FullscreenEvent;

/** Serializes browser fullscreen callbacks and the app video presentation. */
final class YoutubeFullscreenCoordinator {
	private static final long BROWSER_ENTRY_TIMEOUT_MS = 1200L;
	private final Host host;
	private final YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
	private final DiagnosticsObserver diagnosticsObserver;
	private State state = State.IDLE;
	private long activeRequest = NO_REQUEST;
	private long generation;
	private boolean appPresentationActive;

	YoutubeFullscreenCoordinator(Host host) {
		this(host, FermataWebClient.diagnosticsObserver());
	}

	YoutubeFullscreenCoordinator(Host host, DiagnosticsObserver diagnosticsObserver) {
		this.host = host;
		this.diagnosticsObserver = (diagnosticsObserver == null) ?
				FermataWebClient.diagnosticsObserver() : diagnosticsObserver;
	}

	State getState() {
		return state;
	}

	void requestAutoEntry(String pageUrl, String mediaUrl) {
		// A host interruption (for example an OEM reversing-camera overlay) may leave the
		// page running and emitting playback callbacks. Do not let those callbacks replace
		// the suspension transaction while the projected UI is unavailable.
		if (state == State.HOST_SUSPENDED) {
			emit(FullscreenEvent.REQUEST_REJECTED, state, NO_REQUEST, false);
			return;
		}
		long request = gate.requestAutoEntry(pageUrl, mediaUrl);
		if (request == NO_REQUEST) {
			emit(FullscreenEvent.REQUEST_REJECTED, state, request, false);
			return;
		}
		if (appPresentationActive) {
			emit(FullscreenEvent.REQUEST_REJECTED, state, request, false);
			return;
		}

		state = State.ENTRY_REQUESTED;
		activeRequest = request;
		long generation = ++this.generation;
		emit(FullscreenEvent.REQUEST_CREATED, state, request, true);
		host.post(() -> beginBrowserEntry(generation, request));
	}

	boolean acceptBrowserEntry(long request) {
		if (appPresentationActive || (state == State.APP_FULLSCREEN)) {
			emit(FullscreenEvent.REQUEST_REJECTED, state, request, false);
			return false;
		}
		if ((request != NO_REQUEST) &&
				(((state != State.ENTRY_REQUESTED) && (state != State.ENTRY_DEFERRED)) ||
						(request != activeRequest))) {
			emit(FullscreenEvent.REQUEST_REJECTED, state, request, false);
			return false;
		}
		if (!host.canEnterFullscreen() || !gate.acceptsBrowserEntry(request)) {
			emit(FullscreenEvent.REQUEST_REJECTED, state, request, false);
			return false;
		}

		state = State.BROWSER_ACCEPTED;
		activeRequest = request;
		generation++;
		emit(FullscreenEvent.REQUEST_ACCEPTED, state, request, true);
		return true;
	}

	void onBrowserVisibilityChanged(boolean fullScreen) {
		emit(FullscreenEvent.BROWSER_VISIBILITY_CHANGED, state, activeRequest, true,
				fullScreen);
		if (fullScreen) {
			if (state != State.BROWSER_ACCEPTED) return;
			state = State.FULLSCREEN;
			generation++;
			appPresentationActive = true;
			emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true);
			host.enterBrowserVideoMode();
			return;
		}

		State previous = state;
		if (previous == State.HOST_SUSPENDED) {
			leaveOwnedPresentation();
			return;
		}
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
				emit(FullscreenEvent.FALLBACK_ENTERED, state, activeRequest, true);
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
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true);
		host.cancelPendingBrowserFullscreen();
		if (!host.isBrowserFullscreen()) leaveOwnedPresentation();
	}

	boolean onPlayerBack(boolean ownsPlayback, boolean appVideoMode,
			boolean browserFullScreen) {
		if (!gate.onUserBack(ownsPlayback, appVideoMode, browserFullScreen)) return false;

		state = State.USER_EXITED;
		activeRequest = NO_REQUEST;
		generation++;
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true);
		host.cancelPendingBrowserFullscreen();
		// Auto-next can replace the playback identity while the Activity remains in video mode.
		// Re-adopt that presentation so Back can always release the real UI state.
		if (appVideoMode) appPresentationActive = true;
		if (browserFullScreen) host.exitBrowserFullscreen();
		else leaveOwnedPresentation();
		return true;
	}

	void cancelPlayback() {
		gate.cancelCurrentPlayback();
		state = State.CANCELLED;
		activeRequest = NO_REQUEST;
		generation++;
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true);
		host.cancelPendingBrowserFullscreen();
		if (host.isBrowserFullscreen()) host.exitBrowserFullscreen();
		else leaveOwnedPresentation();
	}

	/**
	 * Releases only the projected fullscreen surface while retaining the user's fullscreen
	 * intent. Unlike {@link #onUserExit()}, this does not arm the USER_EXIT gate.
	 */
	Suspension suspendForHostInterruption() {
		boolean browserFullscreen = host.isBrowserFullscreen();
		boolean appFullscreen = host.isAppVideoMode();
		boolean pendingEntry = (state == State.ENTRY_REQUESTED) ||
				(state == State.ENTRY_DEFERRED) || (state == State.BROWSER_ACCEPTED);
		if (!appPresentationActive && !browserFullscreen && !appFullscreen && !pendingEntry)
			return null;

		state = State.HOST_SUSPENDED;
		activeRequest = NO_REQUEST;
		long tokenGeneration = ++generation;
		appPresentationActive = appPresentationActive || browserFullscreen || appFullscreen;
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true, browserFullscreen);
		host.cancelPendingBrowserFullscreen();
		if (browserFullscreen) host.exitBrowserFullscreen();
		leaveOwnedPresentation();
		return new Suspension(tokenGeneration);
	}

	/** Restores a still-current host suspension using the gesture-free app presentation. */
	boolean resumeAfterHostInterruption(Suspension suspension) {
		if (!isCurrent(suspension) || !host.canEnterFullscreen()) return false;
		state = State.APP_FULLSCREEN;
		activeRequest = NO_REQUEST;
		generation++;
		appPresentationActive = true;
		emit(FullscreenEvent.FALLBACK_ENTERED, state, activeRequest, true);
		host.enterFallbackVideoMode();
		return true;
	}

	/** Converts an interrupted presentation into the existing deliberate-exit behavior. */
	void discardHostInterruption(Suspension suspension) {
		if (!isCurrent(suspension)) return;
		gate.onUserExit();
		state = State.USER_EXITED;
		activeRequest = NO_REQUEST;
		generation++;
		appPresentationActive = false;
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true);
		host.cancelPendingBrowserFullscreen();
	}

	boolean isCurrent(Suspension suspension) {
		return (suspension != null) && (state == State.HOST_SUSPENDED) &&
				(suspension.generation == generation);
	}

	boolean enterManualAppFullscreen() {
		if (!host.canEnterFullscreen()) return false;
		gate.consumeManualAppEntry();
		state = State.APP_FULLSCREEN;
		activeRequest = NO_REQUEST;
		generation++;
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, true);
		host.cancelPendingBrowserFullscreen();
		if (host.isBrowserFullscreen()) host.exitBrowserFullscreen();
		appPresentationActive = true;
		emit(FullscreenEvent.FALLBACK_ENTERED, state, activeRequest, true);
		host.enterFallbackVideoMode();
		return true;
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
			emit(FullscreenEvent.BROWSER_REQUEST_DISPATCHED, state, request, false);
			enterFallbackVideoMode(generation, request);
			return;
		}
		emit(FullscreenEvent.BROWSER_REQUEST_DISPATCHED, state, request, true);
		host.postDelayed(() -> deferRequestedEntry(generation, request),
				BROWSER_ENTRY_TIMEOUT_MS);
	}

	private void deferRequestedEntry(long generation, long request) {
		if (!isCurrent(generation, request)) return;
		state = State.ENTRY_DEFERRED;
		emit(FullscreenEvent.STATE_CHANGED, state, request, true);
		// Stop only the caller's bounded wait. The WebView request remains valid because older
		// Android System WebView builds may deliver onShowCustomView after this deadline.
		host.expirePendingBrowserFullscreenWait();
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
		emit(FullscreenEvent.STATE_CHANGED, state, activeRequest, false);
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
		emit(FullscreenEvent.FALLBACK_ENTERED, state, request, true);
		host.enterFallbackVideoMode();
	}

	private void leaveOwnedPresentation() {
		if (!appPresentationActive) return;
		appPresentationActive = false;
		boolean ownsPresentation = host.ownsPlaybackPresentation();
		emit(FullscreenEvent.PRESENTATION_RELEASED, state, activeRequest, ownsPresentation);
		if (ownsPresentation) host.leaveAppVideoMode();
	}

	private void emit(FullscreenEvent event, State eventState, long request, boolean accepted) {
		emit(event, eventState, request, accepted, host.isBrowserFullscreen());
	}

	private void emit(FullscreenEvent event, State eventState, long request, boolean accepted,
			boolean browserFullscreen) {
		try {
			diagnosticsObserver.onFullscreen(event, DiagnosticsSnapshot.builder()
					.state(eventState).result(true, accepted).request(request)
					.generation(generation).revision(generation)
					.fullscreen(browserFullscreen, appPresentationActive)
					.build());
		} catch (RuntimeException ignored) {
			// Diagnostics must never affect fullscreen or Back behavior.
		}
	}

	private boolean isCurrent(long generation, long request) {
		return (generation == this.generation) &&
				((state == State.ENTRY_REQUESTED) || (state == State.ENTRY_DEFERRED)) &&
				(request == activeRequest);
	}

	enum State {
		IDLE,
		ENTRY_REQUESTED,
		ENTRY_DEFERRED,
		BROWSER_ACCEPTED,
		FULLSCREEN,
		APP_FULLSCREEN,
		HOST_SUSPENDED,
		USER_EXITED,
		CANCELLED
	}

	record Suspension(long generation) {
	}

	interface Host {
		boolean canEnterFullscreen();

		boolean requestBrowserFullscreen(long request);

		void cancelPendingBrowserFullscreen();

		void expirePendingBrowserFullscreenWait();

		boolean isBrowserFullscreen();

		boolean isAppVideoMode();

		void exitBrowserFullscreen();

		boolean ownsPlaybackPresentation();

		void enterBrowserVideoMode();

		void enterFallbackVideoMode();

		void leaveAppVideoMode();

		void post(Runnable task);

		void postDelayed(Runnable task, long delayMillis);
	}
}
