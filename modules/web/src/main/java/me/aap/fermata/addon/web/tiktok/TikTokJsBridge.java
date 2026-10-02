package me.aap.fermata.addon.web.tiktok;

import android.webkit.JavascriptInterface;

import androidx.annotation.Nullable;

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

	@Nullable
	private volatile Listener listener;

	public TikTokJsBridge() {
	}

	public TikTokJsBridge(@Nullable Listener listener) {
		this.listener = listener;
	}

	public void setListener(@Nullable Listener listener) {
		this.listener = listener;
	}

	@Nullable
	public Listener getListener() {
		return listener;
	}

	@JavascriptInterface
	public void setPlaying(boolean playing) {
		Listener l = listener;
		if (l != null) {
			l.onPlayingChanged(playing);
		}
	}

	@JavascriptInterface
	public void setNowPlaying(String id, String name, String art, long pos, long dur, boolean playing) {
		Listener l = listener;
		if (l != null) {
			l.onNowPlayingChanged(id, name, art, pos, dur, playing);
		}
	}

	@JavascriptInterface
	public void webLog(String msg) {
		Log.d("TikTokJs: ", msg);
	}
}
