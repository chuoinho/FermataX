package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/** YouTube-only WebAudio graph owner. It passes bounded profile data, never media data, to JS. */
final class YoutubeWebAudioBridge implements PreferenceStore.Listener, AutoCloseable {
	private static final String MOBILE_ORIGIN = "https://m.youtube.com";
	private static final String DESKTOP_ORIGIN = "https://www.youtube.com";
	private static final Set<String> ORIGINS = Set.of(MOBILE_ORIGIN, DESKTOP_ORIGIN);
	private static final Map<YoutubeWebView, YoutubeWebAudioBridge> BRIDGES =
			Collections.synchronizedMap(new WeakHashMap<>());
	private final YoutubeWebView web;
	private final PreferenceStore store;
	private final AudioEffectsProfileRepository profiles;
	@Nullable
	private ScriptHandler script;
	private boolean installed;
	private boolean acceptingDocument;
	private long documentGeneration;

	private YoutubeWebAudioBridge(YoutubeWebView web) {
		this.web = web;
		store = FermataApplication.get().getPreferenceStore();
		profiles = new AudioEffectsProfileRepository(store);
	}

	static void install(YoutubeWebView web) {
		bridge(web).install();
	}

	static void onDocumentNavigation(YoutubeWebView web, String url) {
		if ((url == null) || url.regionMatches(true, 0, "javascript:", 0, 11)) return;
		YoutubeWebAudioBridge bridge;
		synchronized (BRIDGES) {
			bridge = BRIDGES.get(web);
		}
		if (bridge != null) bridge.onNavigation(url);
	}

	static void close(YoutubeWebView web) {
		YoutubeWebAudioBridge bridge;
		synchronized (BRIDGES) {
			bridge = BRIDGES.remove(web);
		}
		if (bridge != null) bridge.close();
	}

	private static YoutubeWebAudioBridge bridge(YoutubeWebView web) {
		synchronized (BRIDGES) {
			return BRIDGES.computeIfAbsent(web, YoutubeWebAudioBridge::new);
		}
	}

