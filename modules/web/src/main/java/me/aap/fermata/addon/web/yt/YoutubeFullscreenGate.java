package me.aap.fermata.addon.web.yt;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/** Keeps automatic fullscreen entry scoped to one YouTube playback identity. */
final class YoutubeFullscreenGate {
	static final long NO_REQUEST = -1L;
	private String playbackKey;
	private long generation;
	private EntryState state = EntryState.AVAILABLE;
	private long manualEntryPermit = NO_REQUEST;

	long requestAutoEntry(String pageUrl, String mediaUrl) {
		String key = playbackKey(pageUrl, mediaUrl);
		if (!isYoutubeVideoKey(key)) return NO_REQUEST;
		if (!Objects.equals(playbackKey, key)) {
			// Playback callbacks and WebView history can report another video without a new user
			// selection. Once Back exits fullscreen, only a fresh WebView gesture may start the next
			// fullscreen transaction; a changed URL alone is never sufficient evidence.
			if (state == EntryState.USER_EXIT) {
				if (!isYoutubeVideoKey(key) || (manualEntryPermit == NO_REQUEST)) return NO_REQUEST;
				state = EntryState.AVAILABLE;
			} else if ((state == EntryState.CONSUMED) && !isYoutubeVideoKey(key)) {
				return NO_REQUEST;
			}
			playbackKey = key;
			state = EntryState.AVAILABLE;
			manualEntryPermit = NO_REQUEST;
			generation++;
		}

		if (state != EntryState.AVAILABLE) return NO_REQUEST;
		state = EntryState.CONSUMED;
		return ++generation;
	}

	void onUserExit() {
		state = EntryState.USER_EXIT;
		manualEntryPermit = NO_REQUEST;
		generation++;
	}

	boolean onUserBack(boolean ownsPlayback, boolean appVideoMode, boolean browserFullScreen) {
		// App video mode can become active before WebChromeClient exposes its custom view.
		if (!browserFullScreen && !(ownsPlayback && appVideoMode)) return false;
		onUserExit();
		return true;
	}

	void cancelCurrentPlayback() {
		// Stop/history navigation can race with stale playing events from the page being left.
		// Keep this identity consumed and preserve USER_EXIT across WebView history transitions.
		if (state != EntryState.USER_EXIT) state = EntryState.CONSUMED;
		manualEntryPermit = NO_REQUEST;
		generation++;
	}

	long grantManualBrowserEntry() {
		if (state != EntryState.USER_EXIT) return NO_REQUEST;
		return manualEntryPermit = ++generation;
	}

	void expireManualBrowserEntry(long permit) {
		if (manualEntryPermit == permit) manualEntryPermit = NO_REQUEST;
	}

	boolean accepts(long requestGeneration) {
		return (state != EntryState.USER_EXIT) && (requestGeneration == generation);
	}

	boolean acceptsBrowserEntry(long requestGeneration) {
		if (requestGeneration != NO_REQUEST) return accepts(requestGeneration);
		if (state == EntryState.AVAILABLE) return true;
		if (manualEntryPermit == NO_REQUEST) return false;
		manualEntryPermit = NO_REQUEST;
		state = EntryState.CONSUMED;
		return true;
	}

	private enum EntryState {
		AVAILABLE,
		CONSUMED,
		USER_EXIT
	}

	static String playbackKey(String pageUrl, String mediaUrl) {
		String pageKey = youtubePageKey(pageUrl);
		if (pageKey != null) return pageKey;
		if ((pageUrl != null) && !pageUrl.isBlank()) return "page:" + stripFragment(pageUrl);
		return ((mediaUrl == null) || mediaUrl.isBlank()) ? null : "media:" + mediaUrl;
	}

	private static String stripFragment(String url) {
		int fragment = url.indexOf('#');
		return (fragment < 0) ? url : url.substring(0, fragment);
	}

	static boolean isYoutubeVideoKey(String key) {
		return (key != null) && (key.startsWith("watch:") || key.startsWith("shorts:"));
	}

	private static String youtubePageKey(String pageUrl) {
		if ((pageUrl == null) || pageUrl.isBlank()) return null;

		try {
			URI uri = new URI(pageUrl);
			String path = uri.getPath();
			if (path == null) return null;

			if (path.equals("/watch")) {
				String id = queryValue(uri.getRawQuery(), "v");
				return (id == null) ? null : "watch:" + id;
			}

			if (path.startsWith("/shorts/")) {
				int start = "/shorts/".length();
				int end = path.indexOf('/', start);
				String id = path.substring(start, (end < 0) ? path.length() : end);
				return id.isEmpty() ? null : "shorts:" + id;
			}
		} catch (URISyntaxException ignored) {
		}

		return null;
	}

	private static String queryValue(String query, String name) {
		if ((query == null) || query.isEmpty()) return null;
		for (String part : query.split("&")) {
			int split = part.indexOf('=');
			String key = (split < 0) ? part : part.substring(0, split);
			if (name.equals(key)) return (split < 0) ? "" : part.substring(split + 1);
		}
		return null;
	}
}
