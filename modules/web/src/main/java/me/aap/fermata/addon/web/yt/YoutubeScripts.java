package me.aap.fermata.addon.web.yt;

import java.util.Locale;

import org.json.JSONTokener;

final class YoutubeScripts {
	static final String PREFER_H264 = """
			(function() {
			  if (window.__fermataH264) return;
			  window.__fermataH264 = true;
			  var BLOCKED = /webm|vp8|vp9|av01|av1/i;
			  try {
			    if (window.MediaSource && MediaSource.isTypeSupported) {
			      var origIsTypeSupported = MediaSource.isTypeSupported.bind(MediaSource);
			      MediaSource.isTypeSupported = function(type) {
			        if (typeof type === 'string' && BLOCKED.test(type)) return false;
			        return origIsTypeSupported(type);
			      };
			    }
			  } catch (e) {}
			  try {
			    var proto = HTMLMediaElement.prototype;
			    var origCanPlayType = proto.canPlayType;
			    proto.canPlayType = function(type) {
			      if (typeof type === 'string' && BLOCKED.test(type)) return '';
			      return origCanPlayType.call(this, type);
			    };
			  } catch (e) {}
			})();
			""";

	static final String ADBLOCK_CSS = """
			(function() {
			  var cssId = 'fermata-adblock-css';
			  if (document.getElementById(cssId)) return;
			  var css = '.video-ads, .ytp-ad-player-overlay, ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, ' +
			      'ytm-compact-promoted-video-renderer, ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer, ' +
			      'ytm-companion-slot, #player-ads, #masthead-ad, .ytp-ad-module, .ytp-ad-overlay-container, ' +
			      'ad-slot-renderer, ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model, ' +
			      '[class*="ad-showing"] .ad-interrupting { display: none !important; } ' +
			      '.html5-video-player.ad-showing video, video.ad-showing, .ad-showing .html5-main-video { opacity: 0 !important; }';
			  function inject() {
			    if (document.getElementById(cssId)) return;
			    var head = document.head || document.documentElement;
			    if (!head) return;
			    var s = document.createElement('style');
			    s.id = cssId;
			    s.textContent = css;
			    head.appendChild(s);
			  }
			  inject();
			  if (document.readyState === 'loading') {
			    document.addEventListener('DOMContentLoaded', inject);
			  }
			})();
			""";

