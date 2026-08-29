package me.aap.fermata.addon.web.yt;

import org.json.JSONObject;

import me.aap.fermata.BuildConfig;

final class YoutubePlaybackDiagnostics {
	private YoutubePlaybackDiagnostics() {
	}

	static void log(YoutubeWebView web, String reason) {
		if (!BuildConfig.YT_DIAGNOSTICS) return;
		web.evaluateJavascript(YoutubeScripts.PLAYBACK_SIGNAL + """
				(function(reason) {
				  var v = fermataActiveContentVideo();
				  var p = document.querySelector('#movie_player');
				  var r = v ? v.getBoundingClientRect() : null;
				  var s = v ? getComputedStyle(v) : null;
				  var pv = null;
				  try { if (p && typeof p.getVolume === 'function') pv = p.getVolume(); } catch (e) {}
				  return JSON.stringify({reason:reason,hasVideo:!!v,playing:!!v&&!v.paused&&!v.ended,
				    muted:!!v&&v.muted,volume:v?v.volume:null,playerVolume:pv,
				    immersive:document.documentElement.classList.contains('fermata-yt-immersive'),
				    viewport:[innerWidth,innerHeight],rect:r?[r.x,r.y,r.width,r.height]:null,
				    intrinsic:v?[v.videoWidth,v.videoHeight]:null,objectFit:s?s.objectFit:null});
				})(""" + JSONObject.quote(reason) + ");", value -> android.util.Log.i(
				"FermataXYtDiag", YoutubeScripts.decodeJavascriptString(value)));
	}
}
