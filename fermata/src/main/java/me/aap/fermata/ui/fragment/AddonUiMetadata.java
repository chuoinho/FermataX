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
		if (info.hasCapability(AddonCapability.WEB)) return Role.WEB;
		if (info.hasCapability(AddonCapability.FELEX)) return Role.FELEX;
		return Role.GENERIC;
	}

	static int priority(AddonInfo info) {
		return switch (role(info)) {
			case TV -> 0;
			case YOUTUBE -> 1;
			case RADIO -> 2;
			case PODCAST -> 3;
			case WEB -> 4;
			case FELEX, GENERIC -> 8;
		};
	}

	enum Role {
		TV,
		YOUTUBE,
		RADIO,
		PODCAST,
		WEB,
		FELEX,
		GENERIC
	}
}
