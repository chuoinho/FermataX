package me.aap.fermata.addon.web;

import androidx.annotation.Nullable;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

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
 * WebAudio equalizer bridge for generic Web Browser pages in FermataX.
 *
 * Implements deterministic CORS pre-checking and fail-safe audio bypass:
 * elements that are safe (blob/MSE, data, same-origin, or explicit CORS) get
 * the 10-band EQ + DynamicsCompressor + auto make-up gain.
 * Unsupported opaque cross-origin elements are untouched so they play original audio
 * without being muted.
 */
final class GenericWebAudioBridge implements PreferenceStore.Listener, AutoCloseable {
	private static final Set<String> ALL_ORIGINS = Set.of("*");
	private final FermataWebView web;
	private final PreferenceStore store;
	private final AudioEffectsProfileRepository profiles;
	@Nullable
	private ScriptHandler script;
	private boolean installed;
	private boolean acceptingDocument;
	private long documentGeneration;

	GenericWebAudioBridge(FermataWebView web) {
		this.web = web;
		this.store = FermataApplication.get().getPreferenceStore();
		this.profiles = new AudioEffectsProfileRepository(store);
	}

	void install() {
		if (installed) return;
		installed = true;
		store.addBroadcastListener(this);
	}

	void onNavigation(@Nullable String url) {
		invalidateDocument();
		if (!installed || url == null || url.regionMatches(true, 0, "javascript:", 0, 11) ||
				"about:blank".equals(url)) {
			return;
		}
		long generation = ++documentGeneration;
		if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
			try {
				script = WebViewCompat.addDocumentStartJavaScript(web,
						shimSource(generation, currentProfile()), ALL_ORIGINS);
				acceptingDocument = true;
			} catch (RuntimeException error) {
				Log.w(error, "Generic WebAudio bridge document start script failed");
			}
		}
	}

	void onPageLoaded(@Nullable String url) {
		if (!installed || url == null || "about:blank".equals(url)) return;
		long generation = documentGeneration;
		WebAudioProfile profile = currentProfile();
		web.post(() -> {
			if (!installed || generation != documentGeneration) return;
			try {
				// Inject if document-start script was not supported or ensure active page has bridge
				web.evaluateJavascript(shimSource(generation, profile), null);
				acceptingDocument = true;
			} catch (RuntimeException ignored) {}
		});
	}

	@Override
	public void onPreferenceChanged(PreferenceStore ignored, List<PreferenceStore.Pref<?>> changed) {
		if (AudioEffectsProfileRepository.containsProfilePreference(changed)) dispatchProfile();
	}

	@Override
	public void close() {
		if (!installed) return;
		installed = false;
		store.removeBroadcastListener(this);
		invalidateDocument();
	}

	private void dispatchProfile() {
		if (!installed || !acceptingDocument) return;
		long generation = documentGeneration;
		WebAudioProfile profile = currentProfile();
		web.post(() -> {
			if (!installed || !acceptingDocument || (generation != documentGeneration)) return;
			try {
				web.evaluateJavascript(updateProfileSource(generation, profile), null);
			} catch (RuntimeException ignored) {}
		});
	}

	private void invalidateDocument() {
		long previousGeneration = documentGeneration;
		acceptingDocument = false;
		documentGeneration++;
		if (previousGeneration != 0L) {
			try {
				web.evaluateJavascript(teardownSource(previousGeneration), null);
			} catch (RuntimeException ignored) {}
		}
		ScriptHandler current = script;
		script = null;
		if (current != null) current.remove();
	}

	private WebAudioProfile currentProfile() {
		try {
			AudioEffectsProfile profile = profiles.load();
			return WebAudioProfile.from(profile);
		} catch (RuntimeException error) {
			return WebAudioProfile.unity();
		}
	}

	static String updateProfileSource(long generation, WebAudioProfile profile) {
		return WebAudioJsSource.updateProfileSource("__fermataGenericWebAudioV1", generation, profile);
	}

	static String teardownSource(long generation) {
		return WebAudioJsSource.teardownSource("__fermataGenericWebAudioV1", generation);
	}

	static String shimSource(long generation, WebAudioProfile initialProfile) {
		return String.format(Locale.ROOT, """
			(function(){
			  'use strict';
			  if (window.top !== window || window.__fermataGenericWebAudioV1) return;
			  var generation = %d;
			  var profile = %s;
			%s
			  var ownership = new WeakMap(), active = null, disposed = false;
			  profile = normalize(profile) || unity();

			  var isSafe = function(media) {
			    try {
			      var s = String(media.currentSrc || media.src || '');
			      if (!s) return false;
			      if (s.indexOf('blob:') === 0 || s.indexOf('data:') === 0) return true;
			      if (s.indexOf('file:') === 0) return false;
			      var co = media.crossOrigin;
			      if (co === 'anonymous' || co === 'use-credentials') return true;
			      return new URL(s, location.href).origin === location.origin;
			    } catch (_) {
			      return false;
			    }
			  };

			  var resume = function(owner){
			    if (!owner || owner.resuming || owner.context.state !== 'suspended') return;
			    owner.resuming = true;
			    try {
			      var p = owner.context.resume();
			      if (p && p.then) p.then(function(){ owner.resuming = false; }, function(){ owner.resuming = false; });
			      else owner.resuming = false;
			    } catch (_) { owner.resuming = false; }
			  };

			  var attach = function(media){
			    if (!isSafe(media)) return;
			    var prior = ownership.get(media);
			    if (prior) { active = prior; apply(prior); resume(prior); return; }
			    try {
			      var Context = window.AudioContext || window.webkitAudioContext;
			      if (!Context) return;
			      var context = new Context();
			      var nodes = buildAudioNodes(context);
			      var source = context.createMediaElementSource(media);
			      source.connect(nodes.preamp);
			      var owner = {
			        media: media, context: context, source: source,
			        preamp: nodes.preamp, filters: nodes.filters,
			        nen: nodes.nen, bu: nodes.bu, output: nodes.output,
			        resuming: false
			      };
			      ownership.set(media, owner);
			      active = owner;
			      apply(owner);
			      resume(owner);
			    } catch (_) {
			      if (active) neutral(active);
			    }
			  };

			  var onMedia = function(event){
			    if (disposed) return;
			    var media = event.target;
			    if (!media || (media.tagName !== 'VIDEO' && media.tagName !== 'AUDIO')) return;
			    if (profile.m && profile.e) attach(media);
			  };

			  var events = ['play', 'playing'];
			  for (var e = 0; e < events.length; e++) document.addEventListener(events[e], onMedia, true);

			  var teardown = function(messageGeneration){
			    if (messageGeneration !== generation || disposed) return false;
			    disposed = true;
			    for (var e = 0; e < events.length; e++) {
			      try { document.removeEventListener(events[e], onMedia, true); } catch (_) {}
			    }
			    if (active) {
			      neutral(active);
			      try {
			        if (active.source) active.source.disconnect();
			        if (active.preamp) active.preamp.disconnect();
			        if (active.filters) {
			          for (var i = 0; i < active.filters.length; i++) active.filters[i].disconnect();
			        }
			        if (active.nen) active.nen.disconnect();
			        if (active.bu) active.bu.disconnect();
			        if (active.output) active.output.disconnect();
			        if (active.context && active.context.state !== 'closed') active.context.close();
			      } catch (_) {}
			      if (active.media) try { ownership.delete(active.media); } catch (_) {}
			      active = null;
			    }
			    return true;
			  };
			  addEventListener('pagehide', function(){ teardown(generation); }, {once:true});

			  window.__fermataGenericWebAudioV1 = Object.freeze({
			    version: VERSION,
			    updateProfile: function(messageGeneration, value){
			      if (messageGeneration !== generation || disposed) return false;
			      var next = normalize(value);
			      if (!next) return false;
			      profile = next;
			      if (profile.m && profile.e && !active) {
			        var allMedia = document.querySelectorAll('video, audio');
			        for (var i = 0; i < allMedia.length; i++) {
			          var m = allMedia[i];
			          if (m && !m.paused && !m.ended && isSafe(m)) {
			            attach(m);
			            break;
			          }
			        }
			      }
			      apply(active);
			      return true;
			    },
			    teardown: teardown
			  });
			})();
			""", generation, initialProfile.toJavascriptObject(), WebAudioJsSource.CORE_DSP_LOGIC);
	}
}
