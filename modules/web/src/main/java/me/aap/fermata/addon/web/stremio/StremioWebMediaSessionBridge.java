package me.aap.fermata.addon.web.stremio;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumSet;
import java.util.Set;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/** Origin-scoped compatibility and control bridge; it never exposes stream or DOM access. */
final class StremioWebMediaSessionBridge implements MediaSessionCallback.ControlOnlyDelegate {
	private static final String ORIGIN = "https://web.stremio.com";
	private static final String PORT = "fermataStremioControl";
	private static final int VERSION = 1;
	private static final int MAX_MESSAGE_SIZE = 4096;
	private static final int MAX_TEXT_SIZE = 256;
	private static final Set<String> ORIGINS = Set.of(ORIGIN);
	private final StremioWebView web;
	private final State state = new State();
	private ScriptHandler script;
	private MediaSessionCallback claimedCallback;
	private boolean installed;

	StremioWebMediaSessionBridge(StremioWebView web) {
		this.web = web;
	}

	void install() {
		if (!supportsBridge(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT),
				WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))) {
			Log.w("Stremio media-session bridge disabled: required WebView feature unavailable");
			return;
		}
		try {
			script = WebViewCompat.addDocumentStartJavaScript(web, shimSource(), ORIGINS);
			WebViewCompat.addWebMessageListener(web, PORT, ORIGINS, this::onMessage);
			installed = true;
		} catch (RuntimeException error) {
			if (script != null) script.remove();
			script = null;
			Log.w(error, "Stremio media-session bridge disabled");
		}
	}

	void onDocumentNavigation() {
		state.reset();
		releaseClaim();
	}

	void onFragmentActiveChanged(boolean active) {
		if (active) syncClaim();
		else releaseClaim();
	}

	@Override
	public boolean isControlOnlyActive() {
		return installed && isStremioActive() && state.canClaim();
	}

	@Override
	public boolean dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction action) {
		String command = switch (action) {
			case PLAY -> "play";
			case PAUSE -> "pause";
			case NEXT_TRACK -> "nexttrack";
		};
		if (!isControlOnlyActive() || !state.canDispatch(command)) return false;
		web.evaluateJavascript(dispatchSource(command), null);
		return true;
	}

	void close() {
		installed = false;
		state.reset();
		releaseClaim();
		if (script != null) script.remove();
		script = null;
		try {
			WebViewCompat.removeWebMessageListener(web, PORT);
		} catch (RuntimeException ignored) {
		}
	}

	private void onMessage(WebView source, WebMessageCompat message, Uri origin,
			boolean isMainFrame, JavaScriptReplyProxy reply) {
		if ((source != web) || !isMainFrame || !isAllowedOrigin(origin.toString())) return;
		String data = message.getData();
		if (!isBoundedPayload(data)) return;
		try {
			JSONObject json = new JSONObject(data);
			if (json.optInt("v", -1) != VERSION) return;
			String type = json.optString("t", "");
			if (!isSupportedMessageType(type)) return;
			String session = bounded(json.optString("s", ""), 64);
			if (session.isEmpty()) return;
			switch (type) {
				case "READY" -> state.open(session);
				case "PLAYBACK_STATE" -> state.setPlayback(session,
						json.optString("state", "none"));
				case "HANDLER_REGISTERED" -> state.setHandler(session,
						json.optString("action", ""), true);
				case "HANDLER_REMOVED" -> state.setHandler(session,
						json.optString("action", ""), false);
				case "METADATA" -> state.setMetadata(session,
						bounded(json.optString("title", ""), MAX_TEXT_SIZE),
						bounded(json.optString("artist", ""), MAX_TEXT_SIZE));
				case "SESSION_CLOSED" -> state.close(session);
				default -> {
					return;
				}
			}
			syncClaim();
		} catch (JSONException ignored) {
		}
	}

	private void syncClaim() {
		if (!isControlOnlyActive()) {
			releaseClaim();
			return;
		}
		MediaSessionCallback callback = MainActivityDelegate.get(web.getContext())
				.getMediaSessionCallback();
		if ((claimedCallback != null) && (claimedCallback != callback)) releaseClaim();
		if (callback.claimControlOnly(this, state.playbackState(), state.actions(), state.metadata()))
			claimedCallback = callback;
	}

	private void releaseClaim() {
		MediaSessionCallback callback = claimedCallback;
		claimedCallback = null;
		if (callback != null) callback.releaseControlOnly(this);
	}

	private boolean isStremioActive() {
		try {
			return (web.getParent() != null) &&
					(MainActivityDelegate.get(web.getContext()).getActiveFragment()
							instanceof StremioWebFragment);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static String bounded(String value, int maxLength) {
		if (value == null) return "";
		return value.substring(0, Math.min(value.length(), maxLength));
	}

	static boolean supportsBridge(boolean documentStart, boolean webMessageListener) {
		return documentStart && webMessageListener;
	}

	static boolean isAllowedOrigin(String origin) {
		return ORIGIN.equals(origin);
	}

	static boolean isBoundedPayload(String payload) {
		return (payload != null) && (payload.length() <= MAX_MESSAGE_SIZE);
	}

	static boolean isSupportedMessageType(String type) {
		return switch (type) {
			case "READY", "PLAYBACK_STATE", "METADATA", "HANDLER_REGISTERED",
					"HANDLER_REMOVED", "SESSION_CLOSED" -> true;
			default -> false;
		};
	}

	static String shimSource() {
		return """
			(function(){
			  'use strict';
			  if (window.top !== window || window.__fermataStremioMediaSessionV1) return;
			  var port = window.fermataStremioControl;
			  if (!port || typeof port.postMessage !== 'function') return;
			  var session = Math.random().toString(36).slice(2) + Date.now().toString(36);
			  var allowed = Object.freeze(Object.assign(Object.create(null), {play:true,pause:true,nexttrack:true}));
			  var handlers = Object.create(null);
			  var send = function(type, data) { try {
			    var msg = data || {}; msg.v = 1; msg.t = type; msg.s = session;
			    port.postMessage(JSON.stringify(msg));
			  } catch (_) {} };
			  var text = function(value) { return typeof value === 'string' ? value.slice(0, 256) : ''; };
			  var expose = function() { window.__fermataStremioMediaSessionV1 = Object.freeze({version:1, dispatch:function(action) {
			    var callback = handlers[action]; if (typeof callback !== 'function') return false;
			    try { callback(); } catch (_) {} return true;
			  }}); };
			  var descriptor = function(object, name) {
			    while (object) { var value = Object.getOwnPropertyDescriptor(object, name); if (value) return value;
			      object = Object.getPrototypeOf(object); }
			    return null;
			  };
			  var observeNative = function(mediaSession) {
			    var state = descriptor(mediaSession, 'playbackState');
			    var metadata = descriptor(mediaSession, 'metadata');
			    var setHandler = mediaSession.setActionHandler;
			    if (!state || typeof state.get !== 'function' || typeof state.set !== 'function' ||
			        !metadata || typeof metadata.get !== 'function' || typeof metadata.set !== 'function' ||
			        typeof setHandler !== 'function') return false;
			    try {
			      Object.defineProperty(mediaSession, 'playbackState', {configurable:true, enumerable:state.enumerable,
			        get:function(){ return state.get.call(mediaSession); }, set:function(value) {
			          state.set.call(mediaSession, value); send('PLAYBACK_STATE', {state:value}); }});
			      Object.defineProperty(mediaSession, 'metadata', {configurable:true, enumerable:metadata.enumerable,
			        get:function(){ return metadata.get.call(mediaSession); }, set:function(value) {
			          metadata.set.call(mediaSession, value);
			          send('METADATA', {title:text(value && value.title), artist:text(value && value.artist)}); }});
			      Object.defineProperty(mediaSession, 'setActionHandler', {configurable:true, writable:true,
			        value:function(action, callback) { setHandler.call(mediaSession, action, callback);
			          if (!allowed[action]) return;
			          if (callback == null) { delete handlers[action]; send('HANDLER_REMOVED', {action:action}); }
			          else if (typeof callback === 'function') { handlers[action] = callback;
			            send('HANDLER_REGISTERED', {action:action}); } }});
			      return true;
			    } catch (_) { return false; }
			  };
			  var nativeSession = navigator.mediaSession;
			  if (nativeSession) { if (!observeNative(nativeSession)) return; expose(); }
			  else {
			    if (!window.MediaMetadata) window.MediaMetadata = function(init) {
			      init = init || {}; this.title = text(init.title); this.artist = text(init.artist);
			      this.album = text(init.album); this.artwork = Array.isArray(init.artwork) ? init.artwork : [];
			    };
			    var mediaSession = {
			      _state: 'none', _metadata: null,
			      get playbackState() { return this._state; },
			      set playbackState(value) {
			        value = value === 'playing' || value === 'paused' ? value : 'none';
			        this._state = value; send('PLAYBACK_STATE', {state:value});
			      },
			      get metadata() { return this._metadata; },
			      set metadata(value) {
			        this._metadata = value || null;
			        send('METADATA', {title:text(value && value.title), artist:text(value && value.artist)});
			      },
			      setActionHandler: function(action, callback) {
			        if (!allowed[action]) return;
			        if (callback == null) { delete handlers[action]; send('HANDLER_REMOVED', {action:action}); return; }
			        if (typeof callback !== 'function') return;
			        handlers[action] = callback; send('HANDLER_REGISTERED', {action:action});
			      }
			    };
			    try { Object.defineProperty(navigator, 'mediaSession', {value:mediaSession, configurable:true}); }
			    catch (_) { try { navigator.mediaSession = mediaSession; } catch (_) { return; } }
			    expose();
			  }
			  addEventListener('pagehide', function(){ send('SESSION_CLOSED'); }, {once:true});
			  send('READY');
			})();
			""";
	}

	private static String dispatchSource(String action) {
		return "(function(){var b=window.__fermataStremioMediaSessionV1;" +
				"return !!(b&&b.version===1&&b.dispatch('" + action + "'));})()";
	}

	static final class State {
		private String session;
		private String title;
		private String artist;
		private Playback playback = Playback.NONE;
		private final EnumSet<Action> handlers = EnumSet.noneOf(Action.class);

		void open(String session) {
			if (!session.equals(this.session)) {
				this.session = session;
				title = artist = null;
				playback = Playback.NONE;
				handlers.clear();
			}
		}

		void close(String session) {
			if (session.equals(this.session)) reset();
		}

		void reset() {
			session = title = artist = null;
			playback = Playback.NONE;
			handlers.clear();
		}

		void setPlayback(String session, String value) {
			if (!session.equals(this.session)) return;
			playback = switch (value) {
				case "playing" -> Playback.PLAYING;
				case "paused" -> Playback.PAUSED;
				default -> Playback.NONE;
			};
		}

		void setHandler(String session, String action, boolean registered) {
			if (!session.equals(this.session)) return;
			Action value = Action.from(action);
			if (value == null) return;
			if (registered) handlers.add(value); else handlers.remove(value);
		}

		void setMetadata(String session, String title, String artist) {
			if (!session.equals(this.session)) return;
			this.title = title;
			this.artist = artist;
		}

		boolean canClaim() {
			return (playback != Playback.NONE) &&
					(handlers.contains(Action.PLAY) || handlers.contains(Action.PAUSE));
		}

		boolean canDispatch(String action) {
			return handlers.contains(Action.from(action));
		}

		int playbackState() {
			return (playback == Playback.PLAYING) ? STATE_PLAYING :
					(playback == Playback.PAUSED) ? STATE_PAUSED : STATE_NONE;
		}

		long actions() {
			return handlers.contains(Action.NEXT_TRACK) ? ACTION_SKIP_TO_NEXT : 0L;
		}

		@Nullable MediaMetadataCompat metadata() {
			if (((title == null) || title.isEmpty()) && ((artist == null) || artist.isEmpty())) return null;
			MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder();
			if ((title != null) && !title.isEmpty()) metadata.putString(METADATA_KEY_DISPLAY_TITLE, title);
			if ((artist != null) && !artist.isEmpty()) {
				metadata.putString(METADATA_KEY_DISPLAY_SUBTITLE, artist);
				metadata.putString(METADATA_KEY_ARTIST, artist);
			}
			return metadata.build();
		}
	}

	private enum Playback { NONE, PAUSED, PLAYING }

	private enum Action {
		PLAY("play"), PAUSE("pause"), NEXT_TRACK("nexttrack");
		private final String value;
		Action(String value) { this.value = value; }
		@Nullable static Action from(String value) {
			for (Action action : values()) if (action.value.equals(value)) return action;
			return null;
		}
	}
}
