package me.aap.fermata.addon.web.stremio;

import java.net.URI;

/** Accepts only the opaque, hosted Stremio Player route used for an explicit car handoff. */
final class StremioOpenOnCarPolicy {
	private StremioOpenOnCarPolicy() {
	}

	static boolean acceptsPlayerRoute(String route) {
		if (!StremioWebSessionPolicy.isPlayerRoute(route)) return false;
		try {
			String fragment = URI.create(route).getRawFragment();
			return (fragment != null) && fragment.startsWith("/player/");
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	static boolean isCurrentPhoneSource(boolean phoneHost, boolean enabled, boolean foreground,
			boolean attached, Object expectedView, Object currentView, long generation,
			long currentGeneration) {
		return phoneHost && enabled && foreground && attached &&
				(expectedView != null) && (expectedView == currentView) &&
				(generation == currentGeneration);
	}

	/** Main-frame navigations need the same admission as the document-start bridge. */
	static boolean shouldInterceptMainFrameRoute(String route, boolean phoneHost, boolean enabled,
			boolean foreground, boolean attached) {
		return acceptsPlayerRoute(route) && phoneHost && enabled && foreground && attached;
	}
}
