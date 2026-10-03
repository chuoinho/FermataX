package me.aap.fermata.addon.web.tiktok;

import android.webkit.JavascriptInterface;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.aap.utils.log.Log;

/**
 * JavaScript bridge interface exposed to WebView under the name "CntBridge".
 * Receives playback events, metadata, and logging from embedded automotive scripts.
 */
public class TikTokJsBridge {
	public static final String INTERFACE_NAME = "CntBridge";

	public interface Listener {
		void onPlayingChanged(boolean playing);
		void onNowPlayingChanged(String id, String name, String art, long positionMs,
				long durationMs, boolean playing);
	}

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public TikTokJsBridge() {
	}

	public TikTokJsBridge(@Nullable Listener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public void addListener(@Nullable Listener listener) {
		if (listener != null && !listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeListener(@Nullable Listener listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

	public void setListener(@Nullable Listener listener) {
		listeners.clear();
		if (listener != null) {
			listeners.add(listener);
		}
	}

	@Nullable
	public Listener getListener() {
		return listeners.isEmpty() ? null : listeners.get(0);
	}

	@JavascriptInterface
	public void setPlaying(boolean playing) {
		for (Listener l : listeners) {
			l.onPlayingChanged(playing);
		}
	}

	@JavascriptInterface
	public void setNowPlaying(String id, String name, String art, long pos, long dur, boolean playing) {
		for (Listener l : listeners) {
			l.onNowPlayingChanged(id, name, art, pos, dur, playing);
		}
	}

	@JavascriptInterface
	public void webLog(String msg) {
		Log.d("TikTokJs: ", msg);
	}
}
