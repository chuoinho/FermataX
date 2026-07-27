package me.aap.fermata.addon.stremio.subtitle;

import java.util.Objects;

public record SubtitleLanguage(String tag, String baseLanguage, Direction direction) {
	public static final SubtitleLanguage UNKNOWN = new SubtitleLanguage("und", "und", Direction.LTR);

	public SubtitleLanguage {
		Objects.requireNonNull(tag, "tag");
		Objects.requireNonNull(baseLanguage, "baseLanguage");
		Objects.requireNonNull(direction, "direction");
	}

	public boolean isUnknown() {
		return "und".equals(tag);
	}

	public enum Direction {
		LTR,
		RTL
	}
}
