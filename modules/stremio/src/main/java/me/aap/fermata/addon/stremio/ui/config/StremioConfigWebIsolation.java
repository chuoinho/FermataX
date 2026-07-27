package me.aap.fermata.addon.stremio.ui.config;

import android.webkit.CookieManager;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ServiceWorkerWebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the ephemeral browser profile and network guards for one configuration session. */
interface StremioConfigWebIsolation {
	CookieManager cookies();

	WebStorage storage();

	void closeAfterDestroy();

	static StremioConfigWebIsolation production(StremioConfigWebView view,
			StremioConfigWebClient client) {
		if (!productionSupported()) {
			throw new IllegalStateException("The installed WebView cannot isolate provider setup");
		}
		String profileName = "fermatax-stremio-" + UUID.randomUUID();
		ProfileStore profiles = ProfileStore.getInstance();
		Profile profile = profiles.getOrCreateProfile(profileName);
		ScriptHandler guard = null;
		ServiceWorkerController workers = null;
		try {
			WebViewCompat.setProfile(view, profileName);
			guard = WebViewCompat.addDocumentStartJavaScript(view,
					networkGuardScript(), Set.of("*"));
			workers = profile.getServiceWorkerController();
			ServiceWorkerWebSettings settings = workers.getServiceWorkerWebSettings();
			settings.setAllowContentAccess(false);
			settings.setAllowFileAccess(false);
			settings.setBlockNetworkLoads(false);
			workers.setServiceWorkerClient(new ServiceWorkerClient() {
				@Override
				public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
					return client.loadWorker(request);
				}
			});
			return new ProfileIsolation(profile, profiles, profileName, workers, guard);
		} catch (RuntimeException failure) {
			if (guard != null) guard.remove();
			if (workers != null) workers.setServiceWorkerClient(null);
			view.destroy();
			profiles.deleteProfile(profileName);
			throw failure;
		}
	}

	static StremioConfigWebIsolation testing() {
		return new StremioConfigWebIsolation() {
			@Override
			public CookieManager cookies() {
				return CookieManager.getInstance();
			}

			@Override
			public WebStorage storage() {
				return WebStorage.getInstance();
			}

			@Override
			public void closeAfterDestroy() {
			}
		};
	}

	static boolean requiredFeaturesAvailable(boolean multiProfile, boolean documentStart) {
		return multiProfile && documentStart;
	}

	static boolean productionSupported() {
		return requiredFeaturesAvailable(isSupported(WebViewFeature.MULTI_PROFILE),
				isSupported(WebViewFeature.DOCUMENT_START_SCRIPT));
	}

	static String networkGuardScript() {
		return "(()=>{'use strict';const deny=(n)=>{try{const f=function(){throw new DOMException(" +
				"'Blocked by FermataX','SecurityError')};Object.defineProperty(globalThis,n," +
				"{value:f,writable:false,configurable:false})}catch(e){}};" +
				"['WebSocket','WebTransport','EventSource','Worker','SharedWorker'," +
				"'RTCPeerConnection','webkitRTCPeerConnection'].forEach(deny);" +
				"try{Object.defineProperty(navigator,'sendBeacon',{value:()=>false," +
				"writable:false,configurable:false})}catch(e){}" +
				"try{const f=globalThis.fetch.bind(globalThis);Object.defineProperty(globalThis,'fetch'," +
				"{value:(i,o)=>{const m=((o&&o.method)||'GET').toUpperCase();if(m!=='GET')" +
				"return Promise.reject(new DOMException('Blocked by FermataX','SecurityError'));" +
				"return f(i,o)},writable:false,configurable:false})}catch(e){}" +
				"try{const o=XMLHttpRequest.prototype.open;Object.defineProperty(" +
				"XMLHttpRequest.prototype,'open',{value:function(m){if(String(m).toUpperCase()!=='GET')" +
				"throw new DOMException('Blocked by FermataX','SecurityError');return o.apply(this," +
				"arguments)},writable:false,configurable:false})}catch(e){}" +
				"try{const s=HTMLFormElement.prototype.submit;Object.defineProperty(" +
				"HTMLFormElement.prototype,'submit',{value:function(){if((this.method||'get').toLowerCase()" +
				"!=='get')throw new DOMException('Blocked by FermataX','SecurityError');return s.call(this)}," +
				"writable:false,configurable:false});document.addEventListener('submit',e=>{" +
				"if((e.target.method||'get').toLowerCase()!=='get'){e.preventDefault();" +
				"e.stopImmediatePropagation()}},true)}catch(e){}" +
				"})()";
	}

	private static boolean isSupported(String feature) {
		return WebViewFeature.isFeatureSupported(feature);
	}

	final class ProfileIsolation implements StremioConfigWebIsolation {
		private final Profile profile;
		private final ProfileStore profiles;
		private final String profileName;
		private final ServiceWorkerController workers;
		private final ScriptHandler guard;
		private final AtomicBoolean closed = new AtomicBoolean();

		private ProfileIsolation(Profile profile, ProfileStore profiles, String profileName,
				ServiceWorkerController workers, ScriptHandler guard) {
			this.profile = profile;
			this.profiles = profiles;
			this.profileName = profileName;
			this.workers = workers;
			this.guard = guard;
		}

		@Override
		public CookieManager cookies() {
			return profile.getCookieManager();
		}

		@Override
		public WebStorage storage() {
			return profile.getWebStorage();
		}

		@Override
		public void closeAfterDestroy() {
			if (!closed.compareAndSet(false, true)) return;
			guard.remove();
			workers.setServiceWorkerClient(null);
			profiles.deleteProfile(profileName);
		}
	}
}
