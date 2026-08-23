package me.aap.fermata.addon.web.yt;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Reads transport availability from the active YouTube player without changing playback state. */
final class YoutubeTransportCapabilities {
	private YoutubeTransportCapabilities() {
	}

	static FutureSupplier<Boolean> hasPreviousVideo(YoutubeWebView web) {
		Promise<Boolean> result = new Promise<>();
		web.evaluateJavascript(previousAvailabilityScript(), value ->
				result.complete("true".equalsIgnoreCase(value)));
		return result;
	}

	static String previousAvailabilityScript() {
		return "(function(){" + YoutubeScripts.PLAYBACK_SIGNAL +
				"if(!fermataActiveContentVideo())return false;" +
				"var p=document.querySelector('#movie_player,.html5-video-player');" +
				"try{if(p&&typeof p.getPlaylistIndex==='function'){" +
				"var i=Number(p.getPlaylistIndex());if(Number.isFinite(i))return i>0;}}catch(e){}" +
				"try{var i=Number(new URL(location.href).searchParams.get('index'));" +
				"return Number.isFinite(i)&&i>1;}catch(e){return false;}})()";
	}
}
