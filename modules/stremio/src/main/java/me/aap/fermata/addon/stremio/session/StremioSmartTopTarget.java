package me.aap.fermata.addon.stremio.session;

import java.util.Objects;

/** Exact current-item presentation and its deterministic back-to-list destination. */
public record StremioSmartTopTarget(
		String stableId,
		String title,
		String subtitle,
		String artwork,
		String backToListId,
		long durationMs) {

	public StremioSmartTopTarget {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		title = StremioSessionIds.requireText(title, "title");
		subtitle = Objects.requireNonNullElse(subtitle, "");
		backToListId = StremioSessionIds.requireOpaque(backToListId, "backToListId");
		if (durationMs < -1L) throw new IllegalArgumentException("durationMs is invalid");
	}

	static StremioSmartTopTarget from(StremioSessionItem item) {
		return new StremioSmartTopTarget(item.stableId(), item.title(), item.subtitle(),
				item.artwork(), item.backToListId(), item.durationMs());
	}

	@Override
	public String toString() {
		return "StremioSmartTopTarget{stableId=" + stableId +
				", backToListId=" + backToListId + ", metadata=<redacted>}";
	}
}