	static final String NETWORK_ADBLOCK = """
			(function() {
			  if (window.__fermataNetworkAdblock) return;
			  window.__fermataNetworkAdblock = true;
			  var AD_FIELDS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adParams'];
			  var AD_RENDERERS = [
			    'adSlotRenderer', 'displayAdRenderer', 'promotedVideoRenderer',
			    'compactPromotedVideoRenderer', 'promotedSparklesWebRenderer',
			    'promotedSparklesTextSearchRenderer', 'inFeedAdLayoutRenderer',
			    'adsEngagementPanelRenderer', 'playerLegacyDesktopWatchAdsRenderer',
			    'searchPyvRenderer', 'bannerPromoRenderer', 'statementBannerRenderer'
			  ];
			  function isAdItem(node) {
			    if (!node || typeof node !== 'object') return false;
			    for (var i = 0; i < AD_RENDERERS.length; i++) {
			      if (Object.prototype.hasOwnProperty.call(node, AD_RENDERERS[i])) return true;
			    }
			    return false;
			  }
			  function strip(node, depth) {
			    if (depth > 30 || !node || typeof node !== 'object') return node;
			    if (Array.isArray(node)) {
			      var out = [];
			      for (var i = 0; i < node.length; i++) {
			        if (!isAdItem(node[i])) out.push(strip(node[i], depth + 1));
			      }
			      return out;
			    }
			    for (var f = 0; f < AD_FIELDS.length; f++) {
			      if (Object.prototype.hasOwnProperty.call(node, AD_FIELDS[f])) delete node[AD_FIELDS[f]];
			    }
			    for (var k in node) {
			      if (Object.prototype.hasOwnProperty.call(node, k)) node[k] = strip(node[k], depth + 1);
			    }
			    return node;
			  }
			  function cleanBody(text) {
			    if (typeof text !== 'string') return text;
			    var hit = false;
			    for (var i = 0; i < AD_FIELDS.length && !hit; i++) {
			      if (text.indexOf('"' + AD_FIELDS[i] + '"') !== -1) hit = true;
			    }
			    for (var j = 0; j < AD_RENDERERS.length && !hit; j++) {
			      if (text.indexOf('"' + AD_RENDERERS[j] + '"') !== -1) hit = true;
			    }
			    if (!hit) return text;
			    try { return JSON.stringify(strip(JSON.parse(text), 0)); }
			    catch (e) { return text; }
			  }
			  function guardGlobal(name) {
			    var stored;
			    try {
			      if (Object.prototype.hasOwnProperty.call(window, name)) {
			        stored = strip(window[name], 0);
			      }
			      Object.defineProperty(window, name, {
			        configurable: true, enumerable: true,
			        get: function() { return stored; },
			        set: function(v) { stored = (v && typeof v === 'object') ? strip(v, 0) : v; }
			      });
			    } catch (e) {
			      try { window[name] = strip(window[name], 0); } catch (e2) {}
			    }
			  }
			  guardGlobal('ytInitialPlayerResponse');
			  var API = /\\/youtubei\\/v1\\/(player|get_watch)/;
			  var nativeFetch = window.fetch;
			  if (nativeFetch) {
			    window.fetch = function() {
			      var args = arguments;
			      var url = '';
			      try {
			        var a0 = args[0];
			        url = (a0 && a0.url) ? a0.url : String(a0);
			      } catch (e) { url = ''; }
			      var p = nativeFetch.apply(this, args);
			      if (!API.test(url)) return p;
			      return p.then(function(res) {
			        if (!res.ok) return res;
			        return res.clone().text().then(function(body) {
			          var cleaned = cleanBody(body);
			          if (cleaned === body) return res;
			          return new Response(cleaned, {
			            status: res.status,
			            statusText: res.statusText,
			            headers: res.headers
			          });
			        }).catch(function() { return res; });
			      });
			    };
			  }
			  try {
			    var open = XMLHttpRequest.prototype.open;
			    XMLHttpRequest.prototype.open = function(method, url) {
			      this.__fermataUrl = url;
			      return open.apply(this, arguments);
			    };
			    var send = XMLHttpRequest.prototype.send;
			    XMLHttpRequest.prototype.send = function() {
			      var xhr = this;
			      if (xhr.__fermataUrl && API.test(String(xhr.__fermataUrl))) {
			        xhr.addEventListener('readystatechange', function() {
			          if (xhr.readyState !== 4) return;
			          try {
			            if (typeof xhr.responseText !== 'string') return;
			            var cleaned = cleanBody(xhr.responseText);
			            if (cleaned !== xhr.responseText) {
			              Object.defineProperty(xhr, 'responseText', { value: cleaned, configurable: true });
			              Object.defineProperty(xhr, 'response', { value: cleaned, configurable: true });
			            }
          } catch (e) {}
        });
      }
      return send.apply(this, arguments);
    };
  } catch (e) {}
})();
""";

