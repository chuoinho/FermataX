package me.aap.fermata.addon.web.yt;

import me.aap.fermata.ui.policy.RuntimeHostMode;

/** Separates Auto-build playback behavior from the host used to render presentation chrome. */
record YoutubeRuntimePolicy(boolean autoPlaybackBehavior, boolean automotivePresentation) {
	static YoutubeRuntimePolicy resolve(boolean autoBuild, RuntimeHostMode hostMode) {
		return new YoutubeRuntimePolicy(autoBuild, hostMode.usesAutomotivePresentation());
	}

	boolean supportsAutomaticFullscreen() {
		return autoPlaybackBehavior;
	}

	boolean keepsPlaybackWhenUiPauses() {
		return autoPlaybackBehavior;
	}
}
