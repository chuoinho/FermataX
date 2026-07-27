package me.aap.fermata.addon.stremio.presentation;

import java.util.List;
import java.util.Objects;

/** Immutable renderer input. It contains presentation data, never playable URLs or credentials. */
public sealed interface StremioUiModel permits StremioUiModel.Action,
		StremioUiModel.ActionBar,
		StremioUiModel.Section, StremioUiModel.Poster, StremioUiModel.Filter,
		StremioUiModel.DetailsHeader, StremioUiModel.Episode,
		StremioUiModel.StreamGroup, StremioUiModel.StreamChoice,
		StremioUiModel.StateRow {
	String stableKey();

	record Action(String stableKey, String title, ActionKind kind) implements StremioUiModel {
		public Action {
			stableKey = key(stableKey);
			title = text(title);
			Objects.requireNonNull(kind, "kind");
		}
	}

	record ActionBar(String stableKey, List<Action> actions) implements StremioUiModel {
		public ActionBar {
			stableKey = key(stableKey);
			actions = List.copyOf(actions);
			if (actions.isEmpty()) throw new IllegalArgumentException("action bar requires actions");
		}
	}

	record Section(String stableKey, String title, List<Poster> posters, Action seeAll)
			implements StremioUiModel {
		public Section(String stableKey, String title, List<Poster> posters) {
			this(stableKey, title, posters, null);
		}

		public Section {
			stableKey = key(stableKey);
			title = text(title);
			posters = List.copyOf(posters);
		}
	}

	record Poster(String stableKey, String title, String subtitle, String artwork,
			String fallbackArtwork, float progress, boolean progressDismissible)
			implements StremioUiModel {
		public Poster(String stableKey, String title, String subtitle, String artwork,
				float progress) {
			this(stableKey, title, subtitle, artwork, "", progress, false);
		}

		public Poster(String stableKey, String title, String subtitle, String artwork,
				float progress, boolean progressDismissible) {
			this(stableKey, title, subtitle, artwork, "", progress, progressDismissible);
		}

		public Poster {
			stableKey = key(stableKey);
			title = text(title);
			subtitle = optional(subtitle);
			artwork = optional(artwork);
			fallbackArtwork = optional(fallbackArtwork);
			if ((progress < 0f) || (progress > 1f)) {
				throw new IllegalArgumentException("progress must be between 0 and 1");
			}
		}
	}

	record Filter(String stableKey, String label, String selectedKey,
			List<Option> options) implements StremioUiModel {
		public Filter {
			stableKey = key(stableKey);
			label = text(label);
			selectedKey = optional(selectedKey);
			options = List.copyOf(options);
		}
	}

	record Option(String stableKey, String label) {
		public Option {
			stableKey = key(stableKey);
			label = text(label);
		}
	}

	record DetailsHeader(String stableKey, String title, String metadata,
			String overview, String poster, String backdrop, boolean watchable, boolean resumable,
			boolean favoriteSupported, boolean favorite, boolean subtitlesSupported)
			implements StremioUiModel {
		public DetailsHeader(String stableKey, String title, String metadata, String overview,
				String poster, String backdrop, boolean watchable, boolean resumable,
				boolean favoriteSupported, boolean favorite) {
			this(stableKey, title, metadata, overview, poster, backdrop, watchable, resumable,
					favoriteSupported, favorite, false);
		}

		public DetailsHeader {
			stableKey = key(stableKey);
			title = text(title);
			metadata = optional(metadata);
			overview = optional(overview);
			poster = optional(poster);
			backdrop = optional(backdrop);
		}
	}

	record Episode(String stableKey, String number, String title, String metadata,
			String thumbnail, float progress) implements StremioUiModel {
		public Episode {
			stableKey = key(stableKey);
			number = text(number);
			title = text(title);
			metadata = optional(metadata);
			thumbnail = optional(thumbnail);
			if ((progress < 0f) || (progress > 1f)) {
				throw new IllegalArgumentException("progress must be between 0 and 1");
			}
		}
	}

	record StreamGroup(String stableKey, String providerName, ProviderState state)
			implements StremioUiModel {
		public StreamGroup {
			stableKey = key(stableKey);
			providerName = text(providerName);
			Objects.requireNonNull(state, "state");
		}
	}

	record StreamChoice(String stableKey, String title, String details,
			boolean recommended) implements StremioUiModel {
		public StreamChoice {
			stableKey = key(stableKey);
			title = text(title);
			details = optional(details);
		}
	}

	record StateRow(String stableKey, String message, StateKind kind)
			implements StremioUiModel {
		public StateRow {
			stableKey = key(stableKey);
			message = text(message);
			Objects.requireNonNull(kind, "kind");
		}
	}

	enum ActionKind {
		SEARCH, DISCOVER, LIBRARY, ADDONS, WATCH, FAVORITE, SUBTITLES, RETRY, NEXT_PAGE
	}

	enum StateKind { LOADING, EMPTY, WARNING, ERROR }

	enum ProviderState { READY, LOADING, FAILED, TIMED_OUT }

	private static String key(String value) {
		String key = text(value);
		if (key.contains("://")) throw new IllegalArgumentException("UI key contains URL");
		return key;
	}

	private static String text(String value) {
		Objects.requireNonNull(value, "value");
		String normalized = value.trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException("value must not be empty");
		return normalized;
	}

	private static String optional(String value) {
		return Objects.requireNonNullElse(value, "");
	}
}
