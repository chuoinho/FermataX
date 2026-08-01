package me.aap.fermata.addon.web.yt;

import me.aap.utils.log.Log;

final class YoutubeReloadCoordinator {
	static final long CAPTURE_TIMEOUT_MS = 600L;
	private Runnable captureDeadline;
	private int dispatchedGeneration = -1;

	void capture(YoutubeWebView view, int generation) {
		cancelDeadline(view);
		dispatchedGeneration = -1;
		captureDeadline = () -> dispatch(view, generation, true);
		view.postDelayed(captureDeadline, CAPTURE_TIMEOUT_MS);
		try {
			view.evaluateJavascript("(function(){" + YoutubeScripts.PLAYBACK_SIGNAL +
					"var v=fermataActiveContentVideo();" +
					"if(!v)return '{}';" +
					"return JSON.stringify({id:fermataPageVideoId(),playing:!v.paused&&!v.ended," +
					"audible:!v.muted&&v.volume>0,volume:v.volume});})()", value -> {
				if (!shouldDispatch(generation, view.reloadAudioGeneration(),
						dispatchedGeneration)) return;
				view.captureReloadAudioState(value);
				dispatch(view, generation, false);
			});
		} catch (RuntimeException error) {
			Log.w(error, "Unable to query YouTube before reload");
			dispatch(view, generation, false);
		}
	}

	void cancel(YoutubeWebView view) {
		dispatchedGeneration = -1;
		cancelDeadline(view);
	}

	private void dispatch(YoutubeWebView view, int generation, boolean captureTimedOut) {
		if (!shouldDispatch(generation, view.reloadAudioGeneration(),
				dispatchedGeneration)) return;
		dispatchedGeneration = generation;
		cancelDeadline(view);
		if (captureTimedOut) Log.w("Timed out capturing YouTube state before reload");
		view.reloadPage();
	}

	static boolean shouldDispatch(int requestedGeneration, int currentGeneration,
			int dispatchedGeneration) {
		return (requestedGeneration == currentGeneration) &&
				(requestedGeneration != dispatchedGeneration);
	}

	private void cancelDeadline(YoutubeWebView view) {
		Runnable deadline = captureDeadline;
		if (deadline == null) return;
		captureDeadline = null;
		view.removeCallbacks(deadline);
	}
}
