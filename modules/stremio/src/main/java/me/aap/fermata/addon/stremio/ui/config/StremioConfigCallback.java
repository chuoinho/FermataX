package me.aap.fermata.addon.stremio.ui.config;

import me.aap.fermata.addon.stremio.R;

/** Receives configuration state without exposing browsed URLs in errors or diagnostics. */
public interface StremioConfigCallback {
	void onConfigured(StremioConfigResult result);

	default void onLoadingChanged(boolean loading) {
	}

	default void onFailure(Failure failure) {
	}

	enum Failure {
		BLOCKED_NAVIGATION(R.string.stremio_config_blocked),
		LOAD_FAILED(R.string.stremio_config_load_failed),
		SSL_ERROR(R.string.stremio_config_load_failed),
		RENDERER_GONE(R.string.stremio_config_load_failed);

		private final int messageResource;

		Failure(int messageResource) {
			this.messageResource = messageResource;
		}

		public int messageResource() {
			return messageResource;
		}
	}
}
