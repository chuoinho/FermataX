package me.aap.fermata.ui.policy;

import java.util.Objects;

/** Stable playback identity plus the coordinator generation that owns visual presentation. */
public final class PlaybackPresentationOwner {
	private PlaybackPresentationOwner() {
	}

	public record Identity(int addonId, int engineId, String itemId) {
		public Identity {
			itemId = Objects.requireNonNullElse(itemId, "");
		}
	}

	public record Token(Identity identity, long generation) {
		public Token {
			Objects.requireNonNull(identity);
		}
	}
}
