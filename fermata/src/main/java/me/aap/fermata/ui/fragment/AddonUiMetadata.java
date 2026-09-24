package me.aap.fermata.ui.fragment;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.AddonInfo;

final class AddonUiMetadata {
	private AddonUiMetadata() {
	}

	static boolean isDashboardItem(AddonInfo info) {
		return info.hasFragment && info.hasCapability(AddonCapability.DASHBOARD);
	}

	static boolean isNavigationItem(AddonInfo info) {
		return info.hasFragment && info.hasCapability(AddonCapability.NAVIGATION);
	}

	static Role role(AddonInfo info) {
		if (info.hasCapability(AddonCapability.TV)) return Role.TV;
		if (info.hasCapability(AddonCapability.YOUTUBE)) return Role.YOUTUBE;
		if (info.hasCapability(AddonCapability.RADIO)) return Role.RADIO;
		if (info.hasCapability(AddonCapability.PODCAST)) return Role.PODCAST;
		if (info.hasCapability(AddonCapability.AUDIOBOOK)) return Role.AUDIOBOOK;
		if (info.hasCapability(AddonCapability.STREMIO)) return Role.STREMIO;
		if (info.hasCapability(AddonCapability.WEB)) return Role.WEB;
		return Role.GENERIC;
	}

	static int color(Role role) {
		return switch (role) {
			case TV -> 0xFF3B82F6;
			case YOUTUBE -> 0xFFEF4444;
			case RADIO -> 0xFF10B981;
			case PODCAST -> 0xFFF59E0B;
			case AUDIOBOOK -> 0xFF8B5CF6;
			case STREMIO -> 0xFFA855F7;
			case WEB -> 0xFF14B8A6;
			case GENERIC -> 0xFF10A37F;
		};
	}

	static int itemColor(String name, AddonInfo info) {
		if (info != null) return color(role(info));
		if (name == null) return 0xFF3B82F6;
		if (DashboardItems.FOLDERS.equals(name)) return 0xFF06B6D4;
		if (DashboardItems.FAVORITES.equals(name)) return 0xFFEC4899;
		if (DashboardItems.RECENT.equals(name)) return 0xFF94A3B8;
		if (DashboardItems.PLAYLISTS.equals(name)) return 0xFF6366F1;
		if ("chatgpt".equalsIgnoreCase(name)) return 0xFF10A37F;
		return 0xFF3B82F6;
	}

	static android.graphics.drawable.Drawable createBadgeDrawable(int color, float radius) {
		android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
		gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
		gd.setCornerRadius(radius);
		int bgColor = (color & 0x00FFFFFF) | 0x24000000;
		gd.setColor(bgColor);
		return gd;
	}

	static int priority(AddonInfo info) {
		return switch (role(info)) {
			case TV -> 0;
			case YOUTUBE -> 1;
			case RADIO -> 2;
			case PODCAST -> 3;
			case AUDIOBOOK -> 4;
			case WEB -> 5;
			case STREMIO -> 6;
			case GENERIC -> 7;
		};
	}

	enum Role {
		TV,
		YOUTUBE,
		RADIO,
		PODCAST,
		AUDIOBOOK,
		STREMIO,
		WEB,
		GENERIC
	}
}
