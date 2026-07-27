package me.aap.fermata.addon.stremio.session;

import java.util.Objects;

import me.aap.fermata.addon.stremio.security.StremioDurableTextPolicy;

/** Immutable playback progress projection without raw URLs, provider credentials or streams. */
public record StremioContinueEntry(
		StremioSessionItem item,
		long positionMs,
		long durationMs,
		long lastPlayedMs) {

	public StremioContinueEntry {
		Objects.requireNonNull(item, "item");
		if (hasAddress(item.title()) || hasAddress(item.subtitle()) ||
				StremioDurableTextPolicy.isTainted(item.title(), item.subtitle())) {
			throw new SecurityException("Continue metadata contains an unsafe address or secret");
		}
		if ((positionMs <= 0L) || (durationMs <= 0L) || (positionMs >= durationMs)) {
			throw new IllegalArgumentException("Continue progress must be incomplete and finite");
		}
		if (lastPlayedMs < 0L) {
			throw new IllegalArgumentException("lastPlayedMs cannot be negative");
		}
	}

	private static boolean hasAddress(String text) {
		String value = text.strip().toLowerCase(java.util.Locale.ROOT);
		return value.startsWith("www.") || value.contains("://") ||
				value.startsWith("file:") || value.startsWith("content:") ||
				value.startsWith("javascript:") || value.startsWith("intent:");
	}

	@Override
	public String toString() {
		return "StremioContinueEntry{stableId=" + item.stableId() +
				", positionMs=" + positionMs + ", durationMs=" + durationMs +
				", lastPlayedMs=" + lastPlayedMs + '}';
	}
}
