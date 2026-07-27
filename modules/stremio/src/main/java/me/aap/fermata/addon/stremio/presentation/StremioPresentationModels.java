package me.aap.fermata.addon.stremio.presentation;

import java.util.Objects;

import me.aap.fermata.addon.stremio.browse.BrowseMedia;

/** Pure factories shared by route loaders so UI models stay transport-free. */
final class StremioPresentationModels {
	private StremioPresentationModels() {
	}

	static StremioUiModel.Poster poster(String key, BrowseMedia media, float progress) {
		return new StremioUiModel.Poster(key, media.title(), posterSubtitle(media),
				optional(media.poster()), optional(media.background()), progress, false);
	}

	static StremioUiModel.StateRow state(
			String key, String message, StremioUiModel.StateKind kind) {
		return new StremioUiModel.StateRow(key, message, kind);
	}

	private static String posterSubtitle(BrowseMedia media) {
		if ((media.releaseInfo() != null) && !media.releaseInfo().isBlank()) {
			return media.releaseInfo();
		}
		return media.type();
	}

	private static String optional(String value) {
		return Objects.requireNonNullElse(value, "");
	}
}
