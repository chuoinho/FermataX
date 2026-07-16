package me.aap.fermata.addon.web;

final class WebBackNavigationPolicy {
	private WebBackNavigationPolicy() {
	}

	static Action resolve(boolean fullScreen, boolean canGoBack) {
		if (fullScreen) return Action.EXIT_FULLSCREEN;
		if (canGoBack) return Action.WEB_HISTORY;
		return Action.PARENT;
	}

	enum Action {
		EXIT_FULLSCREEN, WEB_HISTORY, PARENT
	}
}
