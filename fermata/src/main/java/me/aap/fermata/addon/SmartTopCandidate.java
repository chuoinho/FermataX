package me.aap.fermata.addon;

import java.util.Locale;
import java.util.Objects;

/** Secret-free, transport-free candidate supplied by one loaded addon generation. */
public record SmartTopCandidate(
		String addonClass,
		long lifecycleGeneration,
		String opaqueId,
		Kind kind,
		boolean video,
		long positionMillis,
		long durationMillis,
		boolean completed,
		String title,
		String subtitle,
		boolean favoriteKnown,
		boolean favorite,
		long lastInteractionMillis) {
	public static final int MAX_TEXT_CHARS = 256;
	public static final int MAX_OPAQUE_ID_CHARS = 512;

	public SmartTopCandidate {
		addonClass = bounded(Objects.requireNonNull(addonClass, "addonClass"), MAX_TEXT_CHARS);
		opaqueId = bounded(Objects.requireNonNull(opaqueId, "opaqueId"), MAX_OPAQUE_ID_CHARS);
		kind = Objects.requireNonNull(kind, "kind");
		title = bounded(Objects.requireNonNull(title, "title"), MAX_TEXT_CHARS);
		subtitle = bounded(Objects.requireNonNullElse(subtitle, ""), MAX_TEXT_CHARS);
		if (addonClass.isBlank() || opaqueId.isBlank() || title.isBlank()) {
			throw new IllegalArgumentException("Candidate identity and title must not be blank");
		}
		if ((lifecycleGeneration <= 0L) || (positionMillis < 0L) ||
				(durationMillis < 0L) || (lastInteractionMillis < 0L)) {
			throw new IllegalArgumentException("Candidate values must not be negative");
		}
		if (containsAddress(title) || containsAddress(subtitle)) {
			throw new SecurityException("Candidate display text contains an address");
		}
		if ((kind == Kind.RESUME) &&
				!isMeaningfulResume(positionMillis, durationMillis, completed)) {
			throw new IllegalArgumentException("Resume candidate requires meaningful finite progress");
		}
		if (!favoriteKnown && favorite) {
			throw new IllegalArgumentException("Favorite value requires authoritative state");
		}
	}

	public boolean isMeaningfulResume() {
		return isMeaningfulResume(positionMillis, durationMillis, completed);
	}

	private static boolean isMeaningfulResume(long positionMillis, long durationMillis,
			boolean completed) {
		if (completed || (positionMillis < 30_000L) || (durationMillis <= 0L) ||
				(positionMillis >= durationMillis)) return false;
		long remaining = durationMillis - positionMillis;
		return (remaining >= 60_000L) &&
				(((double) positionMillis / (double) durationMillis) < 0.95D);
	}

	private static String bounded(String value, int max) {
		String text = value.strip();
		return (text.length() <= max) ? text : text.substring(0, max);
	}

	private static boolean containsAddress(String value) {
		String text = value.toLowerCase(Locale.ROOT);
		return text.contains("://") || text.startsWith("www.") ||
				text.startsWith("file:") || text.startsWith("content:") ||
				text.startsWith("javascript:") || text.startsWith("intent:");
	}

	public enum Kind {
		RESUME,
		RECOMMENDED
	}
}
