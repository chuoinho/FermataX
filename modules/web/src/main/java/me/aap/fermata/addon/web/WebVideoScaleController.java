package me.aap.fermata.addon.web;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static me.aap.fermata.media.pref.MediaPrefs.VIDEO_SCALE;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;

/**
 * Applies the common video-scale preference to browser-backed fullscreen video.
 *
 * <p>Native decoders are laid out by {@code VideoView}; Web/YouTube render inside a WebChromeClient
 * custom view and therefore need a DOM bridge. The bridge uses inline {@code !important} styles so
 * site/YouTube fullscreen CSS cannot silently reset the selected scale, and observers reapply the
 * scale when a SPA replaces the video element or the fullscreen viewport changes.</p>
 */
final class WebVideoScaleController implements PreferenceStore.Listener {
	private static final PreferenceStore.Pref<Supplier<String>> LEGACY_WEB_SCALE =
			PreferenceStore.Pref.s("VIDEO_SCALE", "contain");
	private static final String STATE = "__fermataGlobalVideoScaleState";

	private final FermataWebView web;
	private PreferenceStore mediaPrefs;
	private PreferenceStore addonPrefs;
	private boolean attached;

	WebVideoScaleController(FermataWebView web) {
		this.web = web;
	}

	void attach() {
		if (attached) {
			apply();
			return;
		}
		MainActivityDelegate activity;
		try {
			activity = MainActivityDelegate.get(web.getContext());
		} catch (RuntimeException ignored) {
			return;
		}
		mediaPrefs = activity.getLib().getPrefs();
		WebBrowserAddon addon = web.getAddon();
		addonPrefs = (addon == null) ? null : addon.getPreferenceStore();
		mediaPrefs.addBroadcastListener(this);
		if ((addonPrefs != null) && (addonPrefs != mediaPrefs)) addonPrefs.addBroadcastListener(this);
		attached = true;
		apply();
	}

	void detach() {
		if (!attached) return;
		attached = false;
		if (mediaPrefs != null) mediaPrefs.removeBroadcastListener(this);
		if ((addonPrefs != null) && (addonPrefs != mediaPrefs)) addonPrefs.removeBroadcastListener(this);
		mediaPrefs = null;
		addonPrefs = null;
		cleanup();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (!containsVideoScale(prefs)) return;

		// YouTube historically owns a string VIDEO_SCALE preference. Keep its existing menu usable,
		// but promote every selection to the common media-library scale so the choice is shared by
		// subsequent channels and by native/browser-backed addons.
		if ((store == addonPrefs) && (mediaPrefs != null)) {
			String legacy = store.getStringPref(LEGACY_WEB_SCALE);
			mediaPrefs.applyIntPref(false, VIDEO_SCALE, fromLegacyScale(legacy));
		}
		web.post(this::apply);
	}

	private void apply() {
		if (!attached || (mediaPrefs == null)) return;
		int scale = mediaPrefs.getIntPref(VIDEO_SCALE);
		try {
			web.evaluateJavascript(applyScript(scale), null);
		} catch (RuntimeException ignored) {
			// WebView can disappear between fullscreen teardown and a queued preference callback.
		}
	}

	private void cleanup() {
		try {
			web.evaluateJavascript("(function(){var s=window." + STATE + ";if(!s)return;" +
					"try{if(s.dispose)s.dispose();}catch(e){}" +
					"try{delete window." + STATE + ";}catch(e){window." + STATE + "=null;}})()", null);
		} catch (RuntimeException ignored) {
		}
	}

	static int fromLegacyScale(String scale) {
		if ("fill".equals(scale) || "cover".equals(scale)) return SCALE_FILL;
		if ("none".equals(scale)) return SCALE_ORIGINAL;
		return SCALE_BEST;
	}

	static String cssMode(int scale) {
		return switch (scale) {
			case SCALE_FILL -> "cover";
			case SCALE_ORIGINAL -> "original";
			case SCALE_4_3 -> "4:3";
			case SCALE_16_9 -> "16:9";
			default -> "contain";
		};
	}

