package me.aap.fermata.addon.stremio.session;

import java.util.Objects;

import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;

/** Immutable DB-backed projection used by Recent, Favorites, SmartTop and voice. */
public record StremioSessionItem(
		String stableId,
		String canonicalContentKey,
		String sourceUuid,
		String title,
		String subtitle,
		String artwork,
		long durationMs,
		String backToListId,
		String episodeQueueId,
		int seasonNumber,
		int episodeNumber) {

	public StremioSessionItem {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		canonicalContentKey = StremioSessionIds.requireOpaque(
				canonicalContentKey, "canonicalContentKey");
		sourceUuid = StremioSessionIds.requireOpaque(sourceUuid, "sourceUuid");
		title = StremioSessionIds.requireText(title, "title");
		subtitle = Objects.requireNonNullElse(subtitle, "");
		artwork = ArtworkUrlSanitizer.sanitize(artwork);
		if (durationMs < -1L) throw new IllegalArgumentException("durationMs is invalid");
		backToListId = StremioSessionIds.requireOpaque(backToListId, "backToListId");
		if (episodeQueueId != null) {
			episodeQueueId = StremioSessionIds.requireOpaque(episodeQueueId, "episodeQueueId");
			if ((seasonNumber < 0) || (episodeNumber < 0)) {
				throw new IllegalArgumentException("episode coordinates cannot be negative");
			}
		} else if ((seasonNumber != -1) || (episodeNumber != -1)) {
			throw new IllegalArgumentException("non-episode items require -1 coordinates");
		}
	}

	public boolean isEpisode() {
		return episodeQueueId != null;
	}

	@Override
	public String toString() {
		return "StremioSessionItem{stableId=" + stableId +
				", canonicalContentKey=" + canonicalContentKey +
				", sourceUuid=" + sourceUuid + ", metadata=<redacted>, episode=" +
				isEpisode() + '}';
	}
}
