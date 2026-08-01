package me.aap.fermata.addon.web.yt;

import org.json.JSONTokener;

final class YoutubeScripts {
	static final String PLAYBACK_SIGNAL = """
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
	static final String AD_SKIP = """
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
	static final String CLEAR_HIGHEST_VIDEO_QUALITY =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";

	static final String VOICE_RESULTS = """
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

	private YoutubeScripts() {
	}

	static String decodeJavascriptString(String value) {
		if ((value == null) || value.equals("null")) return "";
		try {
			Object decoded = new JSONTokener(value).nextValue();
			return (decoded instanceof String) ? (String) decoded : value;
		} catch (Exception ex) {
			return value;
		}
	}

	static String prevNext(boolean next) {
		String method = next ? "nextVideo" : "previousVideo";
		String selector = next ? ".ytp-next-button" : ".ytp-prev-button";
		int mobileButtonIndex = next ? 1 : 0;
		return """
				(function() {
				  function available(button) {
				    return button && !button.disabled &&
				        button.getAttribute('aria-disabled') !== 'true' &&
				        !button.classList.contains('icon-disable');
				  }
				  function move() {
				    var mobile = document.querySelectorAll(
				        'button.player-middle-controls-prev-next-button');
				    var button = mobile.length > %3$d ? mobile[%3$d] :
				        document.querySelector('%2$s');
				    if (available(button)) {
				      button.click();
				      return true;
				    }
				    var player = document.querySelector('#movie_player,.html5-video-player');
				    if (player && typeof player.%1$s === 'function') {
				      player.%1$s();
				      return true;
				    }
				    return false;
				  }
				  if (!move()) setTimeout(move, 600);
				})()
				""".formatted(method, selector, mobileButtonIndex);
	}

	static String videoQualities(String eventFunction, int eventCode, String autoLabel) {
		return """
				(function() {
				  function player() {
				    return document.querySelector('#movie_player,.html5-video-player');
				  }
				  function label(level) {
				    var names = {
				      highres: 'Highest', hd4320: '4320p (8K)', hd2880: '2880p',
				      hd2160: '2160p (4K)', hd1440: '1440p', hd1080: '1080p',
				      hd720: '720p', large: '480p', medium: '360p',
				      small: '240p', tiny: '144p', auto: %3$s
				    };
				    if (names[level]) return names[level];
				    var match = /^hd(\\d+)$/.exec(level);
				    return match ? match[1] + 'p' : level;
				  }
				  function finish(result) {
				    try { %1$s(%2$d, result); } catch (ignore) {}
				  }
				  function read(attempt) {
				    var p = player();
				    var levels = [];
				    try {
				      if (p && typeof p.getAvailableQualityLevels === 'function')
				        levels = p.getAvailableQualityLevels() || [];
				    } catch (ignore) {}
				    if (!p || !levels.length) {
				      if (attempt < 12) setTimeout(function() { read(attempt + 1); }, 100);
				      else finish(null);
				      return;
				    }
				    var unique = [];
				    for (var i = 0; i < levels.length; i++) {
				      var level = String(levels[i] || '');
				      if (level && unique.indexOf(level) < 0) unique.push(level);
				    }
				    window.__fermataQualityLevels = unique;
				    var current = '';
				    try {
				      if (typeof p.getPlaybackQuality === 'function')
				        current = String(p.getPlaybackQuality() || '');
				    } catch (ignore) {}
				    var result = [];
				    for (var j = 0; j < unique.length; j++)
				      result.push((unique[j] === current ? '*' : '') + label(unique[j]));
				    finish(result.join(';'));
				  }
				  read(0);
				})()
				""".formatted(eventFunction, eventCode, org.json.JSONObject.quote(autoLabel));
	}

	static String setVideoQuality(int index) {
		return """
				(function(index) {
				  var p = document.querySelector('#movie_player,.html5-video-player');
				  if (!p) return false;
				  var levels = window.__fermataQualityLevels;
				  if (!levels || index < 0 || index >= levels.length) {
				    try {
				      levels = (typeof p.getAvailableQualityLevels === 'function') ?
				          (p.getAvailableQualityLevels() || []) : [];
				    } catch (ignore) { levels = []; }
				  }
				  var level = levels[index];
				  if (!level) return false;
				  try {
				    if (typeof p.setPlaybackQualityRange === 'function')
				      p.setPlaybackQualityRange(level, level);
				    if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(level);
				    return true;
				  } catch (ignore) { return false; }
				})(%d)
				""".formatted(index);
	}
}
