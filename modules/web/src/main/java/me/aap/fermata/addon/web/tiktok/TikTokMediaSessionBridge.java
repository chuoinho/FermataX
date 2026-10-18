package me.aap.fermata.addon.web.tiktok;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityListener;

/**
 * Binds Android MediaSessionCompat to TikTok Web player DOM.
 * Translates steering wheel / headunit next/previous buttons to window.__cntNext() / __cntPrev(),
 * play/pause to active video playback, and reports metadata to Android Auto SmartTop Card.
 */
final class TikTokMediaSessionBridge implements MediaSessionCallback.ControlOnlyDelegate,
		TikTokJsBridge.Listener {

	@Nullable
	private final TikTokWebView web;
	private final State state = new State();
	private MediaSessionCallback claimedCallback;
	private boolean installed;
	private long documentGeneration = 1L;

	TikTokMediaSessionBridge(@Nullable TikTokWebView web) {
		this.web = web;
	}

	void install() {
		installed = true;
	}

	void close() {
		installed = false;
		documentGeneration++;
		state.reset();
		releaseClaim();
	}

	void onDocumentNavigation(String url) {
		documentGeneration++;
		state.reset();
		releaseClaim();
		if (!TikTokWebSessionPolicy.isHostedRoute(url)) {
			installed = false;
		}
	}

	@Override
	public void onPlayingChanged(boolean playing) {
		runOnMain(() -> {
			state.setPlaying(playing);
			syncClaim();
			notifyContentChanged();
		});
	}

	@Override
	public void onNowPlayingChanged(String id, String name, String art, long positionMs,
			long durationMs, boolean playing) {
		runOnMain(() -> {
			state.setMetadata(id, name, "TikTok");
			state.setPlaying(playing);
			syncClaim();
			notifyContentChanged();
		});
	}

	@Override
	public boolean isControlOnlyActive() {
		return installed && ((web == null) || web.isAttachedToWindow()) && state.canClaim();
	}

	@Override
	public String controlOnlyAddonClass() {
		return TikTokAddon.class.getName();
	}

	@Override
	public String controlOnlyContentKey() {
		return documentGeneration + ":" + state.contentId();
	}

	@Override
	public long controlOnlyActions() {
		return state.controlOnlyActions();
	}

	@Override
	public void onControlOnlyRevoked() {
		if (web != null) {
			web.post(() -> web.evaluateJavascript(getActionScript(MediaSessionCallback.ControlOnlyAction.PAUSE), null));
		}
		state.reset();
		releaseClaim();
	}

	@Override
	public boolean dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction action) {
		if (web == null) return false;
		String script = getActionScript(action);
		if (script == null) return false;
		web.post(() -> web.evaluateJavascript(script, null));
		return true;
	}

	public void dispatchNext() {
		dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction.NEXT_TRACK);
	}

	public void dispatchPrev() {
		dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction.PREVIOUS_TRACK);
	}

	public void dispatchPlayPause() {
		if (state.isPlaying()) {
			dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction.PAUSE);
		} else {
			dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction.PLAY);
		}
	}

	public void requestTitleUpdate() {
		if (web == null) return;
		web.evaluateJavascript("(function(){ return typeof window.__cntTitle === 'function' ? window.__cntTitle() : ''; })()",
				value -> {
					if (value != null && !value.equals("null") && !value.isEmpty()) {
						String clean = value.replaceAll("^\"|\"$", "").trim();
						if (!clean.isEmpty()) {
							state.setMetadata("", clean, "TikTok");
							syncClaim();
						}
					}
				});
	}

	@NonNull
	static String getActionScript(MediaSessionCallback.ControlOnlyAction action) {
		return switch (action) {
			case NEXT_TRACK -> "(function(){ if (typeof window.__cntNext === 'function') window.__cntNext(); })()";
			case PREVIOUS_TRACK -> "(function(){ if (typeof window.__cntPrev === 'function') window.__cntPrev(); })()";
			case PLAY -> "(function(){ var v = (typeof window.__cntVideo === 'function' ? window.__cntVideo() : document.querySelector('video')); if (v) v.play(); })()";
			case PAUSE -> "(function(){ var v = (typeof window.__cntVideo === 'function' ? window.__cntVideo() : document.querySelector('video')); if (v) v.pause(); })()";
		};
	}

	private void syncClaim() {
		if (!isControlOnlyActive() || web == null) {
			releaseClaim();
			return;
		}
		Context ctx = web.getContext();
		if (ctx == null) return;
		try {
			MainActivityDelegate activity = MainActivityDelegate.get(ctx);
			if (activity == null) return;
			MediaSessionCallback callback = activity.getMediaSessionCallback();
			if (callback == null) return;
			if (state.isPlaying()) {
				if (callback.isPlaying() || callback.getEngine() != null) {
					callback.onStop();
				}
			}
			if ((claimedCallback != null) && (claimedCallback != callback)) {
				releaseClaim();
			}
			if (callback.claimControlOnly(this, state.playbackState(), state.actions(), state.metadata())) {
				claimedCallback = callback;
			}
		} catch (Throwable t) {
			Log.w(t, "TikTok media-session syncClaim failed");
		}
	}

	private void releaseClaim() {
		MediaSessionCallback callback = claimedCallback;
		claimedCallback = null;
		if (callback != null) {
			try {
				callback.releaseControlOnly(this);
			} catch (Throwable ignored) {
			}
		}
	}

	private void notifyContentChanged() {
		if (web == null) return;
		try {
			MainActivityDelegate.get(web.getContext()).fireBroadcastEvent(
					ActivityListener.FRAGMENT_CONTENT_CHANGED);
		} catch (Throwable ignored) {
		}
	}

	private void runOnMain(Runnable runnable) {
		if (web != null) {
			web.post(runnable);
		} else {
			runnable.run();
		}
	}

	State getState() {
		return state;
	}

	static final class State {
		private String id = "";
		private String title = "";
		private String artist = "TikTok";
		private int playbackState = STATE_NONE;

		boolean isPlaybackActive() {
			return playbackState != STATE_NONE;
		}

		boolean isPlaying() {
			return playbackState == STATE_PLAYING;
		}

		boolean isPaused() {
			return playbackState == STATE_PAUSED;
		}

		boolean canClaim() {
			return playbackState != STATE_NONE;
		}

		int playbackState() {
			return playbackState;
		}

		String contentId() {
			return (id != null && !id.isEmpty()) ? id : title;
		}

		String getTitle() {
			return (title != null && !title.trim().isEmpty()) ? title.trim() : "TikTok Video";
		}

		String getArtist() {
			return (artist != null && !artist.trim().isEmpty()) ? artist.trim() : "TikTok";
		}

		String getId() {
			return id;
		}

		long actions() {
			long a = ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS;
			if (isPlaying()) {
				a |= ACTION_PAUSE;
			} else if (isPaused()) {
				a |= ACTION_PLAY;
			}
			return a;
		}

		long controlOnlyActions() {
			return ACTION_PLAY | ACTION_PAUSE | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS;
		}

		@NonNull
		MediaMetadataCompat metadata() {
			String t = (title != null && !title.trim().isEmpty()) ? title.trim() : "TikTok Video";
			String a = (artist != null && !artist.trim().isEmpty()) ? artist.trim() : "TikTok";
			return new MediaMetadataCompat.Builder()
					.putString(METADATA_KEY_TITLE, t)
					.putString(METADATA_KEY_DISPLAY_TITLE, t)
					.putString(METADATA_KEY_ARTIST, a)
					.putString(METADATA_KEY_DISPLAY_SUBTITLE, a)
					.putString(METADATA_KEY_ALBUM, "For You")
					.build();
		}

		void setPlaying(boolean playing) {
			playbackState = playing ? STATE_PLAYING : STATE_PAUSED;
		}

		void setMetadata(String id, String title, String artist) {
			this.id = (id != null) ? id : "";
			this.title = (title != null) ? title : "";
			if (artist != null && !artist.trim().isEmpty()) {
				this.artist = artist.trim();
			}
		}

		void reset() {
			playbackState = STATE_NONE;
			id = "";
			title = "";
		}
	}
}
