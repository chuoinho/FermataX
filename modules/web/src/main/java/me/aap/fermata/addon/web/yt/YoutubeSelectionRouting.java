package me.aap.fermata.addon.web.yt;

import android.os.SystemClock;
import me.aap.fermata.auto.AutomotiveNavigationController;
import me.aap.fermata.auto.OpenOnCarMediaRouting.Selection;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Only explicit selection may leave a WebView; transport keeps its existing session owner. */
final class YoutubeSelectionRouting {
	static String clickScript(int intentEvent) {
		return """
				if (state.intentListener) document.removeEventListener('click', state.intentListener, true);
				state.intentListener = function(e) {
				  var href = '';
				  var path = (typeof e.composedPath === 'function') ? e.composedPath() : [];
				  for (var i = 0; i < path.length; i++) {
				    var node = path[i];
				    if (node && node.tagName === 'A' && node.href) { href = node.href; break; }
				  }
				  if (!href && e.target && e.target.closest) {
				    var anchor = e.target.closest('a[href]');
				    if (anchor) href = anchor.href;
				  }
				  if (!href) return;
				  try {
				    var url = new URL(href, location.href);
				    var host = url.hostname.toLowerCase();
				    var youtube = host === 'youtube.com' || host.endsWith('.youtube.com') ||
				        host === 'youtu.be' || host.endsWith('.youtu.be');
				    var video = (url.pathname === '/watch' && !!url.searchParams.get('v')) ||
				        url.pathname.startsWith('/shorts/');
				    if (youtube && video) {
				      e.preventDefault();
				      e.stopPropagation();
				      e.stopImmediatePropagation();
				      event(%d, url.href);
				    }
				  } catch (err) {}
				};
				document.addEventListener('click', state.intentListener, true);
				""".formatted(intentEvent);
	}
	private final YoutubeWebView web;
	private Selection selection;
	private long deadline;
	private String forwardedVideo = "";

	YoutubeSelectionRouting(YoutubeWebView web) { this.web = web; }

	void capture(long eventTime) {
		selection = MainActivityDelegate.get(web.getContext()).getMediaServiceBinder().captureUserSelection();
		deadline = eventTime + YoutubePlaybackIntentGate.USER_GESTURE_WINDOW_MS;
		forwardedVideo = "";
	}

	boolean isLocal() {
		return selection != null ? !selection.forwardToCar() :
				!AutomotiveNavigationController.get().getOpenOnCarMode().isEnabled();
	}

	boolean explicit(String url) {
		YoutubeItem descriptor;
		try { descriptor = YoutubeItem.fromPageUrl(url, "", System.currentTimeMillis()); }
		catch (IllegalArgumentException ignored) { return false; }
		capture(SystemClock.uptimeMillis());
		if (!selection.forwardToCar()) return false;
		forwardedVideo = descriptor.videoId();
		return web.getAddon().forwardPlaybackToPreferredHost(web, descriptor, selection);
	}

	boolean signal(YoutubePlaybackMetadata.Signal signal, boolean currentEngine, boolean sameVideo,
			YoutubePlaybackIntentGate intent) {
		if (selection == null || !selection.forwardToCar()) return false;
		if (forwardedVideo.equals(signal.videoId())) return true;
		long now = SystemClock.uptimeMillis();
		boolean explicit = now <= deadline && intent.accepts(signal.pageUrl(), now, false);
		if (!YoutubePlaybackHostPolicy.forwardSelection(true, currentEngine, sameVideo, explicit)) return false;
		forwardedVideo = signal.videoId();
		return web.getAddon().forwardPlaybackToPreferredHost(web, signal, selection);
	}
}