	static String applyScript(int scale) {
		String mode = cssMode(scale);
		return String.format(Locale.ROOT, """
				(function(mode) {
				  var KEY = '%s';
				  var props = ['position','left','top','right','bottom','inset','width','height',
				    'max-width','max-height','transform','object-fit','margin'];
				  var s = window[KEY];
				  if (!s) {
				    s = { originals: new WeakMap(), tracked: [], observer: null, resize: null, mode: mode };
				    s.capture = function(v) {
				      if (!v || s.originals.has(v)) return;
				      var o = {};
				      props.forEach(function(p) {
				        o[p] = [v.style.getPropertyValue(p), v.style.getPropertyPriority(p)];
				      });
				      o.metadata = function() { if (window[KEY] === s) s.apply(v); };
				      try { v.addEventListener('loadedmetadata', o.metadata); } catch (e) {}
				      s.originals.set(v, o);
				      s.tracked.push(v);
				    };
				    s.restore = function(v) {
				      var o = s.originals.get(v);
				      if (!o) return;
				      props.forEach(function(p) {
				        var old = o[p];
				        if (old && old[0]) v.style.setProperty(p, old[0], old[1] || '');
				        else v.style.removeProperty(p);
				      });
				    };
				    s.set = function(v, p, value) { v.style.setProperty(p, value, 'important'); };
				    s.apply = function(v) {
				      if (!v || (v.tagName || '').toLowerCase() !== 'video') return;
				      s.capture(v);
				      s.restore(v);
				      var vw = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
				      var vh = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
				      s.set(v, 'position', 'fixed');
				      s.set(v, 'inset', 'auto');
				      s.set(v, 'left', '50%%');
				      s.set(v, 'top', '50%%');
				      s.set(v, 'right', 'auto');
				      s.set(v, 'bottom', 'auto');
				      s.set(v, 'transform', 'translate(-50%%,-50%%)');
				      s.set(v, 'max-width', 'none');
				      s.set(v, 'max-height', 'none');
				      s.set(v, 'margin', '0');
				      if (s.mode === 'original') {
				        var w0 = Math.max(1, Number(v.videoWidth || v.clientWidth || vw));
				        var h0 = Math.max(1, Number(v.videoHeight || v.clientHeight || vh));
				        s.set(v, 'width', w0 + 'px');
				        s.set(v, 'height', h0 + 'px');
				        s.set(v, 'object-fit', 'contain');
				      } else if ((s.mode === '4:3') || (s.mode === '16:9')) {
				        var r = (s.mode === '4:3') ? (4 / 3) : (16 / 9);
				        var w = vw, h = w / r;
				        if (h > vh) { h = vh; w = h * r; }
				        s.set(v, 'width', w + 'px');
				        s.set(v, 'height', h + 'px');
				        s.set(v, 'object-fit', 'fill');
				      } else {
				        s.set(v, 'width', '100vw');
				        s.set(v, 'height', '100vh');
				        s.set(v, 'object-fit', s.mode === 'cover' ? 'cover' : 'contain');
				      }
				    };
				    s.scan = function() {
				      try { document.querySelectorAll('video').forEach(s.apply); } catch (e) {}
				    };
				    if (typeof MutationObserver === 'function') {
				      s.observer = new MutationObserver(function() { s.scan(); });
				      try { s.observer.observe(document.documentElement,
				        { childList: true, subtree: true }); } catch (e) {}
				    }
				    s.resize = function() { s.scan(); };
				    try { window.addEventListener('resize', s.resize); } catch (e) {}
				    s.dispose = function() {
				      try { if (s.observer) s.observer.disconnect(); } catch (e) {}
				      try { if (s.resize) window.removeEventListener('resize', s.resize); } catch (e) {}
				      (s.tracked || []).forEach(function(v) {
				        try {
				          var o = s.originals.get(v);
				          s.restore(v);
				          if (o && o.metadata) v.removeEventListener('loadedmetadata', o.metadata);
				        } catch (e) {}
				      });
				    };
				    window[KEY] = s;
				  }
				  s.mode = mode;
				  s.scan();
				})('%s')
				""", STATE, mode);
	}

	private static boolean containsVideoScale(List<PreferenceStore.Pref<?>> prefs) {
		for (PreferenceStore.Pref<?> pref : prefs) {
			if (VIDEO_SCALE.getName().equals(pref.getName())) return true;
		}
		return false;
	}
}