	private void install() {
		if (installed || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
		installed = true;
		store.addBroadcastListener(this);
	}

	private void onNavigation(String url) {
		invalidateDocument();
		if (!installed || !isHostedDocument(url) || !YoutubePlaybackHostPolicy.isPreferredHost(web)) return;
		long generation = ++documentGeneration;
		try {
			script = WebViewCompat.addDocumentStartJavaScript(web, shimSource(generation, currentProfile()), ORIGINS);
			acceptingDocument = true;
		} catch (RuntimeException error) {
			Log.w(error, "YouTube WebAudio bridge document setup failed");
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore ignored, java.util.List<PreferenceStore.Pref<?>> changed) {
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
		if (!installed || !acceptingDocument || !YoutubePlaybackHostPolicy.isPreferredHost(web)) return;
		long generation = documentGeneration;
		YoutubeWebAudioProfile profile = currentProfile();
		web.post(() -> {
			if (!installed || !acceptingDocument || (generation != documentGeneration) ||
					!isCurrentHostedDocument() || !YoutubePlaybackHostPolicy.isPreferredHost(web)) return;
			try {
				web.evaluateJavascript(updateProfileSource(generation, profile), null);
			} catch (RuntimeException ignored) {
				// A destroyed or replaced host must leave browser playback untouched.
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
				// The page may already be gone; pagehide has the same neutralization boundary.
			}
		}
		ScriptHandler current = script;
		script = null;
		if (current != null) current.remove();
	}

	private YoutubeWebAudioProfile currentProfile() {
		try {
			AudioEffectsProfile profile = profiles.load();
			return YoutubeWebAudioProfile.from(profile);
		} catch (RuntimeException error) {
			return YoutubeWebAudioProfile.unity();
		}
	}

	static boolean isAllowedOrigin(String origin) {
		return ORIGINS.contains(origin);
	}

	static boolean isCurrentDocumentGeneration(long expected, long candidate) {
		return expected == candidate;
	}

	static boolean isHostedDocument(@Nullable String url) {
		if (url == null) return false;
		try {
			URI uri = new URI(url);
			int port = uri.getPort();
			if (!"https".equalsIgnoreCase(uri.getScheme()) || ((port != -1) && (port != 443))) return false;
			String host = uri.getHost();
			return (host != null) && ("m.youtube.com".equalsIgnoreCase(host) ||
					"www.youtube.com".equalsIgnoreCase(host));
		} catch (URISyntaxException ignored) {
			return false;
		}
	}

	private boolean isCurrentHostedDocument() {
		return isHostedDocument(web.getUrl());
	}

	static String updateProfileSource(long generation, YoutubeWebAudioProfile profile) {
		return "(function(){var b=window.__fermataYoutubeWebAudioV1;return !!(b&&b.version===1&&" +
				"b.updateProfile(" + generation + "," + profile.toJavascriptObject() + "));})();";
	}

	static String teardownSource(long generation) {
		return "(function(){var b=window.__fermataYoutubeWebAudioV1;return !!(b&&b.version===1&&" +
				"b.teardown(" + generation + "));})();";
	}

	static String shimSource(long generation, YoutubeWebAudioProfile initialProfile) {
		return String.format(Locale.ROOT, """
			(function(){
			  'use strict';
			  if (window.top !== window || window.__fermataYoutubeWebAudioV1) return;
			  var generation=%d, VERSION=1, MIN_DB=-15, MAX_DB=15, MIN_PREAMP=-60, Q=Math.SQRT2;
			  var profile=%s, ownership=new WeakMap(), encrypted=new WeakSet(), active=null, disposed=false;
			  var finite=Number.isFinite||function(v){return typeof v==='number'&&isFinite(v);};
			  var unity=function(){return {v:VERSION,m:false,e:false,b:[0,0,0,0,0,0,0,0,0,0],p:0};};
			  var normalize=function(v){if(!v||v.v!==VERSION||typeof v.m!=='boolean'||typeof v.e!=='boolean'||!Array.isArray(v.b)||v.b.length!==10||!finite(v.p)||v.p<MIN_PREAMP||v.p>0)return null;var b=[];for(var i=0;i<10;i++){if(!finite(v.b[i])||v.b[i]<MIN_DB||v.b[i]>MAX_DB)return null;b.push(v.b[i]);}return {v:VERSION,m:v.m,e:v.e,b:b,p:v.p};};
			  profile=normalize(profile)||unity();
			  var sourceOf=function(v){try{return String(v.currentSrc||v.src||'');}catch(_){return '';}};
			  var isBlob=function(v){return v.slice(0,5).toLowerCase()==='blob:';};
			  var smooth=function(p,v,c){try{p.cancelScheduledValues(c.currentTime);p.setTargetAtTime(v,c.currentTime,0.015);}catch(_){try{p.value=v;}catch(ignored){}}};
			  var neutral=function(owner){if(!owner)return;try{smooth(owner.preamp.gain,1,owner.context);for(var i=0;i<owner.filters.length;i++)smooth(owner.filters[i].gain,0,owner.context);}catch(_){}};
			  var apply=function(owner){if(!owner)return;var enabled=profile.m&&profile.e;try{smooth(owner.preamp.gain,enabled?Math.pow(10,profile.p/20):1,owner.context);for(var i=0;i<owner.filters.length;i++)smooth(owner.filters[i].gain,enabled?profile.b[i]:0,owner.context);}catch(_){neutral(owner);}};
			  var resume=function(owner){if(!owner||owner.resuming||owner.context.state!=='suspended')return;owner.resuming=true;try{var p=owner.context.resume();if(p&&p.then)p.then(function(){owner.resuming=false;},function(){owner.resuming=false;});else owner.resuming=false;}catch(_){owner.resuming=false;}};
			  var candidate=function(){var media=(typeof fermataActiveContentVideo==='function')?fermataActiveContentVideo():null;if(!media||!media.isConnected||media.paused||media.ended||media.readyState<2||media.mediaKeys!=null||encrypted.has(media)||!isBlob(sourceOf(media)))return null;return media;};
			  var attach=function(media){var prior=ownership.get(media);if(prior){active=prior;apply(prior);resume(prior);return;}try{var Context=window.AudioContext||window.webkitAudioContext;if(!Context)return;var context=new Context(),pre=context.createGain(),filters=[],node=pre;for(var i=0;i<10;i++){var f=context.createBiquadFilter();f.type='peaking';f.frequency.value=[31,62,125,250,500,1000,2000,4000,8000,16000][i];f.Q.value=Q;filters.push(f);node.connect(f);node=f;}var output=context.createGain(),source=context.createMediaElementSource(media),owner={media:media,context:context,source:source,preamp:pre,filters:filters,output:output,resuming:false};source.connect(pre);node.connect(output);output.connect(context.destination);ownership.set(media,owner);active=owner;apply(owner);resume(owner);}catch(_){if(active)neutral(active);}};
			  var evaluate=function(){if(disposed)return;var media=candidate();if(media&&(ownership.get(media)||(profile.m&&profile.e))){attach(media);return;}if(active&&active.media&&active.media.mediaKeys!=null)neutral(active);};
			  var schedule=function(){if(!disposed)Promise.resolve().then(evaluate);};
			  var onMedia=function(event){var media=event.target;if(!media||media.tagName!=='VIDEO')return;if(event.type==='encrypted'||event.type==='webkitneedkey'){encrypted.add(media);if(active&&active.media===media)neutral(active);}schedule();};
			  var events=['play','playing','timeupdate','loadeddata','canplay','loadstart','emptied','ended','error','encrypted','webkitneedkey'];for(var e=0;e<events.length;e++)document.addEventListener(events[e],onMedia,true);
			  var observer=new MutationObserver(schedule);if(document.documentElement)observer.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['src','style','class','hidden']});
			  var timer=setInterval(schedule,750);
			  var teardown=function(messageGeneration){if(messageGeneration!==generation||disposed)return false;disposed=true;clearInterval(timer);observer.disconnect();neutral(active);return true;};
			  addEventListener('pagehide',function(){teardown(generation);},{once:true});
			  window.__fermataYoutubeWebAudioV1=Object.freeze({version:VERSION,status:function(){return {r:active?'SUPPORTED_ACTIVE':'NO_MEDIA',m:profile.m,e:profile.e,p:profile.p,b:active?active.filters[5].gain.value:null};},updateProfile:function(messageGeneration,value){if(messageGeneration!==generation||disposed)return false;var next=normalize(value);if(!next)return false;profile=next;apply(active);schedule();return true;},teardown:teardown});
			})();
			""", generation, initialProfile.toJavascriptObject());
	}
}
