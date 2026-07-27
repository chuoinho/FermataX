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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

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
	private static final String PLAYBACK_SIGNAL_JS = """
			function fermataActiveContentVideo() {
			  var player = document.querySelector('#movie_player');
			  if (player) {
			    var candidates = player.querySelectorAll('video');
			    var fallback = null;
			    for (var j = 0; j < candidates.length; j++) {
			      var candidate = candidates[j];
			      if (!candidate.isConnected || candidate.closest('.video-ads') ||
			          candidate.closest('.ytp-ad-player-overlay')) continue;
			      if (candidate.classList.contains('html5-main-video') &&
			          !candidate.paused && !candidate.ended && candidate.readyState > 0) return candidate;
			      if (!fallback && candidate.classList.contains('html5-main-video')) fallback = candidate;
			      if (!fallback && candidate.currentSrc) fallback = candidate;
			    }
			    if (fallback) return fallback;
			  }
			  var videos = document.querySelectorAll('video.html5-main-video');
			  for (var i = 0; i < videos.length; i++) {
			    if (videos[i].isConnected && !videos[i].closest('.video-ads') &&
			        !videos[i].closest('.ytp-ad-player-overlay') &&
			        videos[i].classList.contains('html5-main-video'))
			      return videos[i];
			  }
			  return null;
			}
			function fermataPageVideoId() {
			  try {
			    var url = new URL(location.href);
			    if (url.pathname === '/watch') return url.searchParams.get('v') || '';
			    if (url.pathname.startsWith('/shorts/'))
			      return url.pathname.substring('/shorts/'.length).split('/')[0] || '';
			  } catch (err) {}
			  return '';
			}
			function fermataPlayerVideoId() {
			  var player = document.querySelector('#movie_player') ||
			      document.querySelector('.html5-video-player');
			  try {
			    if (player && typeof player.getVideoData === 'function') {
			      var data = player.getVideoData();
			      return (data && data.video_id) || '';
			    }
			  } catch (err) {}
			  return '';
			}
			function fermataPlaybackIdentityMatchesPage() {
			  var page = fermataPageVideoId();
			  var player = fermataPlayerVideoId();
			  return !!page && !!player && page === player;
			}
			function fermataVideoTitle() {
			  var player = document.querySelector('#movie_player') ||
			      document.querySelector('.html5-video-player');
			  try {
			    if (player && typeof player.getVideoData === 'function') {
			      var data = player.getVideoData();
			      var pageVideoId = fermataPageVideoId();
			      if (pageVideoId && data && data.video_id && data.video_id !== pageVideoId) return '';
			      if (data && data.title) return data.title;
			    }
			  } catch (err) {}
			  var selectors = [
			    'h1.ytd-watch-metadata yt-formatted-string',
			    'h1.title yt-formatted-string',
			    'ytm-slim-video-metadata-section-renderer h1',
			    'ytm-video-description-header-renderer h1'
			  ];
			  for (var i = 0; i < selectors.length; i++) {
			    var element = document.querySelector(selectors[i]);
			    if (element && element.textContent && element.textContent.trim()) {
			      return element.textContent.trim();
			    }
			  }
			  var meta = document.querySelector('meta[property="og:title"], meta[name="title"]');
			  if (meta && meta.content) return meta.content;
			  return document.title || '';
			}
			function fermataVideoSignal(video) {
			  if (!fermataPlaybackIdentityMatchesPage()) return '';
			  var page = location.href || '';
			  var media = (video && (video.currentSrc || video.src)) || page;
			  return 'ytv2|' + encodeURIComponent(page) + '|' +
			      encodeURIComponent(media) + '|' + encodeURIComponent(fermataVideoTitle()) + '|' +
			      String((video && video.__fermataGeneration) || 0) + '|' +
			      encodeURIComponent(fermataPlayerVideoId()) + '|' +
			      ((video && video.muted) ? '1' : '0') + '|' +
			      String((video && Number.isFinite(video.volume)) ? video.volume : -1);
			}
			""";
	private static final String AD_SKIP_JS = """
				(function() {
				  const state = window.__fermataAdState || (window.__fermataAdState = {
				    enabled: false, skipEnabled: false, eventCode: 0,
				    observer: null, timer: null, watchdog: null, lastAttempt: 0, adKey: '', adId: '', adNode: null,
				    lastAdTime: -1, adSequence: 0, podSequence: 0, podKey: '', attempts: 0,
				    podAttempts: 0,
				    lastShowing: false, lastPhase: '',
				    suppressEndedUntil: 0, suppressEndedKey: '', failureEmitted: false,
				    contentSource: '', contentDuration: 0, observedRoot: null, tickPending: false
				  });
				  function activeVideo() { return fermataActiveContentVideo(); }
				  function visible(node) {
				    if (!node) return false;
				    const style = window.getComputedStyle(node);
				    return style.display !== 'none' && style.visibility !== 'hidden' &&
				      node.getBoundingClientRect().width > 0 && node.getBoundingClientRect().height > 0;
				  }
				  function adShowing() {
				    var player = document.querySelector('.html5-video-player, #movie_player');
				    if (player && player.classList.contains('ad-showing')) return true;
				    var markers = document.querySelectorAll(
				      '.video-ads, .ytp-ad-player-overlay, .ytp-ad-module, .ytp-ad-preview-container,' +
				      '.ytp-ad-progress-list, .ytp-ad-message-container, .ytp-ad-overlay-container');
				    for (var i = 0; i < markers.length; i++) if (visible(markers[i])) return true;
				    return false;
				  }
				  function adVideo() {
				    const nodes = document.querySelectorAll(
				      '.video-ads video, .ytp-ad-player-overlay video, .ytp-ad-preview-container video,' +
				      '.html5-video-player.ad-showing video, #movie_player.ad-showing video,' +
				      'video.html5-ad-video, video.ad-showing');
				    for (var i = 0; i < nodes.length; i++) if (visible(nodes[i])) return nodes[i];
				    var content = activeVideo();
				    var videos = document.querySelectorAll('video');
				    for (var j = 0; j < videos.length; j++) {
				      var video = videos[j];
				      if (!visible(video) || video === content) continue;
				      if (video.closest('.video-ads, .ytp-ad-player-overlay, .ytp-ad-preview-container') ||
				          video.classList.contains('html5-ad-video') || video.classList.contains('ad-showing'))
				        return video;
				    }
				    return null;
				  }
				  function skipButton() {
				    const selector =
				      '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton,' +
				      '.ytp-ad-skip-button-slot, .ytp-ad-skip-button-slot button,' +
				      '.ytp-ad-skip-button-container button, button.ytp-skip-ad-button,' +
				      '[id^="skip-button"], [id^="skip-button"] button';
				    const direct = document.querySelector(selector);
				    if (visible(direct)) return direct;
				    const candidates = document.querySelectorAll('button, [role="button"]');
				    for (var i = 0; i < candidates.length; i++) {
				      var candidate = candidates[i];
				      if (!visible(candidate)) continue;
				      var label = ((candidate.getAttribute('aria-label') || '') + ' ' +
				          (candidate.textContent || '')).replace(/\\s+/g, ' ').trim().toLowerCase();
				      if (label === 'skip' || label.indexOf('skip ad') >= 0 ||
				          label.indexOf('skip ads') >= 0) return candidate;
				    }
				    return null;
				  }
				  function adTarget() {
				    const dedicated = adVideo();
				    if (dedicated) return dedicated;
				    const fallback = activeVideo();
				    if (!fallback) return null;
				    const source = fallback.currentSrc || fallback.src || '';
				    const duration = Number(fallback.duration || 0);
				    if (state.contentSource && source === state.contentSource &&
				        Number.isFinite(duration) && Number.isFinite(state.contentDuration) &&
				        Math.abs(duration - state.contentDuration) < 0.5) return null;
				    return fallback;
				  }
				  function adKey(video) {
				    const source = video && (video.currentSrc || video.src) || location.href;
				    return source + ':' + Math.floor((video && video.duration || 0) * 10);
				  }
			  function emit(phase, pod, ad) {
			    if (!state.eventCode) return;
				    var generation = (activeVideo() && activeVideo().__fermataGeneration) ||
				      window.__fermataPlaybackGeneration || 0;
				    var key = String(generation) + '|' + phase + '|' + (pod || '') + '|' +
				      (ad || '') + '|' + (location.href || '');
				    if (state.lastPhase === key) return;
				    state.lastPhase = key;
				    try { window.Fermata.event(state.eventCode, 'ytad1|' +
				      encodeURIComponent(phase) + '|' + encodeURIComponent(pod || '') + '|' +
				      encodeURIComponent(ad || '') + '|' + encodeURIComponent(location.href || '') + '|' +
				      String(generation)); }
			    catch (err) {}
			  }
				  function attempt() {
				    if (!state.skipEnabled || !adShowing()) return false;
				    const now = Date.now();
				    if ((now - state.lastAttempt) < 350) return true;
				    const skip = skipButton();
				    if (visible(skip) && state.attempts < 2) {
				      state.lastAttempt = now;
				      state.attempts++;
				      state.podAttempts++;
				      skip.click();
				      state.suppressEndedUntil = now + 1800;
				      state.suppressEndedKey = state.adKey;
				      return true;
				    }
				    const video = adTarget();
				    if (!video) {
				      if ((now - state.lastAttempt) >= 350) {
				        state.lastAttempt = now;
				        state.attempts++;
				        state.podAttempts++;
				      }
				      if ((state.attempts >= 12) || (state.podAttempts >= 36)) {
				        if (!state.failureEmitted && state.podKey && state.adId) {
				          state.failureEmitted = true;
				          emit('ad-error', state.podKey, state.adId);
				        }
				      }
				      return true;
				    }
				    const key = adKey(video);
				    if (key !== state.adKey) {
				      state.adKey = key;
				      state.attempts = 0;
				      state.failureEmitted = false;
				    }
				    if ((state.attempts >= 12) || (state.podAttempts >= 36)) {
				      if (!state.failureEmitted && state.podKey && state.adId) {
				        state.failureEmitted = true;
				        emit('ad-error', state.podKey, state.adId);
				      }
				      return true;
				    }
				    const ad = adTarget();
				    if (adShowing() && ad && Number.isFinite(ad.duration) && ad.duration > 0) {
				      state.lastAttempt = now;
				      state.attempts++;
				      state.podAttempts++;
				      state.suppressEndedUntil = now + 1800;
				      state.suppressEndedKey = key;
				      try { ad.currentTime = Math.max(0, ad.duration - 0.15); }
				      catch (err) {}
				      return true;
				    }
				    return true;
				  }
				  function tick() {
				    state.tickPending = false;
				    if (!state.enabled) return;
				    const showing = adShowing();
				    if (showing) {
				      const currentAd = adTarget();
				      const key = adKey(currentAd);
				      const currentTime = currentAd ? Number(currentAd.currentTime || 0) : -1;
				      const restarted = (currentAd === state.adNode) && (key === state.adKey) &&
				        (currentTime >= 0) && (state.lastAdTime >= 0) &&
				        ((currentTime + 0.75) < state.lastAdTime);
				      const changed = (currentAd !== state.adNode) || (key !== state.adKey) || restarted;
				      if (!state.lastShowing) {
				        state.lastShowing = true;
				        state.adNode = currentAd;
				        state.lastAdTime = currentTime;
				        state.podKey = (location.href || key) + '#pod-' + (++state.podSequence);
				        state.adKey = key;
				        state.adId = key + '#ad-' + (++state.adSequence);
				        state.attempts = 0;
				        state.podAttempts = 0;
				        state.failureEmitted = false;
				        emit('pod-start', state.podKey, '');
				        emit('ad-start', state.podKey, state.adId);
				      } else if (changed) {
				        emit('ad-complete', state.podKey, state.adId);
				        state.adNode = currentAd;
				        state.lastAdTime = currentTime;
				        state.adKey = key;
				        state.adId = key + '#ad-' + (++state.adSequence);
				        state.attempts = 0;
				        state.failureEmitted = false;
				        emit('ad-start', state.podKey, state.adId);
				      }
				      state.lastAdTime = currentTime;
				      attempt();
				      if (state.timer == null) state.timer = setInterval(tick, 250);
				    } else if (state.timer != null) {
				      if (state.lastShowing) {
				        emit('ad-complete', state.podKey, state.adId);
				        emit('pod-complete', state.podKey, '');
				        state.lastShowing = false;
				        state.podKey = '';
				      }
				      clearInterval(state.timer);
				        state.timer = null;
				        state.attempts = 0;
				        state.failureEmitted = false;
				        state.adKey = '';
				        state.adId = '';
				        state.adNode = null;
				        state.lastAdTime = -1;
				        state.podAttempts = 0;
				      }
				    const video = activeVideo();
				    if (!showing && video) {
				      state.contentSource = video.currentSrc || video.src || '';
				      state.contentDuration = Number(video.duration || 0);
				      if (!video.paused && !video.ended) emit('content', '', '');
				    }
				  }
				  function scheduleTick() {
				    if (state.tickPending) return;
				    state.tickPending = true;
				    requestAnimationFrame(tick);
				  }
				  function bindObserver() {
				    if (state.observer) state.observer.disconnect();
				    const player = document.querySelector('#movie_player, .html5-video-player');
				    const root = player || document.documentElement;
				    state.observedRoot = player || null;
				    state.observer = new MutationObserver(function() {
				      const nextPlayer = document.querySelector('#movie_player, .html5-video-player');
				      if (nextPlayer && nextPlayer !== state.observedRoot) bindObserver();
				      scheduleTick();
				    });
				    if (root) state.observer.observe(root, player ?
				      {childList: true, subtree: true, attributes: true,
				       attributeFilter: ['class', 'style']} :
				      {childList: true, subtree: true});
				  }
				  state.configure = function(skipEnabled, eventCode) {
				    state.enabled = true;
				    state.skipEnabled = !!skipEnabled;
				    state.eventCode = eventCode || 0;
				    if (state.observer) state.observer.disconnect();
				    if (state.timer != null) clearInterval(state.timer);
				    if (state.watchdog != null) clearInterval(state.watchdog);
				    state.observer = null;
				    state.timer = null;
				    state.attempts = 0;
				    state.lastAttempt = 0;
				    state.suppressEndedUntil = 0;
				    state.suppressEndedKey = '';
				    state.failureEmitted = false;
				    state.contentSource = '';
				    state.contentDuration = 0;
				    state.adKey = '';
				    state.adId = '';
				    state.adNode = null;
				    state.lastAdTime = -1;
				    state.adSequence = 0;
				    state.podSequence = 0;
				    state.podAttempts = 0;
				    state.lastShowing = false;
				    state.podKey = '';
				    state.lastPhase = '';
				    state.tickPending = false;
				    state.skipNow = attempt;
				    state.retryAd = function() {
				      state.attempts = 0;
				      state.lastAttempt = 0;
				      state.failureEmitted = false;
				      if (state.lastShowing && state.podKey && state.adId)
				        emit('ad-start', state.podKey, state.adId);
				      attempt();
				    };
				    state.rebindGeneration = function() {
				      state.lastPhase = '';
				      if (state.lastShowing && state.podKey && state.adId) {
				        emit('pod-start', state.podKey, '');
				        emit('ad-start', state.podKey, state.adId);
				      } else tick();
				    };
				    bindObserver();
				    state.watchdog = setInterval(function() {
				      var nextPlayer = document.querySelector('#movie_player, .html5-video-player');
				      if ((nextPlayer && nextPlayer !== state.observedRoot) ||
				          (!nextPlayer && state.observedRoot)) bindObserver();
				      tick();
				    }, 500);
				    tick();
				  };
				})();
			""";
	private static final String CLEAR_HIGHEST_VIDEO_QUALITY_JS =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";
	private static final long MANUAL_FULLSCREEN_GESTURE_WINDOW_MS = 1_500L;
	private YoutubeJsInterface js;
	private YoutubeMediaEngine mediaEngine;
	private float tapX;
	private float tapY;
	private long tapTime;
	private boolean pendingVoiceSearch;
	private boolean voiceSearchCollecting;
	private int voiceSearchGeneration;
	private boolean initialPlaybackNavigationClaimed;
	private boolean suppressSessionExpiryOnce;
	private boolean clearHistoryOnNextPageCommit;
	private boolean fullscreenTapEnabled;
	private int reloadAudioGeneration;
	private boolean reloadAudioRestorePending;
	private boolean reloadAudioPageCommitted;
	private String reloadAudioVideoId = "";
	private double reloadAudioVolume = 1d;
	private int autoNextAudioGeneration;
	private boolean autoNextAudioRestorePending;
	private String autoNextAudioSourceId = "";
	private String autoNextAudioTargetId = "";
	private double autoNextAudioVolume = 1d;
	private static final String VOICE_RESULTS_JS = """
			(function() {
			  const rows = document.querySelectorAll(
			      'ytd-video-renderer, ytm-video-with-context-renderer');
			  const seen = {};
			  const result = [];
			  for (const row of rows) {
			    try {
			      const titleElement = row.querySelector(
			          'a#video-title, #video-title, h3.media-item-headline, .media-item-headline');
			      const link = (titleElement && titleElement.closest('a[href*="/watch?v="]')) ||
			          row.querySelector('a[href*="/watch?v="]');
			      if (!link) continue;
			      const u = new URL(link.href, location.href);
			      const id = u.searchParams.get('v');
			      if (!id || seen[id]) continue;
			      let title = link.getAttribute('title') ||
			          (titleElement && titleElement.textContent) || '';
			      title = title.replace(/\\s+/g, ' ').trim();
			      if (!title) continue;
			      const channelElement = row.querySelector(
			          '#channel-name a, #channel-name, .byline, ' +
			          'ytm-badge-and-byline-renderer [class*="ItemByline"]');
			      const channel = (channelElement && channelElement.textContent) || '';
			      seen[id] = true;
			      result.push({id: id, title: title, channel: channel.replace(/\\s+/g, ' ').trim()});
			      if (result.length >= 3) break;
			    } catch (ignore) {}
			  }
			  return JSON.stringify(result);
			})()
			""";

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
		mediaEngine = new YoutubeMediaEngine(this, a);
		return js = new YoutubeJsInterface(this, mediaEngine);
	}

	@Override
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
			FermataChromeClient chromeClient) {
		super.init(addon, webClient, chromeClient);
		initialPlaybackNavigationClaimed = false;
		MediaSessionCallback callback = MainActivityDelegate.get(getContext())
				.getMediaSessionCallback();
		if ((callback.getEngine() instanceof YoutubeDeferredMediaEngine pending) &&
				pending.belongsTo(getAddon()))
			initialPlaybackNavigationClaimed = pending.attach(mediaEngine);
		else if ((callback.getEngine() instanceof YoutubeMediaEngine previous) &&
				(previous != mediaEngine) && previous.belongsTo(getAddon()))
			previous.transferTo(mediaEngine);
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

	@Override
	public void reload() {
		if ((mediaEngine == null) || !mediaEngine.isPlaybackActive()) {
			cancelReloadAudioRestore();
			super.reload();
			return;
		}

		int generation = ++reloadAudioGeneration;
		reloadAudioRestorePending = false;
		reloadAudioPageCommitted = false;
		evaluateJavascript("(function(){" + PLAYBACK_SIGNAL_JS +
				"var v=fermataActiveContentVideo();" +
				"if(!v)return '{}';" +
				"return JSON.stringify({id:fermataPageVideoId(),playing:!v.paused&&!v.ended," +
				"audible:!v.muted&&v.volume>0,volume:v.volume});})()", value -> {
			if (generation != reloadAudioGeneration) return;
			try {
				JSONObject state = new JSONObject(decodeJavascriptString(value));
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
			reloadPage();
		});
	}

	private void reloadPage() {
		super.reload();
	}

	void resetToHome() {
		cancelReloadAudioRestore();
		cancelAutoNextAudioRestore();
		leavePlaybackPresentation();
		clearHistoryOnNextPageCommit = true;
		getAddon().setLastYoutubeUrl(HOME_URL);
		stopLoading();
		loadUrl(HOME_URL);
	}

	void prepareVoiceSearch() {
		voiceSearchGeneration++;
		pendingVoiceSearch = true;
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
		evaluateJavascript(VOICE_RESULTS_JS, value -> {
			if (!isCurrentVoiceSearch(generation)) return;
			try {
				JSONArray array = new JSONArray(decodeJavascriptString(value));
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
					finishVoiceSearch(generation);
					MainActivityDelegate.get(getContext()).beginVoiceSelectionOptions(options);
					return;
				}
			} catch (Exception err) {
				Log.d(err, "Failed to parse YouTube voice results");
			}
			if (attempt + 1 >= VOICE_RESULTS_MAX_ATTEMPTS) {
				finishVoiceSearch(generation);
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
		armExplicitPlayback();
		loadUrl("https://www.youtube.com/watch?v=" + videoId);
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
		if (cb.getEngine() instanceof YoutubeMediaEngine) {
			suppressSessionExpiryOnce = true;
			cb.onStop();
		}
		super.goBack();
	}

	boolean consumeSessionExpirySuppression() {
		boolean suppress = suppressSessionExpiryOnce;
		suppressSessionExpiryOnce = false;
		return suppress;
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

	boolean acceptsBrowserFullScreen(long request) {
		return (js != null) && js.acceptsBrowserFullScreen(request);
	}

	boolean enterManualFullScreen() {
		return (js != null) && js.enterManualAppFullScreen();
	}

	void setFullscreenTapEnabled(boolean enabled) {
		enabled = BuildConfig.AUTO && enabled;
		if (fullscreenTapEnabled == enabled) return;
		fullscreenTapEnabled = enabled;
		evaluateJavascript("window.__fermataFullscreenTapEnabled = " + enabled + ";", null);
	}

	void onBrowserFullScreenRejected() {
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

		if (BuildConfig.AUTO) js.onPlaybackGesture(event.getEventTime());
		long permit = js.grantManualFullScreenEntry();
		if (permit != YoutubeFullscreenGate.NO_REQUEST) {
			postDelayed(() -> {
				if (js != null) js.expireManualFullScreenEntry(permit);
			}, MANUAL_FULLSCREEN_GESTURE_WINDOW_MS);
		}

		if (!BuildConfig.AUTO) return;
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		if (activity.isVideoMode() && !activity.getBody().isBothMode() &&
				(activity.getMediaSessionCallback().getEngine() == mediaEngine)) {
			// JavaScript may report the same tap; the engine owns the debounce.
			js.getEngine().touched();
		}
	}

	@Override
	protected void pageLoaded(String uri) {
		getAddon().setLastYoutubeUrl(uri);
		if (clearHistoryOnNextPageCommit) {
			clearHistoryOnNextPageCommit = false;
			post(this::clearHistory);
		}
		attachListeners((mediaEngine == null) ? 0L : mediaEngine.playbackGenerationSeed());
		if (mediaEngine != null) mediaEngine.onPageLoaded(uri);
		injectSponsorBlock();
		configureAdSkip();
		addFocusHighlight();
		flushCookiesSoon();
		if (reloadAudioRestorePending) {
			reloadAudioPageCommitted = true;
			restoreReloadAudio(0, reloadAudioGeneration);
		}
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

	private void attachListeners(long seedGeneration) {
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
				  window.__fermataFullscreenTapEnabled = %b;
				  function event(code, data) {
				    try { %s(code, data); } catch (err) {}
				  }
				  %s
				  function isVideoPage() {
				    return location.pathname === '/watch' || location.pathname.startsWith('/shorts/');
				  }
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
				      setTimeout(function() { scheduleScan(true); }, 250);
				      setTimeout(function() { retryTitle(location.href, 0); }, 250);
				    }
				  }, 750);
				})();""", scale, Math.max(0L, seedGeneration), fullscreenTapEnabled,
				JS_EVENT, PLAYBACK_SIGNAL_JS,
				JS_VIDEO_PLAYING, JS_VIDEO_READY, debug, JS_VIDEO_PLAYING,
				JS_VIDEO_PAUSED, JS_VIDEO_ENDED, JS_VIDEO_FULLSCREEN_TAP,
				JS_VIDEO_TOUCHED, JS_VIDEO_TOUCHED), null);
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
		clearReloadAudioRestore(reloadAudioGeneration);
	}

	void prepareAutoNextAudioRestore(YoutubePlaybackMetadata.Signal signal) {
		cancelAutoNextAudioRestore();
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
		if (mediaEngine != null) mediaEngine.onWebViewDestroyed();
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
				})(%s);""", enabled ? "true" : "false"), null);
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
				value -> p.complete(decodeJavascriptString(value)));
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
		evaluateJavascript(PLAYBACK_SIGNAL_JS +
				"var v = fermataActiveContentVideo(); if (v) v.style.objectFit = '" + p + "';", null);
	}
}
