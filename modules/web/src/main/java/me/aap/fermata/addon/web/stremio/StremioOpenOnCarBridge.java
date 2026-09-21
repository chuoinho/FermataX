package me.aap.fermata.addon.web.stremio;

import android.net.Uri;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;


/** Origin-scoped capture of a user click on an opaque Stremio Player route. */
final class StremioOpenOnCarBridge {
	private static final String ORIGIN = "https://web.stremio.com";
	private static final String PORT = "fermataStremioOpenOnCar";
	private static final int VERSION = 1;
	private static final int MAX_MESSAGE_SIZE = (64 * 1024) + 256;
	private static final Set<String> ORIGINS = Set.of(ORIGIN);

	private final StremioWebView web;
	@Nullable
	private Listener listener;
	@Nullable
	private ScriptHandler script;
	private boolean installed;
	private boolean captureEnabled;
	private boolean acceptingMessages;
	private long documentGeneration;

	StremioOpenOnCarBridge(StremioWebView web) {
		this.web = web;
	}

	void install() {
		if (!supportsBridge(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT),
				WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))) return;
		try {
			WebViewCompat.addWebMessageListener(web, PORT, ORIGINS, this::onMessage);
			installed = true;
		} catch (RuntimeException ignored) {
			removeDocumentScript();
		}
	}

	void setListener(@Nullable Listener listener) {
		this.listener = listener;
	}

	void setCaptureEnabled(boolean enabled) {
		if (!installed || (captureEnabled == enabled)) return;
		captureEnabled = enabled;
		if (!enabled) {
			acceptingMessages = false;
			documentGeneration++;
			try {
				web.evaluateJavascript(disableSource(), null);
			} catch (RuntimeException ignored) {
			}
			removeDocumentScript();
			return;
		}
		onDocumentNavigation(web.getUrl());
	}

	/** Prepares the document-start bridge before a hosted document is loaded. */
	void onDocumentNavigation(String url) {
		acceptingMessages = false;
		documentGeneration++;
		try {
			web.evaluateJavascript(disableSource(), null);
		} catch (RuntimeException ignored) {
		}
		removeDocumentScript();
		if (!installed || !captureEnabled || !StremioWebSessionPolicy.isHostedRoute(url)) return;
		try {
			script = WebViewCompat.addDocumentStartJavaScript(web,
					captureSource(documentGeneration), ORIGINS);
			acceptingMessages = true;
			// Also activate an already-loaded SPA document when the switch changes state.
			web.evaluateJavascript(captureSource(documentGeneration), null);
		} catch (RuntimeException ignored) {
			removeDocumentScript();
			acceptingMessages = false;
		}
	}

	void close() {
		captureEnabled = false;
		acceptingMessages = false;
		listener = null;
		documentGeneration++;
		removeDocumentScript();
		try {
			WebViewCompat.removeWebMessageListener(web, PORT);
		} catch (RuntimeException ignored) {
		}
		installed = false;
	}

	private void onMessage(WebView source, WebMessageCompat message, Uri origin,
			boolean isMainFrame, JavaScriptReplyProxy reply) {
		if (!captureEnabled || !acceptingMessages || (source != web) || !isMainFrame ||
				!isAllowedOrigin(origin.toString())) return;
		String data = message.getData();
		if (!isBoundedPayload(data)) return;
		try {
			JSONObject json = new JSONObject(data);
			if ((json.optInt("v", -1) != VERSION) ||
					(json.optLong("g", -1L) != documentGeneration) ||
					!"PLAYER_SELECTED".equals(json.optString("t", ""))) return;
			String route = json.optString("route", "");
			if (!StremioOpenOnCarPolicy.acceptsPlayerRoute(route)) return;
			Listener current = listener;
			if (current != null) current.onPlayerRouteSelected(route);
		} catch (JSONException ignored) {
		}
	}

	private void removeDocumentScript() {
		ScriptHandler current = script;
		script = null;
		if (current != null) current.remove();
	}

	static boolean supportsBridge(boolean documentStart, boolean webMessageListener) {
		return documentStart && webMessageListener;
	}

	static boolean isAllowedOrigin(String origin) {
		return ORIGIN.equals(origin);
	}

	private static boolean isBoundedPayload(String payload) {
		return (payload != null) && (payload.length() <= MAX_MESSAGE_SIZE);
	}

	static String captureSource(long generation) {
		return String.format(Locale.ROOT, """
			(function(){
			  'use strict';
			  if (window.top !== window) return;
			  var state = window.__fermataStremioOpenOnCarV1;
			  if (state && state.version === 1 && typeof state.setEnabled === 'function') {
			    state.setEnabled(true, %d); return;
			  }
			  var port = window.fermataStremioOpenOnCar;
			  if (!port || typeof port.postMessage !== 'function') return;
			state = {version:1, enabled:true, generation:%d, capture:null, lastSafeUrl:location.href,
				send:null};
			state.setEnabled = function(enabled, nextGeneration) {
			  state.enabled = !!enabled;
			  if (typeof nextGeneration === 'number') {
			    state.generation = nextGeneration;
			    state.lastRoute = null;
			    state.lastSafeUrl = window.location.href;
			  }
			};
			var asPlayerUrl = function(value) {
			  var url;
			  try { url = new URL(value, window.location.href); } catch (_) { return null; }
			  if (url.protocol !== 'https:' || url.hostname !== 'web.stremio.com' ||
			      (url.port !== '' && url.port !== '443') || url.username || url.password ||
			      url.hash.indexOf('#/player/') !== 0) return null;
			  return url;
			};
			state.send = function(url) {
			  if (!state.enabled || !url || state.lastRoute === url.href) return false;
			  state.lastRoute = url.href;
			  try { port.postMessage(JSON.stringify({v:1,g:state.generation,t:'PLAYER_SELECTED',route:url.href})); }
			  catch (_) {}
			  return true;
			};
			var observeTransition = function(value) {
			  var url = asPlayerUrl(value);
			  if (url) { state.send(url); return true; }
			  if (state.enabled) state.lastSafeUrl = String(value || window.location.href);
			  return false;
			};
			var pushState = history.pushState, replaceState = history.replaceState;
			history.pushState = function(nextState, title, url) {
			  if (observeTransition(url)) return;
			  return pushState.apply(this, arguments);
			};
			history.replaceState = function(nextState, title, url) {
			  if (observeTransition(url)) return;
			  return replaceState.apply(this, arguments);
			};
			state.capture = function(event) {
			  if (!state.enabled || event.defaultPrevented || event.button !== 0 ||
			      event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
			  var target = event.target;
			  target = target && target.closest ? target.closest('a[href]') : null;
			  if (!target) return;
			  var url = asPlayerUrl(target.href);
			  if (!url) return;
			  event.preventDefault();
			  event.stopImmediatePropagation();
			  state.send(url);
			};
			var restoreHash = function() {
			  if (!state.enabled) return;
			  var url = asPlayerUrl(window.location.href);
			  if (!url) { state.lastSafeUrl = window.location.href; return; }
			  state.send(url);
			  try { replaceState.call(history, history.state, document.title, state.lastSafeUrl); }
			  catch (_) {}
			};
			window.addEventListener('click', state.capture, true);
			window.addEventListener('hashchange', restoreHash, true);
			window.addEventListener('popstate', restoreHash, true);
			  window.addEventListener('pagehide', function(){ state.setEnabled(false); }, {once:true});
			  window.__fermataStremioOpenOnCarV1 = state;
			})();
			""", generation, generation);
	}

	private static String disableSource() {
		return "(function(){var s=window.__fermataStremioOpenOnCarV1;" +
				"if(s&&s.version===1&&s.setEnabled)s.setEnabled(false);})()";
	}

	interface Listener {
		void onPlayerRouteSelected(String route);
	}
}
