package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_READY;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_TOUCHED;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;

import org.json.JSONTokener;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private static final String CLEAR_HIGHEST_VIDEO_QUALITY_JS =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";
	private static final long MANUAL_FULLSCREEN_GESTURE_WINDOW_MS = 1000L;
	private YoutubeJsInterface js;
	private float videoTapX;
	private float videoTapY;
	private long videoTapTime;
	private float playbackTapX;
	private float playbackTapY;
	private long playbackTapTime;

	public YoutubeWebView(Context context) {
		super(context);
	}

	public YoutubeWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public YoutubeWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected FermataJsInterface createJsInterface() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		return js = new YoutubeJsInterface(this, new YoutubeMediaEngine(this, a));
	}

	@Override
	public YoutubeAddon getAddon() {
		return (YoutubeAddon) super.getAddon();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		super.onPreferenceChanged(store, prefs);

		if (getAddon().autoHighestQualityChanged(prefs)) {
			if (getAddon().autoHighestQuality()) setHighestVideoQuality();
			else clearHighestVideoQuality();
		}

		if (YoutubeSponsorBlock.isPreferenceChanged(prefs)) injectSponsorBlock();
	}

	@Override
	public void loadUrl(@NonNull String url) {
		Log.d("Loading URL: " + url);
		super.loadUrl(url);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		handlePlaybackIntentTouch(event);
		grantManualFullScreenEntry(event);
		handleAutoVideoTouch(event);
		return super.onTouchEvent(event);
	}

	@Override
	public void goBack() {
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if (cb.getEngine() instanceof YoutubeMediaEngine) cb.onStop();
		super.goBack();
	}

	@Override
	protected void onUserExitFullScreen() {
		if (js != null) js.onUserExitFullScreen();
	}

	boolean exitPlaybackFullScreenForBack() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		FermataChromeClient chrome = getWebChromeClient();
		boolean browserFullScreen = (chrome != null) && chrome.isFullScreen();

		// Invalidate pending auto-entry before changing either asynchronous fullscreen state.
		if ((js == null) || !js.onPlayerBack(a.isVideoMode(), browserFullScreen)) return false;

		if (chrome != null) chrome.cancelPendingFullScreenEntry();
		if (browserFullScreen) chrome.exitFullScreen();
		else a.setVideoMode(false, null);
		a.setBarsHidden(false);
		return true;
	}

	boolean acceptsBrowserFullScreen(long request) {
		return (js != null) && js.acceptsBrowserFullScreen(request);
	}

	private void grantManualFullScreenEntry(MotionEvent event) {
		if ((js == null) || (event.getActionMasked() != MotionEvent.ACTION_UP)) return;
		long permit = js.grantManualFullScreenEntry();
		if (permit == YoutubeFullscreenGate.NO_REQUEST) return;
		postDelayed(() -> {
			if (js != null) js.expireManualFullScreenEntry(permit);
		}, MANUAL_FULLSCREEN_GESTURE_WINDOW_MS);
	}

	private void handlePlaybackIntentTouch(MotionEvent event) {
		if (!BuildConfig.AUTO || (js == null)) return;
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				playbackTapX = event.getX();
				playbackTapY = event.getY();
				playbackTapTime = event.getEventTime();
			}
			case MotionEvent.ACTION_UP -> {
				if (playbackTapTime == 0L) return;
				float dx = Math.abs(event.getX() - playbackTapX);
				float dy = Math.abs(event.getY() - playbackTapY);
				int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
				long elapsed = event.getEventTime() - playbackTapTime;
				playbackTapTime = 0L;
				if ((elapsed <= ViewConfiguration.getDoubleTapTimeout()) &&
						(dx <= slop) && (dy <= slop)) {
					js.onPlaybackGesture(event.getEventTime());
				}
			}
			case MotionEvent.ACTION_CANCEL -> playbackTapTime = 0L;
		}
	}

	private void handleAutoVideoTouch(MotionEvent event) {
		if (!BuildConfig.AUTO) return;
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		if (!a.isVideoMode() || a.getBody().isBothMode()) return;
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (!(cb.getEngine() instanceof YoutubeMediaEngine)) return;

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				videoTapX = event.getX();
				videoTapY = event.getY();
				videoTapTime = event.getEventTime();
			}
			case MotionEvent.ACTION_UP -> {
				if (videoTapTime == 0L) return;
				float dx = Math.abs(event.getX() - videoTapX);
				float dy = Math.abs(event.getY() - videoTapY);
				int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
				long elapsed = event.getEventTime() - videoTapTime;
				videoTapTime = 0L;
				if ((elapsed <= ViewConfiguration.getDoubleTapTimeout()) && (dx <= slop) && (dy <= slop))
					a.getControlPanel().onTouch(null);
			}
			case MotionEvent.ACTION_CANCEL -> videoTapTime = 0L;
		}
	}

	@Override
	protected void pageLoaded(String uri) {
		getAddon().setLastYoutubeUrl(uri);
		attachListeners();
		injectSponsorBlock();
		addFocusHighlight();
		flushCookiesSoon();
	}

	protected void submitForm() {
		if (!me.aap.fermata.BuildConfig.AUTO) return;
		loadUrl("javascript:\n" +
				"var e = new KeyboardEvent('keydown',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);\n" +
				"e = new KeyboardEvent('keyup',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);");
	}

	private void attachListeners() {
		String debug = BuildConfig.D ? "event(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		evaluateJavascript(String.format(Locale.ROOT, """
				(function() {
				  const scale = '%s';
				  const state = window.__fermataVideoState || (window.__fermataVideoState = {});
				  if (state.observer) state.observer.disconnect();
				  if (state.urlTimer) clearInterval(state.urlTimer);
				  state.lastUrl = location.href;
				  function event(code, data) {
				    try { %s(code, data); } catch (err) {}
				  }
				  function isVideoPage() {
				    return location.pathname === '/watch' || location.pathname.startsWith('/shorts/');
				  }
				  function videoUrl(v) {
				    return (v && (v.currentSrc || v.src)) || location.href || '';
				  }
				  function notifyState(v) {
				    if (!v) return;
				    v.style.objectFit = scale;
				    if (!isVideoPage()) return;
				    if (!v.paused && !v.ended) event(%d, videoUrl(v));
				    else event(%d, videoUrl(v));
				  }
				  function attachVideoListeners(v) {
				    if (!v || v.__fermataAttached) {
				      notifyState(v);
				      return;
				    }
				    v.__fermataAttached = true;
				    v.style.objectFit = scale;
				    %s
				    notifyState(v);
				    v.addEventListener('playing', function() { event(%d, videoUrl(v)); });
				    v.addEventListener('pause', function() { event(%d, videoUrl(v)); });
				    v.addEventListener('ended', function() { event(%d, null); });
				    v.addEventListener('click', function() { event(%d, null); }, true);
				    v.addEventListener('touchend', function() { event(%d, null); }, true);
				  }
				  function scan(root) {
				    if (!root) return;
				    if (root.tagName === 'VIDEO') attachVideoListeners(root);
				    if (root.querySelectorAll) root.querySelectorAll('video').forEach(attachVideoListeners);
				  }
				  function scanDocument() { scan(document); }
				  scan(document);
				  state.observer = new MutationObserver(function(records) {
				    records.forEach(function(record) {
				      record.addedNodes.forEach(scan);
				    });
				  });
				  if (document.documentElement) {
				    state.observer.observe(document.documentElement, { childList: true, subtree: true });
				  }
				  state.urlTimer = setInterval(function() {
				    if (state.lastUrl !== location.href) {
				      state.lastUrl = location.href;
				      setTimeout(scanDocument, 250);
				    }
				  }, 500);
				})();""", scale, JS_EVENT, JS_VIDEO_PLAYING, JS_VIDEO_READY, debug, JS_VIDEO_PLAYING,
				JS_VIDEO_PAUSED, JS_VIDEO_ENDED, JS_VIDEO_TOUCHED, JS_VIDEO_TOUCHED), null);
	}

	void syncPlaybackState() {
		evaluateJavascript(String.format(Locale.ROOT, """
				(function() {
				  var v = document.querySelector('video');
				  if (!v) return;
				  if ((location.pathname !== '/watch') && !location.pathname.startsWith('/shorts/')) return;
				  var url = v.currentSrc || v.src || location.href || '';
				  if (!v.paused && !v.ended) %s(%d, url);
				  else %s(%d, url);
				})();""", JS_EVENT, JS_VIDEO_PLAYING, JS_EVENT, JS_VIDEO_READY), null);
	}

	private void injectSponsorBlock() {
		String script = YoutubeSponsorBlock.getScript(getContext(), getAddon().getPreferenceStore());
		if (!script.isEmpty()) evaluateJavascript(script, result -> configureSponsorBlock());
		else configureSponsorBlock();
	}

	private void configureSponsorBlock() {
		evaluateJavascript("if (window.FermataSponsorBlock) window.FermataSponsorBlock.configure(" +
				YoutubeSponsorBlock.getConfigJson(getAddon().getPreferenceStore()) + ");", null);
	}

	protected boolean requestFullScreen() {
		evaluateJavascript("var v = document.querySelector('video');\n" +
				"if (v && ('webkitRequestFullscreen' in v)) v.webkitRequestFullscreen();\n" +
				"else if (v && ('requestFullscreen' in v)) v.requestFullscreen();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Method requestFullscreen not found in ' + v);", null);
		return true;
	}

	void setImmersiveVideoMode(boolean enabled) {
		evaluateJavascript(String.format(Locale.ROOT, """
				(function(enabled) {
				  const id = 'fermata-yt-immersive-style';
				  let style = document.getElementById(id);
				  if (enabled) {
				    if (!style) {
				      style = document.createElement('style');
				      style.id = id;
				      style.textContent = `
				        html.fermata-yt-immersive,
				        html.fermata-yt-immersive body {
				          background: #000 !important;
				          overflow: hidden !important;
				        }
				        html.fermata-yt-immersive body *:not(video) {
				          visibility: hidden !important;
				        }
				        html.fermata-yt-immersive video {
				          visibility: visible !important;
				          position: fixed !important;
				          inset: 0 !important;
				          width: 100vw !important;
				          height: 100vh !important;
				          max-width: none !important;
				          max-height: none !important;
				          object-fit: contain !important;
				          background: #000 !important;
				          z-index: 2147483647 !important;
				        }`;
				      document.documentElement.appendChild(style);
				    }
				    document.documentElement.classList.add('fermata-yt-immersive');
				  } else {
				    document.documentElement.classList.remove('fermata-yt-immersive');
				  }
				})(%s);""", enabled ? "true" : "false"), null);
	}

	void play() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.play();");
	}

	void pause() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.pause();");
	}

	void stop() {
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				"if (v != null) { v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		prevNext(false);
	}

	void next() {
		prevNext(true);
	}

	private void prevNext(boolean next) {
		FermataChromeClient chrome = getWebChromeClient();
		if (chrome == null) return;
		chrome.exitFullScreen().thenRun(() -> evaluateJavascript(String.format(Locale.ROOT, """
				function prevNextVideo() {
				  const buttons = document.querySelectorAll('button.player-middle-controls-prev-next-button');
				  if (buttons) buttons[%d].click();
				}
				setTimeout(prevNextVideo, 600);
				""", next ? 1 : 0), null));
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	FutureSupplier<String> getVideoQualities() {
		Promise<String> p = js.getResultPromise();
		loadUrl("javascript:\n" +
				"function retryGetVideoQualities(attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(getVideoQualities, 100, attempt + 1, openMenu);\n" +
				"  else " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", null);\n" +
				"  return null;\n" +
				"}\n" +
				"function getVideoQualities(attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retryGetVideoQualities(attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var result = '';\n" +
				"  for (let i = 0; i < options.length; i++) {\n" +
				"    if (i != 0) result += ';';\n" +
				"    if (i == select.selectedIndex) result += '*';\n" +
				"    result += options[i].innerText;\n" +
				"  }\n" +
				"  " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", result);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return result;\n" +
				"}\n" +
				"getVideoQualities(0, true);");
		return p;
	}

	void setVideoQuality(int idx) {
		loadUrl("javascript:\n" +
				"function retrySetVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(setVideoQuality, 100, idx, attempt + 1, openMenu);\n" +
				"  return false;\n" +
				"}\n" +
				"function setVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retrySetVideoQuality(idx, attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var evt = document.createEvent(\"HTMLEvents\");\n" +
				"  evt.initEvent(\"change\", true, true);\n" +
				"  select.selectedIndex = idx;\n" +
				"  options[idx].selected = true;\n" +
				"  select.dispatchEvent(evt);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return true;\n" +
				"}\n" +
				"setVideoQuality(" + idx + ", 0, true);");
	}

	void setHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"  var state = window.__fermataQ = { player: null, handler: null, timeout: null, attempts: 0 };\n" +
				"  function getPlayer() {\n" +
				"    return document.querySelector('#movie_player') || document.querySelector('.html5-video-player');\n" +
				"  }\n" +
				"  function applyHighest(p) {\n" +
				"    if (!p || typeof p.getAvailableQualityLevels !== 'function') return false;\n" +
				"    var levels = p.getAvailableQualityLevels();\n" +
				"    if (!levels || levels.length === 0) return false;\n" +
				"    var best = null;\n" +
				"    for (var i = 0; i < levels.length; i++) {\n" +
				"      if (levels[i] !== 'auto') { best = levels[i]; break; }\n" +
				"    }\n" +
				"    if (!best) return false;\n" +
				"    if (p.getPlaybackQuality && p.getPlaybackQuality() === best) return true;\n" +
				"    if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(best, best);\n" +
				"    else if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(best);\n" +
				"    else return false;\n" +
				"    return true;\n" +
				"  }\n" +
				"  function install() {\n" +
				"    var p = getPlayer();\n" +
				"    if (!p || typeof p.addEventListener !== 'function') {\n" +
				"      if (++state.attempts < 50) state.timeout = setTimeout(install, 200);\n" +
				"      return;\n" +
				"    }\n" +
				"    state.player = p;\n" +
				"    state.handler = function(s) {\n" +
				"      if ((s === 1) || (s === 3)) applyHighest(getPlayer() || p);\n" +
				"    };\n" +
				"    p.addEventListener('onStateChange', state.handler);\n" +
				"    applyHighest(p);\n" +
				"  }\n" +
				"  install();\n" +
				"})();");
	}

	void clearHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"})();");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v." + value +
						" : 0})();",
				v -> {
					try {
						p.complete((long) (Double.parseDouble(v) * 1000));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(0L);
					}
				});
		return p;
	}

	void setPosition(long position) {
		double pos = position / 1000f;
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.currentTime = " +
				pos + ";");
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v" +
						".playbackRate" +
						" " +
						": 0})();",
				v -> {
					try {
						p.complete(Float.parseFloat(v));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(1f);
					}
				});
		return p;
	}

	void setSpeed(float speed) {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.playbackRate =" +
				" " +
				speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		evaluateJavascript("document.title", value -> p.complete(decodeJavascriptString(value)));
		return p;
	}

	private static String decodeJavascriptString(String value) {
		if ((value == null) || value.equals("null")) return "";
		try {
			Object decoded = new JSONTokener(value).nextValue();
			return (decoded instanceof String) ? (String) decoded : value;
		} catch (Exception ex) {
			return value;
		}
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		evaluateJavascript(
				"document.querySelectorAll('video')" +
				".forEach(v => v.style.objectFit = '" + p + "');", null);
	}
}
