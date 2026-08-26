package me.aap.fermata.addon.web.stremio;

import java.util.Locale;

final class StremioWebNavigationPolicy {
	private StremioWebNavigationPolicy() {
	}

	static boolean blocksExternalScheme(String scheme) {
		if (scheme == null) return false;
		return switch (scheme.toLowerCase(Locale.ROOT)) {
			case "intent", "stremio" -> true;
			default -> false;
		};
	}
}
