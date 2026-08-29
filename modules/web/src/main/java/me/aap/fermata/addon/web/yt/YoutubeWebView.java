package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FULLSCREEN_TAP;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_READY;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_TOUCHED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_PLAYBACK_INTENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_NAVIGATION;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URISyntaxException;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.voice.VoiceSession;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private static final String HOME_URL = "https://m.youtube.com/";
	private static final int VOICE_RESULTS_MAX_ATTEMPTS = 12;
	private static final long VOICE_RESULTS_RETRY_MS = 350L;
	private static final int RELOAD_AUDIO_MAX_ATTEMPTS = 20;
	private static final long RELOAD_AUDIO_RETRY_MS = 300L;
	private final Handler sponsorHandler = new Handler(Looper.getMainLooper());
	private final YoutubeReloadCoordinator reloadCoordinator = new YoutubeReloadCoordinator();
	private final YoutubeNavigationCoordinator navigation = new YoutubeNavigationCoordinator();
	private FutureSupplier<List<SponsorBlockClient.Segment>> sponsorRequest;
	private List<SponsorBlockClient.Segment> sponsorSegments = List.of();
	private int sponsorSegmentIndex;
	private int sponsorSkippedSegmentIndex = -1;
	private long sponsorSkippedTargetMillis = -1L;
	private long sponsorGeneration;
	private String sponsorVideoId = "";
	private boolean sponsorPlaybackPaused;
	private Runnable sponsorCheck;
	private Runnable sponsorRetry;
	private int sponsorRetryAttempt;
	private boolean sponsorLoadComplete;
	private static final String PLAYBACK_SIGNAL_JS = YoutubeScripts.PLAYBACK_SIGNAL;
	private static final String AD_SKIP_JS = YoutubeScripts.AD_SKIP;
	private static final long MANUAL_FULLSCREEN_GESTURE_WINDOW_MS = 1_500L;
	private YoutubeJsInterface js;
	private YoutubeMediaEngine mediaEngine;
	private float tapX;
	private float tapY;
	private long tapTime;
	private boolean pendingVoiceSearch;
	private long pendingVoiceRequestId;
	private boolean voiceSearchCollecting;
	private int voiceSearchGeneration;
	private boolean initialPlaybackNavigationClaimed;
	private boolean clearHistoryOnNextPageCommit;
	private boolean fullscreenTapEnabled;
	private int reloadAudioGeneration, autoNextAudioGeneration;
	private boolean reloadAudioRestorePending;
	private boolean reloadAudioPageCommitted;
	private String reloadAudioVideoId = "";
	private double reloadAudioVolume = 1d;
	private boolean autoNextAudioRestorePending;
	private String autoNextAudioSourceId = "";
	private String autoNextAudioTargetId = "";
	private double autoNextAudioVolume = 1d;

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
	protected YoutubeWebView createReplacementView(Context context) {
		return new YoutubeWebView(context);
	}

	@Override
	protected FermataJsInterface createJsInterface() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		mediaEngine = new YoutubeMediaEngine(this, a);
		return js = new YoutubeJsInterface(this, mediaEngine);
	}

	@Override
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
			FermataChromeClient chromeClient) {
		super.init(addon, webClient, chromeClient);
		navigation.open(getAddon());
		initialPlaybackNavigationClaimed = false;
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		MediaSessionCallback callback = activity.getMediaSessionCallback();
		boolean preferredHost = getAddon().isPreferredPlaybackActivity(activity);
		getAddon().getRuntime().registerHost(this, mediaEngine, activity);
		if (preferredHost && (callback.getEngine() instanceof YoutubeSessionEngine stable) &&
				stable.belongsTo(getAddon()))
			initialPlaybackNavigationClaimed = stable.owns(mediaEngine);
	}

	boolean consumeInitialPlaybackNavigationClaim() {
		boolean claimed = initialPlaybackNavigationClaimed;
		initialPlaybackNavigationClaimed = false;
		return claimed;
	}

	YoutubeMediaEngine getMediaEngine() {
		return mediaEngine;
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

		if (YoutubeSponsorBlock.isPreferenceChanged(prefs)) {
			cancelSponsorBlock();
			injectSponsorBlock();
		}
		if (getAddon().skipAdChanged(prefs)) configureAdSkip();
	}

	@Override
	public void loadUrl(@NonNull String url) {
		Log.d("Loading URL: " + url);
		super.loadUrl(url);
	}

	boolean loadExplicitUrl(@NonNull String url) {
		if (!navigation.begin(true)) return false;
		armExplicitPlayback();
		loadUrl(url);
		return true;
	}

	void onMainFramePageStarted() {
		navigation.pageStarted();
	}

	long sessionGeneration() {
		return navigation.current();
	}

	@Override
	public void reload() {
		if (!BuildConfig.YT_AUDIO_RESTORE || (mediaEngine == null) ||
				!mediaEngine.isPlaybackActive()) {
			cancelReloadAudioRestore();
			super.reload();
			return;
		}

		int generation = ++reloadAudioGeneration;
		reloadAudioRestorePending = false;
		reloadAudioPageCommitted = false;
		reloadCoordinator.capture(this, generation);
	}

	int reloadAudioGeneration() {
		return reloadAudioGeneration;
	}

	void captureReloadAudioState(String value) {
		try {
			JSONObject state = new JSONObject(YoutubeScripts.decodeJavascriptString(value));
			String videoId = state.optString("id", "");
			if (state.optBoolean("playing") && state.optBoolean("audible") &&
					mediaEngine.ownsPlayback(videoId)) {
				reloadAudioVideoId = videoId;
				reloadAudioVolume = Math.max(0.01d, Math.min(1d,
						state.optDouble("volume", 1d)));
				reloadAudioRestorePending = true;
			}
		} catch (Exception error) {
			Log.d(error, "Unable to capture YouTube audio state before reload");
		}
	}

	void reloadPage() {
		super.reload();
	}

	void resetToHome() {
		if (!navigation.begin(false)) return;
		cancelReloadAudioRestore();
		cancelAutoNextAudioRestore();
		leavePlaybackPresentation();
		clearHistoryOnNextPageCommit = true;
		getAddon().setLastYoutubeUrl(HOME_URL, navigation.current());
		stopLoading();
		loadUrl(HOME_URL);
	}

	void prepareVoiceSearch(long voiceRequestId) {
		voiceSearchGeneration++;
		pendingVoiceSearch = true;
		pendingVoiceRequestId = voiceRequestId;
		voiceSearchCollecting = false;
	}

	void collectVoiceSearchResults(String url) {
		if (!pendingVoiceSearch || voiceSearchCollecting || !isVoiceSearchResultsUrl(url)) return;
		voiceSearchCollecting = true;
		collectVoiceSearchResults(voiceSearchGeneration, 0);
	}

	private void collectVoiceSearchResults(int generation, int attempt) {
		if (!isCurrentVoiceSearch(generation)) return;
		if (!isVoiceSearchResultsUrl(getUrl())) {
			finishVoiceSearch(generation);
			return;
		}
		evaluateJavascript(YoutubeScripts.VOICE_RESULTS, value -> {
			if (!isCurrentVoiceSearch(generation)) return;
			try {
				JSONArray array = new JSONArray(YoutubeScripts.decodeJavascriptString(value));
				List<VoiceSession.Option> options = new ArrayList<>(Math.min(3, array.length()));
				for (int i = 0; i < array.length() && i < 3; i++) {
					JSONObject item = array.getJSONObject(i);
					String id = item.optString("id", "");
					String title = item.optString("title", "");
					if (id.isEmpty() || title.isEmpty()) continue;
					options.add(new VoiceSession.Option("youtube:video:" + id, title,
							item.optString("channel", ""), "youtube"));
				}
				if (!options.isEmpty()) {
					long requestId = pendingVoiceRequestId;
					finishVoiceSearch(generation);
					MainActivityDelegate.get(getContext()).beginVoiceSelectionOptions(
							requestId, options);
					return;
				}
			} catch (Exception err) {
				Log.d(err, "Failed to parse YouTube voice results");
			}
			if (attempt + 1 >= VOICE_RESULTS_MAX_ATTEMPTS) {
				long requestId = pendingVoiceRequestId;
				finishVoiceSearch(generation);
				if (requestId != 0L) {
					MainActivityDelegate.get(getContext()).completeVoiceTransaction(requestId);
				}
			} else {
				postDelayed(() -> collectVoiceSearchResults(generation, attempt + 1),
						VOICE_RESULTS_RETRY_MS);
			}
		});
	}

	private boolean isCurrentVoiceSearch(int generation) {
		return pendingVoiceSearch && voiceSearchCollecting &&
				(generation == voiceSearchGeneration);
	}

	private void finishVoiceSearch(int generation) {
		if (generation != voiceSearchGeneration) return;
		pendingVoiceSearch = false;
		pendingVoiceRequestId = 0L;
		voiceSearchCollecting = false;
	}

	static boolean isVoiceSearchResultsUrl(String url) {
		if ((url == null) || url.isBlank()) return false;
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException ex) {
			return false;
		}
		String host = uri.getHost();
		return "/results".equals(uri.getPath()) && (host != null) &&
				(host.equals("youtube.com") || host.endsWith(".youtube.com"));
	}

	void playVoiceVideo(String videoId) {
		loadExplicitUrl("https://www.youtube.com/watch?v=" + videoId);
	}

	void armExplicitPlayback() {
		if (js != null) js.armExplicitPlayback();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		handlePlaybackTap(event);
		return super.onTouchEvent(event);
	}

	@Override
	public void goBack() {
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if ((cb.getEngine() instanceof YoutubeMediaEngine) ||
				(cb.getEngine() instanceof YoutubeSessionEngine)) {
			cb.onStop();
		}
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

		return (js != null) && js.onPlayerBack(a.isVideoMode(), browserFullScreen);
	}

	void leavePlaybackPresentation() {
		if (js != null) js.onUserExitFullScreen();
		FermataChromeClient chrome = getWebChromeClient();
		if ((chrome != null) && chrome.isFullScreen()) chrome.exitFullScreen();
		setImmersiveVideoMode(false);
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		if (activity.isVideoMode() && (activity.getMediaSessionCallback().getEngine() == mediaEngine))
			activity.setVideoMode(false, null);
	}

	YoutubeFullscreenCoordinator.Suspension suspendPlaybackPresentation() {
		return (js == null) ? null : js.suspendFullscreenForHostInterruption();
	}

	boolean resumePlaybackPresentation(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		return (js != null) && js.resumeFullscreenAfterHostInterruption(suspension);
	}

	void discardPlaybackPresentationSuspension(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		if (js != null) js.discardFullscreenHostInterruption(suspension);
	}

	boolean isPlaybackPresentationSuspensionCurrent(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		return (js != null) && js.isFullscreenHostInterruptionCurrent(suspension);
	}

	boolean acceptsBrowserFullScreen(long request) {
		return (js != null) && js.acceptsBrowserFullScreen(request);
	}

	boolean enterManualFullScreen() {
		return (js != null) && js.enterManualAppFullScreen();
	}

	void setFullscreenTapEnabled(boolean enabled) {
		enabled = usesAutoPlaybackBehavior() && enabled;
		if (fullscreenTapEnabled == enabled) return;
		fullscreenTapEnabled = enabled;
		evaluateJavascript("window.__fermataFullscreenTapEnabled = " + enabled + ";", null);
	}

	void onBrowserFullScreenRejected() {
		// A WebView callback may arrive after the coordinator has already entered its
		// gesture-free fallback. Reject the stale custom view without tearing down the
		// presentation that currently owns the screen.
		if ((mediaEngine != null) && mediaEngine.isFallbackFullScreenActive()) {
			setImmersiveVideoMode(true);
			return;
		}
		setImmersiveVideoMode(false);
		evaluateJavascript("(function(){if(document.fullscreenElement&&document.exitFullscreen)" +
				"document.exitFullscreen();})()", null);
	}

	void onBrowserFullScreenChanged(boolean fullScreen) {
		if (js != null) js.onBrowserFullScreenChanged(fullScreen);
	}

	private void handlePlaybackTap(MotionEvent event) {
		if (js == null) return;
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				tapX = event.getX();
				tapY = event.getY();
				tapTime = event.getEventTime();
				return;
			}
			case MotionEvent.ACTION_UP -> {
				if (tapTime == 0L) return;
				float dx = Math.abs(event.getX() - tapX);
				float dy = Math.abs(event.getY() - tapY);
				int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
				long elapsed = event.getEventTime() - tapTime;
				tapTime = 0L;
				if ((elapsed > ViewConfiguration.getDoubleTapTimeout()) ||
						(dx > slop) || (dy > slop)) return;
			}
			case MotionEvent.ACTION_CANCEL -> {
				tapTime = 0L;
				return;
			}
			default -> {
				return;
			}
		}

		if (usesAutoPlaybackBehavior()) js.onPlaybackGesture(event.getEventTime());
		long permit = js.grantManualFullScreenEntry();
		if (permit != YoutubeFullscreenGate.NO_REQUEST) {
			postDelayed(() -> {
				if (js != null) js.expireManualFullScreenEntry(permit);
			}, MANUAL_FULLSCREEN_GESTURE_WINDOW_MS);
		}

		if (!usesAutoPlaybackBehavior()) return;
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		if (activity.isVideoMode() && !activity.getBody().isBothMode() &&
				(activity.getMediaSessionCallback().getEngine() == mediaEngine)) {
			// JavaScript may report the same tap; the engine owns the debounce.
			js.getEngine().touched();
		}
	}

	boolean usesAutomotiveHost() {
		return runtimePolicy().automotivePresentation();
	}

	boolean usesAutoPlaybackBehavior() {
		return runtimePolicy().autoPlaybackBehavior();
	}

	private YoutubeRuntimePolicy runtimePolicy() {
		return YoutubeRuntimePolicy.resolve(BuildConfig.AUTO, getRuntimeHostMode());
	}

	@Override
	protected void pageLoaded(String uri) {
		if (!navigation.acceptsPage(uri, getUrl())) {
			Log.d("Ignoring stale YouTube page callback: " + uri);
			return;
		}
		long generation = navigation.current();
		getAddon().setLastYoutubeUrl(uri, generation);
		if (clearHistoryOnNextPageCommit) {
			clearHistoryOnNextPageCommit = false;
			post(() -> {
				clearHistory();
				notifyToolbarPageChanged();
			});
		}
		attachListeners((mediaEngine == null) ? 0L : mediaEngine.playbackGenerationSeed(),
				generation);
		if (mediaEngine != null) mediaEngine.onPageLoaded(uri);
		injectSponsorBlock();
		configureAdSkip();
		addFocusHighlight();
		flushCookiesSoon();
		notifyToolbarPageChanged();
		if (reloadAudioRestorePending) {
			reloadAudioPageCommitted = true;
			restoreReloadAudio(0, reloadAudioGeneration);
		}
	}

	boolean acceptsPageCallback(String url) {
		return navigation.acceptsPage(url, getUrl());
	}

	void onSpaNavigation(String data) {
		YoutubeNavigationCoordinator.Navigation accepted = navigation.acceptSpa(data, getUrl());
		if (accepted == null) return;
		String url = accepted.url();
		getAddon().setLastYoutubeUrl(url, accepted.generation());
		if (mediaEngine != null) mediaEngine.onPageLoaded(url);
		collectVoiceSearchResults(url);
		notifyToolbarPageChanged();
	}

	private void notifyToolbarPageChanged() {
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(activity -> {
			if (activity.getActiveFragment() instanceof YoutubeFragment youtube)
				youtube.onPageNavigationChanged();
		});
	}

	protected void submitForm() {
		if (!usesAutoPlaybackBehavior()) return;
		leavePlaybackPresentation();
		super.submitForm();
	}

	private void attachListeners(long seedGeneration, long sessionGeneration) {
		String debug = BuildConfig.D ? "event(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		evaluateJavascript(String.format(Locale.ROOT, """
				(function() {
				  const scale = '%s';
				  const state = window.__fermataVideoState || (window.__fermataVideoState = {});
				  if (state.observer) state.observer.disconnect();
				  if (state.urlTimer) clearInterval(state.urlTimer);
				  state.lastUrl = location.href;
				  state.lastTitlePage = '';
				  state.lastEvent = 0;
				  state.lastSignal = '';
				  window.__fermataPlaybackGeneration = %d;
				  const sessionGeneration = %d;
				  window.__fermataFullscreenTapEnabled = %b;
				  function event(code, data) {
				    try { %s(code, data); } catch (err) {}
				  }
				  %s
				  function isVideoPage() {
				    return location.pathname === '/watch' || location.pathname.startsWith('/shorts/');
				  }
				  if (state.intentListener)
				    document.removeEventListener('click', state.intentListener, true);
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
				      if (youtube && video) event(%d, url.href);
				    } catch (err) {}
				  };
				  document.addEventListener('click', state.intentListener, true);
				  function activeVideo() { return fermataActiveContentVideo(); }
				  function isActiveVideo(v) {
				    return !!v && isVideoPage() && v === activeVideo();
				  }
			  function emitState(code, v) {
			    if (!isActiveVideo(v)) return;
			    var signal = fermataVideoSignal(v);
			    if (!signal) return;
				    if ((state.lastEvent === code) && (state.lastSignal === signal)) return;
				    state.lastEvent = code;
				    state.lastSignal = signal;
				    event(code, signal);
				  }
				  function notifyState(v) {
				    if (!isActiveVideo(v)) return;
				    v.style.objectFit = scale;
				    if (!v.paused && !v.ended) emitState(%d, v);
				    else emitState(%d, v);
				    if (state.lastTitlePage !== location.href) {
				      state.lastTitlePage = location.href;
				      setTimeout(function() { retryTitle(location.href, 0); }, 250);
				    }
				  }
				  function retryTitle(page, attempt) {
				    if (location.href !== page) return;
				    var current = activeVideo();
				    if (current) notifyState(current);
				    if (attempt < 5)
				      setTimeout(function() { retryTitle(page, attempt + 1); }, 300);
				  }
				  function attachVideoListeners(v) {
				    if (!v || v.__fermataAttached) {
				      notifyState(v);
				      return;
				    }
				    v.__fermataAttached = true;
				    v.__fermataGeneration = window.__fermataPlaybackGeneration || 0;
				    v.style.objectFit = scale;
				    %s
				    notifyState(v);
				    v.addEventListener('playing', function() { emitState(%d, v); });
				    v.addEventListener('pause', function() { emitState(%d, v); });
				    v.addEventListener('ended', function() {
				      var adState = window.__fermataAdState;
				      var adKey = (v.currentSrc || v.src || location.href) + ':' +
				        Math.floor((v.duration || 0) * 10);
				      if (isActiveVideo(v) && !(adState && adState.suppressEndedUntil > Date.now() &&
				          adState.suppressEndedKey === adKey)) {
				        event(%d, fermataVideoSignal(v));
				      }
				    });
				    v.addEventListener('click', function(e) {
				      if (!isActiveVideo(v)) return;
				      if (window.__fermataFullscreenTapEnabled === true) {
				        var signal = fermataVideoSignal(v);
				        if (!signal) return;
				        window.__fermataFullscreenTapEnabled = false;
				        e.preventDefault();
				        e.stopPropagation();
				        e.stopImmediatePropagation();
				        event(%d, signal);
				        return;
				      }
				      event(%d, null);
				    }, true);
				    v.addEventListener('touchend', function() {
				      if (isActiveVideo(v) && window.__fermataFullscreenTapEnabled !== true)
				        event(%d, null);
				    }, true);
				  }
				  function scan(root) {
				    if (!root) return;
				    if (root.tagName === 'VIDEO') attachVideoListeners(root);
				    if (root.querySelectorAll) root.querySelectorAll('video').forEach(attachVideoListeners);
				  }
				  function scanDocument() { scan(document); }
				  function scheduleScan(fullDocument) {
				    state.fullScanPending = state.fullScanPending || !!fullDocument;
				    if (state.scanPending) return;
				    state.scanPending = true;
				    requestAnimationFrame(function() {
				      state.scanPending = false;
				      if (state.fullScanPending) scanDocument();
				      state.fullScanPending = false;
				      notifyState(activeVideo());
				    });
				  }
				  function bindObserver() {
				    if (state.observer) state.observer.disconnect();
				    var player = document.querySelector('#movie_player, .html5-video-player');
				    var root = player || document.documentElement;
				    state.observedRoot = player || null;
				    state.observer = new MutationObserver(function(records) {
				      var nextPlayer = document.querySelector('#movie_player, .html5-video-player');
				      if (nextPlayer && nextPlayer !== state.observedRoot) bindObserver();
				      records.forEach(function(record) { record.addedNodes.forEach(scan); });
				      scheduleScan(false);
				    });
				    if (root) state.observer.observe(root, { childList: true, subtree: true });
				  }
				  scan(document);
				  bindObserver();
				  state.urlTimer = setInterval(function() {
				    if (state.lastUrl !== location.href) {
				      state.lastUrl = location.href;
				      state.lastTitlePage = '';
				      state.lastEvent = 0;
				      state.lastSignal = '';
				      var current = activeVideo();
				      if (current) current.__fermataGeneration =
				          Number(window.__fermataPlaybackGeneration || 0);
				      event(%d, JSON.stringify({
				        generation: sessionGeneration,
				        url: location.href
				      }));
				      setTimeout(function() { scheduleScan(true); }, 250);
				      setTimeout(function() { retryTitle(location.href, 0); }, 250);
				    }
				  }, 750);
				})();""", scale, Math.max(0L, seedGeneration),
				Math.max(0L, sessionGeneration), fullscreenTapEnabled,
				JS_EVENT, PLAYBACK_SIGNAL_JS, JS_PLAYBACK_INTENT,
				JS_VIDEO_PLAYING, JS_VIDEO_READY, debug, JS_VIDEO_PLAYING,
				JS_VIDEO_PAUSED, JS_VIDEO_ENDED, JS_VIDEO_FULLSCREEN_TAP,
				JS_VIDEO_TOUCHED, JS_VIDEO_TOUCHED, JS_NAVIGATION), null);
	}

	void syncPlaybackState() {
		evaluateJavascript(String.format(Locale.ROOT, """
				(function() {
				  %s
			  var v = fermataActiveContentVideo();
				  if (!v) return;
				  if ((location.pathname !== '/watch') && !location.pathname.startsWith('/shorts/')) return;
				  var signal = fermataVideoSignal(v);
				  if (!signal) return;
				  if (!v.paused && !v.ended) %s(%d, signal);
				  else %s(%d, signal);
				})();""", PLAYBACK_SIGNAL_JS, JS_EVENT, JS_VIDEO_PLAYING, JS_EVENT,
				JS_VIDEO_READY), null);
	}

	private void injectSponsorBlock() {
		String url = getUrl();
		me.aap.fermata.addon.web.yt.YoutubeItem item;
		try {
			item = me.aap.fermata.addon.web.yt.YoutubeItem.fromPageUrl(url, "", 0L);
		} catch (IllegalArgumentException ignored) {
			cancelSponsorBlock();
			return;
		}
		loadSponsorBlock(item);
	}

	void setPlaybackGeneration(long generation) {
		applyPlaybackGeneration(generation, false);
	}

	void rebindPlaybackGeneration(long generation) {
		applyPlaybackGeneration(generation, true);
	}

	void clearPlaybackGeneration() {
		applyPlaybackGeneration(0L, false);
	}

	private void applyPlaybackGeneration(long generation, boolean syncState) {
		String script = PLAYBACK_SIGNAL_JS +
				"window.__fermataPlaybackGeneration = " + Math.max(0L, generation) + ";" +
				"var v = fermataActiveContentVideo(); if (v) v.__fermataGeneration = " +
				Math.max(0L, generation) + ";" +
				"var a = window.__fermataAdState; if (a && a.rebindGeneration) " +
				"a.rebindGeneration();";
		if (syncState) evaluateJavascript(script, ignored -> syncPlaybackState());
		else evaluateJavascript(script, null);
	}

	void onYoutubePlaybackPaused() {
		sponsorPlaybackPaused = true;
		if (sponsorCheck != null) {
			sponsorHandler.removeCallbacks(sponsorCheck);
			sponsorCheck = null;
		}
	}

	void onYoutubePlaybackResumed() {
		sponsorPlaybackPaused = false;
		if (reloadAudioPageCommitted)
			restoreReloadAudio(0, reloadAudioGeneration);
		if (autoNextAudioRestorePending)
			restoreAutoNextAudio(0, autoNextAudioGeneration);
		if (sponsorSegments.isEmpty() || sponsorVideoId.isEmpty()) return;
		scheduleSponsorCheck(0L, sponsorGeneration, sponsorVideoId);
	}

	private void seekSponsorSegment(YoutubePlaybackSession.Snapshot playback,
			String videoId, long targetMillis) {
		long generation = playback.generation();
		evaluateJavascript(sponsorSeekScript(generation, videoId, targetMillis), null);
	}

	static String sponsorSeekScript(long generation, String videoId, long targetMillis) {
		return PLAYBACK_SIGNAL_JS +
				"(function(expectedGeneration, expectedVideoId, targetMillis) {" +
				"var v = fermataActiveContentVideo();" +
				"if (!v || v.paused || v.ended || " +
				"Number(v.__fermataGeneration || 0) !== expectedGeneration) return;" +
				"var url = new URL(location.href);" +
				"if (fermataPageVideoId() !== expectedVideoId) return;" +
				"v.currentTime = targetMillis / 1000;" +
				"}) (" + Math.max(0L, generation) + ", " + JSONObject.quote(videoId) + ", " +
				Math.max(0L, targetMillis) + ");";
	}

	void skipCurrentAd(long generation, String videoId) {
		evaluateJavascript(PLAYBACK_SIGNAL_JS +
				"(function(expectedGeneration, expectedVideoId) {" +
				"var state = window.__fermataAdState;" +
				"if (!state || !state.skipNow || " +
				"Number(window.__fermataPlaybackGeneration || 0) !== expectedGeneration || " +
				"fermataPageVideoId() !== expectedVideoId) return;" +
				"state.skipNow();" +
				"})(" + Math.max(0L, generation) + ", " + JSONObject.quote(videoId) + ");", null);
	}

	void retryCurrentAd(long generation, String videoId) {
		evaluateJavascript(PLAYBACK_SIGNAL_JS +
				"(function(expectedGeneration, expectedVideoId) {" +
				"var state = window.__fermataAdState;" +
				"if (!state || !state.retryAd || " +
				"Number(window.__fermataPlaybackGeneration || 0) !== expectedGeneration || " +
				"fermataPageVideoId() !== expectedVideoId) return;" +
				"state.retryAd();" +
				"})(" + Math.max(0L, generation) + ", " + JSONObject.quote(videoId) + ");", null);
	}

	void onYoutubePlaybackItemChanged(me.aap.fermata.addon.web.yt.YoutubeItem item) {
		if (item == null) return;
		loadSponsorBlock(item);
		if (autoNextAudioRestorePending && !item.videoId().equals(autoNextAudioSourceId)) {
			autoNextAudioTargetId = item.videoId();
			restoreAutoNextAudio(0, autoNextAudioGeneration);
		}
	}

	void onYoutubePlaybackOwnershipLost() {
		setFullscreenTapEnabled(false);
		cancelSponsorBlock();
		cancelReloadAudioRestore();
		cancelAutoNextAudioRestore();
	}

	private void restoreReloadAudio(int attempt, int generation) {
		if (!reloadAudioRestorePending || !reloadAudioPageCommitted ||
				(generation != reloadAudioGeneration)) return;
		String videoId = reloadAudioVideoId;
		if ((mediaEngine == null) || !mediaEngine.ownsPlayback(videoId)) {
			cancelReloadAudioRestore();
			return;
		}
		if (!mediaEngine.isPlaybackActive()) {
			retryReloadAudio(attempt, generation);
			return;
		}

		int volume = (int) Math.round(reloadAudioVolume * 100d);
		evaluateJavascript(audibleRestoreScript(videoId, volume), restored -> {
			if (!reloadAudioRestorePending || (generation != reloadAudioGeneration)) return;
			if ("true".equals(restored)) clearReloadAudioRestore(generation);
			else retryReloadAudio(attempt, generation);
		});
	}

	private void retryReloadAudio(int attempt, int generation) {
		if (attempt + 1 >= RELOAD_AUDIO_MAX_ATTEMPTS) {
			clearReloadAudioRestore(generation);
			return;
		}
		postDelayed(() -> restoreReloadAudio(attempt + 1, generation),
				RELOAD_AUDIO_RETRY_MS);
	}

	private void clearReloadAudioRestore(int generation) {
		if (generation != reloadAudioGeneration) return;
		reloadAudioRestorePending = false;
		reloadAudioPageCommitted = false;
		reloadAudioVideoId = "";
		reloadAudioVolume = 1d;
	}

	private void cancelReloadAudioRestore() {
		reloadAudioGeneration++;
		reloadCoordinator.cancel(this);
		clearReloadAudioRestore(reloadAudioGeneration);
	}

	void prepareAutoNextAudioRestore(YoutubePlaybackMetadata.Signal signal) {
		cancelAutoNextAudioRestore();
		if (!BuildConfig.YT_AUDIO_RESTORE) return;
		if ((signal == null) || !signal.isAudible() || signal.videoId().isEmpty()) return;
		autoNextAudioRestorePending = true;
		autoNextAudioSourceId = signal.videoId();
		autoNextAudioVolume = Math.max(0.01d, Math.min(1d, signal.volume()));
	}

	private void restoreAutoNextAudio(int attempt, int generation) {
		if (!autoNextAudioRestorePending || (generation != autoNextAudioGeneration)) return;
		String videoId = autoNextAudioTargetId;
		if (videoId.isEmpty() || (mediaEngine == null) || !mediaEngine.ownsPlayback(videoId) ||
				!mediaEngine.isPlaybackActive()) {
			retryAutoNextAudio(attempt, generation);
			return;
		}

		int volume = (int) Math.round(autoNextAudioVolume * 100d);
		evaluateJavascript(audibleRestoreScript(videoId, volume), restored -> {
			if (!autoNextAudioRestorePending || (generation != autoNextAudioGeneration)) return;
			if ("true".equals(restored)) clearAutoNextAudioRestore(generation);
			else retryAutoNextAudio(attempt, generation);
		});
	}

	private void retryAutoNextAudio(int attempt, int generation) {
		if (attempt + 1 >= RELOAD_AUDIO_MAX_ATTEMPTS) {
			clearAutoNextAudioRestore(generation);
			return;
		}
		postDelayed(() -> restoreAutoNextAudio(attempt + 1, generation),
				RELOAD_AUDIO_RETRY_MS);
	}

	private void clearAutoNextAudioRestore(int generation) {
		if (generation != autoNextAudioGeneration) return;
		autoNextAudioRestorePending = false;
		autoNextAudioSourceId = "";
		autoNextAudioTargetId = "";
		autoNextAudioVolume = 1d;
	}

	private void cancelAutoNextAudioRestore() {
		autoNextAudioGeneration++;
		clearAutoNextAudioRestore(autoNextAudioGeneration);
	}

	static String audibleRestoreScript(String videoId, int volume) {
		return PLAYBACK_SIGNAL_JS +
				"(function(expectedId,volume){" +
				"if(fermataPageVideoId()!==expectedId)return false;" +
				"var v=fermataActiveContentVideo();if(!v)return false;" +
				"var p=document.querySelector('#movie_player');" +
				"try{if(p&&typeof p.unMute==='function')p.unMute();" +
				"if(p&&typeof p.setVolume==='function')p.setVolume(volume);" +
				"v.muted=false;v.volume=Math.max(0.01,Math.min(1,volume/100));}catch(e){}" +
				"return !v.muted&&v.volume>0;})(" + JSONObject.quote(videoId) + "," +
				Math.max(1, Math.min(100, volume)) + ")";
	}

	private void loadSponsorBlock(me.aap.fermata.addon.web.yt.YoutubeItem item) {
		if ((item == null) || (item.videoId().equals(sponsorVideoId) &&
				((sponsorRequest != null) || (sponsorRetry != null) || sponsorLoadComplete))) return;
		cancelSponsorBlock();
		if (!getAddon().getSponsorBlockEnabled()) return;

		java.util.Set<SponsorBlockClient.Category> categories =
				YoutubeSponsorBlock.getCategories(getAddon().getPreferenceStore());
		if (categories.isEmpty()) return;
		long generation = ++sponsorGeneration;
		String videoId = sponsorVideoId = item.videoId();
		sponsorPlaybackPaused = false;
		SponsorBlockClient.Request request = new SponsorBlockClient.Request(item.videoId(), categories);
		requestSponsorBlock(request, generation, videoId);
	}

	private void requestSponsorBlock(SponsorBlockClient.Request request, long generation,
			String videoId) {
		FutureSupplier<List<SponsorBlockClient.Segment>> future =
				getAddon().getSponsorBlockController().getSegments(request).main();
		sponsorRequest = future;
		future.onCompletion((segments, error) -> {
			if (sponsorRequest == future) sponsorRequest = null;
			if (!isSponsorGeneration(generation, videoId)) return;
			if (error != null) {
				Log.d(error, "SponsorBlock unavailable");
				if (SponsorBlockClient.isRetryableFailure(error)) {
					scheduleSponsorRetry(request, generation, videoId);
				} else {
					sponsorLoadComplete = true;
				}
				return;
			}
			sponsorRetryAttempt = 0;
			sponsorLoadComplete = true;
			sponsorSegments = segments;
			sponsorSegmentIndex = 0;
			scheduleSponsorCheck(0L, generation, videoId);
		});
	}

	private void scheduleSponsorRetry(SponsorBlockClient.Request request, long generation,
			String videoId) {
		long delay = SponsorBlockSchedule.retryDelayMillis(sponsorRetryAttempt++);
		if (delay < 0L) return;
		if (sponsorRetry != null) sponsorHandler.removeCallbacks(sponsorRetry);
		sponsorRetry = () -> {
			sponsorRetry = null;
			if (!isSponsorGeneration(generation, videoId)) return;
			if (!isYoutubePlaybackOwned(videoId)) {
				cancelSponsorBlock();
				return;
			}
			requestSponsorBlock(request, generation, videoId);
		};
		sponsorHandler.postDelayed(sponsorRetry, delay);
	}

	protected boolean requestFullScreen() {
		evaluateJavascript(PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo();\n" +
				"if (v && ('webkitRequestFullscreen' in v)) v.webkitRequestFullscreen();\n" +
				"else if (v && ('requestFullscreen' in v)) v.requestFullscreen();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Method requestFullscreen not found in ' + v);", null);
		return true;
	}

	private void cancelSponsorBlock() {
		sponsorGeneration++;
		if (sponsorRequest != null) {
			sponsorRequest.cancel();
			sponsorRequest = null;
		}
		if (sponsorCheck != null) {
			sponsorHandler.removeCallbacks(sponsorCheck);
			sponsorCheck = null;
		}
		if (sponsorRetry != null) {
			sponsorHandler.removeCallbacks(sponsorRetry);
			sponsorRetry = null;
		}
		sponsorRetryAttempt = 0;
		sponsorLoadComplete = false;
		sponsorSegments = List.of();
		sponsorSegmentIndex = 0;
		sponsorSkippedSegmentIndex = -1;
		sponsorSkippedTargetMillis = -1L;
		sponsorVideoId = "";
	}

	private void scheduleSponsorCheck(long delayMillis, long generation, String videoId) {
		if (!isSponsorGeneration(generation, videoId) || sponsorSegments.isEmpty()) return;
		if (sponsorCheck != null) sponsorHandler.removeCallbacks(sponsorCheck);
		sponsorCheck = () -> {
			sponsorCheck = null;
			if (!isSponsorGeneration(generation, videoId)) return;
			if (!isYoutubePlaybackOwned(videoId)) {
				cancelSponsorBlock();
				return;
			}
			if (!isYoutubePlaybackActive(videoId)) {
				return;
			}
			getPosition().main().onSuccess(position -> {
				if (!isSponsorGeneration(generation, videoId) || sponsorSegments.isEmpty() ||
						!isYoutubePlaybackActive(videoId)) return;
				sponsorSegmentIndex = SponsorBlockSchedule.findSegmentIndex(sponsorSegments, position);
				if (sponsorSegmentIndex == sponsorSkippedSegmentIndex) {
					if (position >= Math.max(0L, sponsorSkippedTargetMillis - 500L)) {
						sponsorSegmentIndex++;
					} else {
						sponsorSkippedSegmentIndex = -1;
						sponsorSkippedTargetMillis = -1L;
					}
				} else if (sponsorSkippedSegmentIndex >= 0) {
					sponsorSkippedSegmentIndex = -1;
					sponsorSkippedTargetMillis = -1L;
				}
				if (sponsorSegmentIndex >= sponsorSegments.size()) {
					scheduleSponsorCheck(SponsorBlockSchedule.POST_SEGMENT_RESCAN_MS,
							generation, videoId);
					return;
				}
				SponsorBlockClient.Segment segment = sponsorSegments.get(sponsorSegmentIndex);
				long start = SponsorBlockSchedule.millis(segment.startSeconds());
				long end = SponsorBlockSchedule.millis(segment.endSeconds());
				long trigger = Math.max(0L, start - 250L);
				if (position >= trigger) {
					getDuration().main().onSuccess(duration -> {
						if (!isSponsorGeneration(generation, videoId) ||
								!isYoutubePlaybackActive(videoId)) return;
						if (duration <= 0L) {
							scheduleSponsorCheck(1000L, generation, videoId);
							return;
						}
						long clampedEnd = Math.min(end, duration);
						long target = Math.min(clampedEnd, Math.max(0L, duration - 250L));
						if ((clampedEnd > position) && (target > (position + 100L))) {
							YoutubePlaybackSession.Snapshot playback =
									mediaEngine.playbackSnapshot(videoId);
							if (playback == null) {
								scheduleSponsorCheck(250L, generation, videoId);
								return;
							}
							seekSponsorSegment(playback, videoId, target);
							sponsorSkippedSegmentIndex = sponsorSegmentIndex;
							sponsorSkippedTargetMillis = target;
						}
						sponsorSegmentIndex++;
						scheduleSponsorCheck(250L, generation, videoId);
					}).onFailure(error -> scheduleSponsorCheck(1000L, generation, videoId));
				} else {
					getSpeed().main().onSuccess(speed -> scheduleSponsorCheck(
							SponsorBlockSchedule.delayUntil(position, trigger, speed), generation, videoId))
							.onFailure(error -> scheduleSponsorCheck(
									SponsorBlockSchedule.delayUntil(position, trigger, 1f), generation, videoId));
				}
			}).onFailure(error -> scheduleSponsorCheck(1000L, generation, videoId));
		};
		sponsorHandler.postDelayed(sponsorCheck, Math.max(0L, delayMillis));
	}

	private boolean isSponsorGeneration(long generation, String videoId) {
		return (generation == sponsorGeneration) && videoId.equals(sponsorVideoId);
	}

	private boolean isYoutubePlaybackOwned(String videoId) {
		return (mediaEngine != null) && mediaEngine.ownsPlayback(videoId);
	}

	private boolean isYoutubePlaybackActive(String videoId) {
		return !sponsorPlaybackPaused && isYoutubePlaybackOwned(videoId) &&
				mediaEngine.isPlaybackActive();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
	}

	@Override
	public void destroy() {
		setFullscreenTapEnabled(false);
		if (mediaEngine != null) {
			getAddon().getRuntime().unregisterHost(this, mediaEngine);
			mediaEngine.onWebViewDestroyed();
		}
		cancelSponsorBlock();
		super.destroy();
	}

	private void configureAdSkip() {
		evaluateJavascript(PLAYBACK_SIGNAL_JS + AD_SKIP_JS +
				"window.__fermataAdState.configure(" + getAddon().skipAd() + ", " +
				YoutubeJsInterface.JS_AD_SIGNAL + ");", null);
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
		})(%s);""", enabled ? "true" : "false"), ignored -> {
			YoutubePlaybackDiagnostics.log(this, enabled ? "after_immersive_enable" : "after_immersive_disable");
			postDelayed(() -> YoutubePlaybackDiagnostics.log(this, enabled ? "settled_immersive_enable" : "settled_immersive_disable"), 500L);
		});
	}

	void play() {
		loadUrl("javascript:" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo(); if (v != null) v.play();");
	}

	/** Starts a user/media-session requested play audibly without overriding later mute changes. */
	void playAudible() {
		evaluateJavascript(PLAYBACK_SIGNAL_JS +
				"(function(){var v=fermataActiveContentVideo();if(!v)return false;" +
				"var p=document.querySelector('#movie_player');" +
				"try{if(p&&typeof p.unMute==='function')p.unMute();" +
				"if(v.volume<=0){v.volume=1;if(p&&typeof p.setVolume==='function')p.setVolume(100);}" +
				"v.muted=false;var started=v.play();" +
				"if(started&&started.catch)started.catch(function(){});return true;}catch(e){return false;}})()",
				null);
	}

	/** Quiets only the exact generation rejected by the native playback ownership gate. */
	void silenceRejectedPlayback(String signal) {
		YoutubePlaybackMetadata.Signal parsed = YoutubePlaybackMetadata.parse(signal, getUrl());
		if (parsed.videoId().isEmpty()) return;
		evaluateJavascript(PLAYBACK_SIGNAL_JS +
				"(function(expectedId,expectedGeneration){" +
				"if(fermataPageVideoId()!==expectedId||" +
				"Number(window.__fermataPlaybackGeneration||0)!==expectedGeneration)return false;" +
				"var v=fermataActiveContentVideo();if(!v)return false;" +
				"v.pause();v.muted=true;return true;})(" +
				JSONObject.quote(parsed.videoId()) + "," + Math.max(0L, parsed.generation()) + ")", null);
	}

	void pause() {
		loadUrl("javascript:" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo(); if (v != null) v.pause();");
	}

	void stop() {
		loadUrl("javascript:" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo();\n" +
				"if (v != null) { v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		prevNext(false);
	}

	void next() {
		prevNext(true);
	}

	private void prevNext(boolean next) {
		evaluateJavascript(YoutubeScripts.prevNext(next), null);
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getContentDuration() {
		Promise<Long> promise = new Promise<>();
		evaluateJavascript("(function(){" + PLAYBACK_SIGNAL_JS +
				"if(!fermataPlaybackIdentityMatchesPage())return 0;" +
				"var state=window.__fermataAdState;" +
				"var player=document.querySelector('#movie_player,.html5-video-player');" +
				"var showing=!!((state&&state.lastShowing)||" +
				"(player&&player.classList.contains('ad-showing')));" +
				"if(showing)return state?Number(state.contentDuration||0):0;" +
				"var v=fermataActiveContentVideo();if(!v)return 0;" +
				"var duration=Number(v.duration||0);" +
				"if(state&&duration>0){state.contentDuration=duration;" +
				"state.contentSource=v.currentSrc||v.src||'';}return duration;})()", value -> {
			try {
				promise.complete((long) (Double.parseDouble(value) * 1000d));
			} catch (NumberFormatException error) {
				Log.d(error);
				promise.complete(0L);
			}
		});
		return promise;
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	FutureSupplier<String> getVideoQualities() {
		Promise<String> p = js.getResultPromise();
		evaluateJavascript(YoutubeScripts.videoQualities(JS_EVENT, JS_VIDEO_QUALITIES,
				getContext().getString(me.aap.fermata.R.string.auto)), null);
		return p;
	}

	void setVideoQuality(int idx) {
		evaluateJavascript(YoutubeScripts.setVideoQuality(idx), null);
	}

	void setHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				YoutubeScripts.CLEAR_HIGHEST_VIDEO_QUALITY +
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
				YoutubeScripts.CLEAR_HIGHEST_VIDEO_QUALITY +
				"  clearFermataQ();\n" +
				"})();");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
			evaluateJavascript(
				"(function(){" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo(); return (v != null) ? v." + value +
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
		loadUrl("javascript:" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo(); if (v != null) v.currentTime = " +
				pos + ";");
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
			evaluateJavascript(
				"(function(){" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo(); return (v != null) ? v" +
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
		loadUrl("javascript:" + PLAYBACK_SIGNAL_JS + "var v = fermataActiveContentVideo(); if (v != null) v.playbackRate =" +
				" " +
				speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		evaluateJavascript("(function(){" + PLAYBACK_SIGNAL_JS +
				"return fermataVideoTitle();})()",
				value -> p.complete(YoutubeScripts.decodeJavascriptString(value)));
		return p;
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		evaluateJavascript(PLAYBACK_SIGNAL_JS +
				"var v = fermataActiveContentVideo(); if (v) v.style.objectFit = '" + p + "';", null);
	}
}
