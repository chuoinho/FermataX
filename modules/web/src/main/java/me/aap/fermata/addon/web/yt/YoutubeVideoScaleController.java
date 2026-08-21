package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Keeps the configured YouTube object-fit authoritative while fallback immersive CSS is active. */
final class YoutubeVideoScaleController {
	private static final String STYLE_ID = "fermata-yt-scale-style";

	private YoutubeVideoScaleController() {
	}

	static void apply(@Nullable MainActivityDelegate activity, YoutubeAddon.VideoScale scale) {
		if ((activity == null) || !(activity.getActiveFragment() instanceof YoutubeFragment youtube)) return;
		YoutubeWebView web = youtube.getWebView();
		if (web != null) apply(web, scale);
	}

	static void apply(YoutubeWebView web, YoutubeAddon.VideoScale scale) {
		web.evaluateJavascript(script(scale), null);
	}

	static String script(YoutubeAddon.VideoScale scale) {
		String fit = scale.prefName();
		return "(function(){var id='" + STYLE_ID + "';var s=document.getElementById(id);" +
				"if(!s){s=document.createElement('style');s.id=id;" +
				"(document.head||document.documentElement).appendChild(s);}" +
				"s.textContent='html.fermata-yt-immersive body video{object-fit:" + fit +
				" !important;}';})()";
	}
}
