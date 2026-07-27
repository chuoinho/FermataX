package me.aap.fermata.addon.stremio.presentation;

import java.util.Objects;

/** Explicit UI intent. Renderers never infer navigation from labels or key prefixes. */
public sealed interface StremioSelection permits StremioSelection.Navigate,
		StremioSelection.Restore, StremioSelection.Command, StremioSelection.Play,
		StremioSelection.Subtitles {

	record Navigate(StremioRoute route, boolean replace) implements StremioSelection {
		public Navigate(StremioRoute route) {
			this(route, false);
		}

		public Navigate {
			Objects.requireNonNull(route, "route");
		}
	}

	record Restore(String stableId, boolean streams) implements StremioSelection {
		public Restore {
			if ((stableId == null) || stableId.isBlank() || stableId.contains("://")) {
				throw new IllegalArgumentException("restore key must be opaque");
			}
		}
	}

	record Command(StremioUiModel.ActionKind action) implements StremioSelection {
		public Command {
			Objects.requireNonNull(action, "action");
		}
	}

	record Play(String stableKey) implements StremioSelection {
		public Play {
			if ((stableKey == null) || stableKey.isBlank() || stableKey.contains("://")) {
				throw new IllegalArgumentException("playback key must be opaque");
			}
		}
	}

	record Subtitles(String stableKey) implements StremioSelection {
		public Subtitles {
			if ((stableKey == null) || stableKey.isBlank() || stableKey.contains("://")) {
				throw new IllegalArgumentException("subtitle key must be opaque");
			}
		}
	}
}
