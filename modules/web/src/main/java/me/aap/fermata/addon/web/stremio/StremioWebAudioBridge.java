package me.aap.fermata.addon.web.stremio;

import androidx.annotation.Nullable;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.web.audio.WebAudioJsSource;
import me.aap.fermata.addon.web.audio.WebAudioProfile;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * Exact-origin WebAudio backend for the narrow, proven-safe Stremio main-document blob/MSE path.
 * It sends a bounded profile only; media data and page state remain inside the hosted document.
 */
final class StremioWebAudioBridge implements PreferenceStore.Listener, AutoCloseable {
	private static final String ORIGIN = "https://web.stremio.com";
	private static final Set<String> ORIGINS = Set.of(ORIGIN);
	private final StremioWebView web;
	private final AudioEffectsProfileRepository profiles;
	private final PreferenceStore store;
	@Nullable
	private ScriptHandler script;
	private boolean installed;
	private boolean acceptingDocument;
	private long documentGeneration;

	StremioWebAudioBridge(StremioWebView web) {
		this.web = web;
		store = FermataApplication.get().getPreferenceStore();
		profiles = new AudioEffectsProfileRepository(store);
	}

	void install() {
		if (!supportsBridge(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))) {
			Log.w("Stremio WebAudio bridge disabled: document-start scripts unavailable");
			return;
		}
		installed = true;
		store.addBroadcastListener(this);
	}

	void onDocumentNavigation(@Nullable String url) {
		invalidateDocument();
		if (!installed || !isHostedDocument(url)) return;
		long generation = ++documentGeneration;
		try {
			script = WebViewCompat.addDocumentStartJavaScript(web,
					shimSource(generation, currentProfile()), ORIGINS);
			acceptingDocument = true;
		} catch (RuntimeException error) {
			Log.w(error, "Stremio WebAudio bridge document setup failed");
		}
	}

	/** Invalidates document-owned graphs before automotive teardown, replacement, or destruction. */
	void endAutomotiveSession() {
		invalidateDocument();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore ignored, List<PreferenceStore.Pref<?>> changed) {
		if (AudioEffectsProfileRepository.containsProfilePreference(changed)) dispatchProfile();
	}

	@Override
	public void close() {
		store.removeBroadcastListener(this);
		installed = false;
		invalidateDocument();
	}

	private void dispatchProfile() {
		if (!installed || !acceptingDocument) return;
		long generation = documentGeneration;
		WebAudioProfile profile = currentProfile();
		web.post(() -> {
			if (!installed || !acceptingDocument || (generation != documentGeneration) ||
					!isCurrentHostedDocument()) return;
			try {
				web.evaluateJavascript(updateProfileSource(generation, profile), null);
			} catch (RuntimeException ignored) {
				// A destroyed or replaced WebView must leave hosted playback alone.
			}
		});
	}

	private void invalidateDocument() {
		long previousGeneration = documentGeneration;
		acceptingDocument = false;
		documentGeneration++;
		if ((previousGeneration != 0L) && isCurrentHostedDocument()) {
			try {
				web.evaluateJavascript(teardownSource(previousGeneration), null);
			} catch (RuntimeException ignored) {
				// The document may already be gone; pagehide is the secondary cleanup boundary.
			}
		}
		removeDocumentScript();
	}

	private WebAudioProfile currentProfile() {
		try {
			AudioEffectsProfile profile = profiles.load();
			return WebAudioProfile.from(profile);
		} catch (RuntimeException error) {
			return WebAudioProfile.unity();
		}
	}

	private void removeDocumentScript() {
		ScriptHandler current = script;
		script = null;
		if (current != null) current.remove();
	}

	static boolean supportsBridge(boolean documentStartScript) {
		return documentStartScript;
	}

	static boolean isAllowedOrigin(String origin) {
		return ORIGIN.equals(origin);
	}

	private boolean isCurrentHostedDocument() {
		return isHostedDocument(web.getUrl());
	}

	static boolean isHostedDocument(@Nullable String url) {
		if (url == null) return false;
		try {
			URI uri = new URI(url);
			int port = uri.getPort();
			return "https".equalsIgnoreCase(uri.getScheme()) &&
					"web.stremio.com".equalsIgnoreCase(uri.getHost()) &&
					((port == -1) || (port == 443));
		} catch (URISyntaxException ignored) {
			return false;
		}
	}

	static String updateProfileSource(long generation, WebAudioProfile profile) {
		return WebAudioJsSource.updateProfileSource("__fermataStremioWebAudioV1", generation, profile);
	}

	static String teardownSource(long generation) {
		return WebAudioJsSource.teardownSource("__fermataStremioWebAudioV1", generation);
	}

	static String shimSource(long generation, WebAudioProfile initialProfile) {
		return String.format(Locale.ROOT, """
			(function(){
			  'use strict';
			  if (window.top !== window || window.__fermataStremioWebAudioV1) return;
			  var generation = %d;
			  var profile = %s;
			%s
			  var ownership = new WeakMap(), activity = new WeakMap(), encrypted = new WeakSet();
			  var active = null, observer = null, scheduled = false, disposed = false;
			  var now = function(){ return (window.performance && performance.now) ? performance.now() : Date.now(); };
			  profile = normalize(profile) || unity();
			  var visible = function(media){
			    try {
			      var style = getComputedStyle(media), rect = media.getBoundingClientRect();
			      return style.display !== 'none' && style.visibility !== 'hidden' &&
			          Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;
			    } catch (_) { return false; }
			  };
			  var noteActivity = function(media){
			    var state = activity.get(media) || {}, time = Number(media.currentTime), stamp = now();
			    if (finite(time) && finite(state.time) && time > state.time + 0.01) state.advancingUntil = stamp + 1500;
			    state.time = time; activity.set(media, state); return state;
			  };
			  var candidate = function(media, stamp){
			    var state = noteActivity(media), source = sourceOf(media);
			    if (!media.isConnected || !visible(media) || media.paused || media.ended || media.readyState < 2 ||
			        !isBlob(source) || media.mediaKeys != null || encrypted.has(media) ||
			        !state.advancingUntil || stamp > state.advancingUntil) return null;
			    var rect = media.getBoundingClientRect();
			    return {media:media, source:source, rank:(media.readyState * 1000000000) +
			        Math.round(rect.width * rect.height)};
			  };
			  var selectActive = function(){
			    var media = document.querySelectorAll('video,audio'), winner = null, stamp = now();
			    for (var i = 0; i < media.length; i++) {
			      var next = candidate(media[i], stamp); if (!next) continue;
			      if (!winner || next.rank > winner.rank) winner = next;
			      else if (next.rank === winner.rank) return null;
			    }
			    return winner;
			  };
			  var playerRoute = function(){ return String(location.hash || '').indexOf('#/player/') === 0; };
			  var disconnect = function(node){ try { if (node) node.disconnect(); } catch (_) {} };
			  var closeOwner = function(owner){
			    if (!owner || owner.closed) return;
			    owner.closed = true; owner.terminal = true;
			    if (active === owner) active = null;
			    disconnect(owner.source); disconnect(owner.preamp);
			    for (var i = 0; i < owner.filters.length; i++) disconnect(owner.filters[i]);
			    disconnect(owner.nen); disconnect(owner.bu);
			    disconnect(owner.output);
			    try { if (owner.context.state !== 'closed') owner.context.close(); } catch (_) {}
			  };
			  var resume = function(owner){
			    if (!owner || owner.closed || owner.resuming || owner.media.paused ||
			        owner.context.state !== 'suspended') return;
			    owner.resuming = true;
			    try {
			      var pending = owner.context.resume();
			      if (pending && typeof pending.then === 'function') pending.then(
			          function(){ owner.resuming = false; }, function(){ owner.resuming = false; });
			      else owner.resuming = false;
			    }
			    catch (_) { owner.resuming = false; }
			  };
			  var retainsOwner = function(owner){
			    var media = owner.media, source = sourceOf(media);
			    return media.isConnected && !media.ended && !media.error &&
			        isBlob(source) && source === owner.sourceKey && owner.context.state !== 'closed' &&
			        media.mediaKeys == null && !encrypted.has(media);
			  };
			  var attach = function(selected){
			    var prior = ownership.get(selected.media);
			    if (prior && prior.terminal) return;
			    var owner = prior || {media:selected.media, terminal:false, closed:false};
			    ownership.set(selected.media, owner);
			    try {
			      var Context = window.AudioContext || window.webkitAudioContext;
			      if (!Context) { owner.terminal = true; return; }
			      owner.context = new Context();
			      owner.source = owner.context.createMediaElementSource(selected.media);
			      var nodes = buildAudioNodes(owner.context);
			      owner.preamp = nodes.preamp;
			      owner.filters = nodes.filters;
			      owner.nen = nodes.nen;
			      owner.bu = nodes.bu;
			      owner.output = nodes.output;
			      owner.sourceKey = selected.source;
			      owner.source.connect(owner.preamp);
			      active = owner;
			      apply(owner);
			      resume(owner);
			    } catch (_) { closeOwner(owner); }
			  };
			  var evaluate = function(){
			    if (disposed) return;
			    if (active) { if (!retainsOwner(active)) closeOwner(active); else { resume(active); return; } }
			    if (!(profile.m && profile.e)) return;
			    if (!playerRoute()) return;
			    var selected = selectActive(); if (selected) attach(selected);
			  };
			  var schedule = function(){
			    if (disposed || scheduled) return; scheduled = true;
			    Promise.resolve().then(function(){ scheduled = false; evaluate(); });
			  };
			  var onMedia = function(event){
			    var media = event.target;
			    if (!media || (media.tagName !== 'VIDEO' && media.tagName !== 'AUDIO')) return;
			    if (event.type === 'encrypted' || event.type === 'webkitneedkey') {
			      encrypted.add(media); if (active && active.media === media) closeOwner(active);
			    } else noteActivity(media);
			    schedule();
			  };
			  var events = ['play','playing','timeupdate','loadeddata','canplay','loadstart','emptied','ended','error','encrypted','webkitneedkey'];
			  for (var e = 0; e < events.length; e++) document.addEventListener(events[e], onMedia, true);
			  observer = new MutationObserver(schedule);
			  if (document.documentElement) observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['src','style','class','hidden']});
			  window.addEventListener('hashchange', schedule);
			  var teardown = function(messageGeneration){
			    if (messageGeneration !== generation || disposed) return false;
			    disposed = true;
			    if (observer) { observer.disconnect(); observer = null; }
			    window.removeEventListener('hashchange', schedule);
			    closeOwner(active);
			    return true;
			  };
			  addEventListener('pagehide', function(){ teardown(generation); }, {once:true});
			  window.__fermataStremioWebAudioV1 = Object.freeze({
			    version: VERSION,
			    status: function(){ return {r: active ? 'SUPPORTED_ACTIVE' : 'NO_MEDIA', m: profile.m, e: profile.e, p: profile.p, b: active ? active.filters[5].gain.value : null}; },
			    updateProfile: function(messageGeneration, value){
			      if (messageGeneration !== generation || disposed) return false;
			      var next = normalize(value);
			      if (!next) return false;
			      profile = next;
			      apply(active);
			      schedule();
			      return true;
			    },
			    teardown: teardown
			  });
			})();
			""", generation, initialProfile.toJavascriptObject(), WebAudioJsSource.CORE_DSP_LOGIC);
	}
}
