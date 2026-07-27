package me.aap.fermata.addon.stremio.session;

import java.util.Objects;

/** Provider search candidate. The gateway's rank is used before locale-aware tie breaking. */
public record StremioVoiceCandidate(
		String stableId,
		String sourceUuid,
		String title,
		String subtitle,
		int providerRank) {

	public StremioVoiceCandidate {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		sourceUuid = StremioSessionIds.requireOpaque(sourceUuid, "sourceUuid");
		title = StremioSessionIds.requireText(title, "title");
		subtitle = Objects.requireNonNullElse(subtitle, "");
		if (providerRank < 0) throw new IllegalArgumentException("providerRank cannot be negative");
	}

	@Override
	public String toString() {
		return "StremioVoiceCandidate{stableId=" + stableId +
				", sourceUuid=" + sourceUuid + ", metadata=<redacted>, rank=" +
				providerRank + '}';
	}
}