	static final String NONSTOP = """
			(function() {
			  if (window.__fermataNonstop) return;
			  window.__fermataNonstop = true;
			  var TOUCH_GRACE_MS = 5000;
			  var lastTouch = 0;
			  function touched() { lastTouch = Date.now(); }
			  ['pointerdown', 'mousedown', 'touchstart', 'keydown'].forEach(function(e) {
			    document.addEventListener(e, touched, true);
			  });
			  function userIsActing() { return (Date.now() - lastTouch) < TOUCH_GRACE_MS; }
			  function guard(v) {
			    if (!v || v.__fermataRealPause) return;
			    v.__fermataRealPause = v.pause.bind(v);
			    v.pause = function() {
			      if (window.__fermataPauseOk) return v.__fermataRealPause();
			      if (userIsActing()) return v.__fermataRealPause();
			      return undefined;
			    };
			  }
			  function isYouThereDialog(dlg) {
			    if (!dlg) return false;
			    var tag = (dlg.tagName || '').toLowerCase();
			    if (tag.indexOf('you-there') !== -1) return true;
			    var text = (dlg.textContent || '').toLowerCase();
			    if (/delete|remove|xóa|unsubscribe|hủy đăng ký|hủy|report|báo cáo|block|chặn|discard|clear history/.test(text)) {
			      return false;
			    }
			    if (/continue watching|still watching|you there|video paused|tiếp tục xem|đang xem|bạn có đang xem|bạn vẫn đang xem|video đã tạm dừng|continuar viendo|poursuivre la lecture|weiter ansehen|続きを視聴|계속 시청|继续观看/.test(text)) {
			      return true;
			    }
			    if (dlg.querySelector('[dialog-action*="you_there"], [dialog-action*="you-there"], [data-type*="you-there"]')) {
			      return true;
			    }
			    return false;
			  }
			  function dismiss() {
			    var dlg = document.querySelector('yt-confirm-dialog-renderer, ytm-confirm-dialog-renderer, ytmusic-you-there-renderer, ytm-you-there-renderer');
			    if (!dlg || !isYouThereDialog(dlg)) return false;
			    var btn = dlg.querySelector('#confirm-button, .yt-spec-button-shape-next, tp-yt-paper-button:not(#cancel-button), button');
			    if (btn) {
			      btn.click();
			      return true;
			    }
			    return false;
			  }
			  document.addEventListener('yt-popup-opened', function(e) {
			    try {
			      var n = e && e.detail && e.detail.nodeName;
			      if (!n || String(n).indexOf('CONFIRM') < 0) return;
			      if (dismiss()) {
			        var v = document.querySelector('video');
			        if (v && v.paused && !userIsActing()) try { v.play(); } catch (err) {}
			      }
			    } catch (err) {}
			  });
			  setInterval(function() {
			    var v = document.querySelector('video');
			    if (v && !v.__fermataRealPause) guard(v);
			    if (dismiss()) {
			      if (v && v.paused && !userIsActing()) try { v.play(); } catch (err) {}
			    }
			  }, 1000);
			  var v0 = document.querySelector('video');
			  if (v0) guard(v0);
			})();
			""";

