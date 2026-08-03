package me.aap.fermata.addon.web.yt;

import me.aap.utils.net.NetUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Strict wire model for normalized ad events emitted by the YouTube page integration. */
record YoutubeAdSignal(Phase phase, String podId, String adId, String pageUrl, long generation) {
	private static final String PREFIX = "ytad1";

	static YoutubeAdSignal parse(String data) {
		if ((data == null) || data.isBlank()) return null;
		String[] fields = data.split("\\|", -1);
		if ((fields.length != 6) || !PREFIX.equals(fields[0])) return null;
		Phase phase = Phase.fromWire(decode(fields[1]));
		String podId = decode(fields[2]);
		String adId = decode(fields[3]);
		String pageUrl = decode(fields[4]);
		long generation;
		try {
			generation = Long.parseLong(fields[5]);
		} catch (NumberFormatException ignored) {
			return null;
		}
		if ((phase == null) || pageUrl.isBlank() || (generation <= 0L)) return null;
		if (phase.requiresPod() && podId.isBlank()) return null;
		if (phase.requiresAd() && adId.isBlank()) return null;
		return new YoutubeAdSignal(phase, podId, adId, pageUrl, generation);
	}

	private static String decode(String value) {
		try {
			return NetUtils.urlDecode(value);
		} catch (IllegalArgumentException ignored) {
			return "";
		}
	}

	enum Phase {
		POD_START("pod-start", true, false),
		AD_START("ad-start", true, true),
		AD_ERROR("ad-error", true, true),
		AD_COMPLETE("ad-complete", true, true),
		POD_COMPLETE("pod-complete", true, false),
		CONTENT("content", false, false);

		private final String wireName;
		private final boolean requiresPod;
		private final boolean requiresAd;

		Phase(String wireName, boolean requiresPod, boolean requiresAd) {
			this.wireName = wireName;
			this.requiresPod = requiresPod;
			this.requiresAd = requiresAd;
		}

		boolean requiresPod() {
			return requiresPod;
		}

		boolean requiresAd() {
			return requiresAd;
		}

		static Phase fromWire(String value) {
			for (Phase phase : values()) if (phase.wireName.equals(value)) return phase;
			return null;
		}
	}
}
