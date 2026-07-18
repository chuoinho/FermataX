package me.aap.fermata.addon;

import java.util.EnumSet;
import java.util.Locale;

public enum AddonCapability {
	DASHBOARD,
	NAVIGATION,
	TV,
	RADIO,
	PODCAST,
	AUDIOBOOK,
	YOUTUBE,
	WEB,
	CHATGPT,
	VOICE_SEARCH;

	static EnumSet<AddonCapability> parse(String value) {
		EnumSet<AddonCapability> result = EnumSet.noneOf(AddonCapability.class);
		if ((value == null) || value.isBlank()) return result;
		for (String capability : value.split("[, \\[\\]]")) {
			if (!capability.isEmpty())
				result.add(valueOf(capability.toUpperCase(Locale.ROOT)));
		}
		return result;
	}
}