	static String sponsorBlock(String categoriesJson) {
		return String.format(Locale.ROOT, """
				(function(categories) {
				  if (window.__fermataSponsorBlock && window.__fermataSponsorBlock.setCategories) {
				    window.__fermataSponsorBlock.setCategories(categories);
				    return;
				  }
				  var API = 'https://sponsor.ajay.app/api/skipSegments/';
				  var EDGE = 0.35;
				  var currentVideoId = null;
				  var segments = [];
				  var lastSkippedEnd = -1;
				  var currentCategories = categories;
				  var intervalId = null;
				  var attachedVideo = null;
				  var isStopped = false;
				  function videoIdFromUrl() {
				    try {
				      var u = new URL(location.href);
				      var v = u.searchParams.get('v');
				      if (v) return v;
				      var m = u.pathname.match(/^\\/shorts\\/([A-Za-z0-9_-]{11})/);
				      return m ? m[1] : null;
				    } catch (e) { return null; }
				  }
				  function sha256Hex(text) {
				    var bytes = new TextEncoder().encode(text);
				    return crypto.subtle.digest('SHA-256', bytes).then(function(buf) {
				      var arr = new Uint8Array(buf);
				      var hex = '';
				      for (var i = 0; i < arr.length; i++) {
				        hex += ('0' + arr[i].toString(16)).slice(-2);
				      }
				      return hex;
				    });
				  }
				  function loadSegments(videoId) {
				    segments = [];
				    lastSkippedEnd = -1;
				    sha256Hex(videoId).then(function(hex) {
				      var prefix = hex.slice(0, 4);
				      var cats = Array.isArray(currentCategories) && currentCategories.length ? currentCategories :
				          ['sponsor', 'selfpromo', 'interaction', 'intro', 'outro', 'music_offtopic'];
				      var url = API + prefix + '?categories=' + encodeURIComponent(JSON.stringify(cats));
				      return fetch(url).then(function(res) {
				        if (!res.ok) return [];
				        return res.json();
				      });
				    }).then(function(list) {
				      if (isStopped || !Array.isArray(list) || videoId !== currentVideoId) return;
				      var mine = [];
				      for (var i = 0; i < list.length; i++) {
				        if (list[i] && list[i].videoID === videoId && Array.isArray(list[i].segments)) {
				          mine = list[i].segments;
				          break;
				        }
				      }
				      var out = [];
				      for (var j = 0; j < mine.length; j++) {
				        var s = mine[j];
				        if (s && s.actionType === 'skip' && Array.isArray(s.segment) && s.segment.length === 2) {
				          var start = Number(s.segment[0]);
				          var end = Number(s.segment[1]);
				          if (isFinite(start) && isFinite(end) && end > start) out.push([start, end]);
				        }
				      }
				      out.sort(function(a, b) { return a[0] - b[0]; });
				      segments = out;
				    }).catch(function() {});
				  }
				  function maybeSkip(video) {
				    if (isStopped || !segments.length || !video || video.paused) return;
				    var t = video.currentTime;
				    for (var i = 0; i < segments.length; i++) {
				      var start = segments[i][0];
				      var end = segments[i][1];
				      if (end === lastSkippedEnd) continue;
				      if (t >= start && t < end - EDGE) {
				        lastSkippedEnd = end;
				        video.currentTime = end;
				        return;
				      }
				    }
				  }
				  function onTimeUpdate() {
				    if (!isStopped && attachedVideo) maybeSkip(attachedVideo);
				  }
				  function tick() {
				    if (isStopped) return;
				    var id = videoIdFromUrl();
				    if (id !== currentVideoId) {
				      currentVideoId = id;
				      segments = [];
				      lastSkippedEnd = -1;
				      if (id) loadSegments(id);
				    }
				    var v = document.querySelector('video');
				    if (v && v !== attachedVideo) {
				      if (attachedVideo) {
				        try { attachedVideo.removeEventListener('timeupdate', onTimeUpdate); } catch (e) {}
				      }
				      attachedVideo = v;
				      v.addEventListener('timeupdate', onTimeUpdate);
				    }
				  }
				  function stop() {
				    isStopped = true;
				    if (intervalId) {
				      clearInterval(intervalId);
				      intervalId = null;
				    }
				    if (attachedVideo) {
				      try { attachedVideo.removeEventListener('timeupdate', onTimeUpdate); } catch (e) {}
				      attachedVideo = null;
				    }
				    segments = [];
				    currentVideoId = null;
				    lastSkippedEnd = -1;
				  }
				  function setCategories(newCats) {
				    currentCategories = newCats;
				    if (isStopped) {
				      isStopped = false;
				      if (!intervalId) intervalId = setInterval(tick, 1000);
				    }
				    segments = [];
				    lastSkippedEnd = -1;
				    if (currentVideoId) loadSegments(currentVideoId);
				  }
				  window.__fermataSponsorBlock = {
				    stop: stop,
				    setCategories: setCategories
				  };
				  intervalId = setInterval(tick, 1000);
				  tick();
				})(%s);
				""", categoriesJson);
	}


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
			window.__fermataActiveVideo = fermataActiveContentVideo;
			window.__fermataVideoSignal = fermataVideoSignal;
			window.__fermataVideoTitle = fermataVideoTitle;
			window.__fermataPageVideoId = fermataPageVideoId;
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
				    contentSource: '', contentDuration: 0, observedRoot: null, tickPending: false,
				    videoListener: null
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
				      '.ytp-ad-skip-button, .ytp-ad-skip-button-modern,' +
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
				      try {
				        ad.muted = true;
				        ad.currentTime = Math.max(0, ad.duration - 0.15);
				      } catch (err) {}
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
				        state.lastAttempt = 0;
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
				        state.lastAttempt = 0;
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
				  function bindVideoEvents() {
				    if (state.videoListener) {
				      document.removeEventListener('playing', state.videoListener, true);
				      document.removeEventListener('loadedmetadata', state.videoListener, true);
				      document.removeEventListener('durationchange', state.videoListener, true);
				    }
				    state.videoListener = function(event) {
				      var target = event && event.target;
				      if (target && target.tagName === 'VIDEO') scheduleTick();
				    };
				    document.addEventListener('playing', state.videoListener, true);
				    document.addEventListener('loadedmetadata', state.videoListener, true);
				    document.addEventListener('durationchange', state.videoListener, true);
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
				    bindVideoEvents();
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
			  function badScheme(h) {
			    return /^[a-z][a-z0-9+.\\-]*:/i.test(h) && !/^https?:/i.test(h);
			  }
			  const rows = document.querySelectorAll(
			      'ytd-video-renderer, ytm-video-with-context-renderer, yt-lockup-view-model, ytm-compact-video-renderer');
			  const seen = {};
			  const result = [];
			  for (const row of rows) {
			    try {
			      if (row.closest('reel-shelf-renderer, ytm-reel-shelf-renderer, [class*="reel-shelf"]')) continue;
			      const titleElement = row.querySelector(
			          'a#video-title, #video-title, h3.media-item-headline, .media-item-headline, [class*="title"]');
			      const link = (titleElement && titleElement.closest('a[href*="/watch?v="]')) ||
			          row.querySelector('a[href*="/watch?v="]');
			      if (!link) continue;
			      const href = link.getAttribute('href') || link.href || '';
			      if (badScheme(href)) continue;
			      const u = new URL(link.href, location.href);
			      const id = u.searchParams.get('v');
			      if (!id || seen[id]) continue;
			      let title = link.getAttribute('title') ||
			          (titleElement && titleElement.textContent) || '';
			      title = title.replace(/\\s+/g, ' ').trim();
			      if (!title) continue;
			      const channelElement = row.querySelector(
			          '#channel-name a, #channel-name, .byline, ' +
			          'ytm-badge-and-byline-renderer [class*="ItemByline"], [class*="channel-name"]');
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
		return String.format(Locale.ROOT, """
				(function() {
				  function available(button) {
				    return button && !button.disabled &&
				        button.getAttribute('aria-disabled') !== 'true' &&
				        !button.classList.contains('icon-disable');
				  }
				  function unMute() {
				    var p = document.querySelector('#movie_player,.html5-video-player');
				    if (p && p.isMuted && p.isMuted() && p.unMute) {
				      try { p.unMute(); } catch (e) {}
				    }
				  }
				  function move() {
				    unMute();
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
				      try { player.%1$s(); return true; } catch (e) {}
				    }
				    if (%4$s) {
				      var nextLink = document.querySelector('ytm-video-with-context-renderer a[href*="/watch?v="], a.compact-media-item-image[href*="/watch?v="], a[href*="/watch?v="]');
				      if (nextLink) { nextLink.click(); return true; }
				    }
				    return false;
				  }
				  if (!move()) setTimeout(move, 600);
				})()
				""", method, selector, mobileButtonIndex, next ? "true" : "false");
	}

	static String videoQualities(String eventFunction, int eventCode, String autoLabel) {
		return String.format(Locale.ROOT, """
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
				""", eventFunction, eventCode, org.json.JSONObject.quote(autoLabel));
	}

	static String setVideoQuality(int index) {
		return String.format(Locale.ROOT, """
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
				""", index);
	}
}
