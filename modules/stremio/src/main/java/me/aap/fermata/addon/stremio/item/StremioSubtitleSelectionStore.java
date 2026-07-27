package me.aap.fermata.addon.stremio.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;

/** Bounded process-local subtitle choice keyed by one immutable Stremio video identity. */
public final class StremioSubtitleSelectionStore {
	private static final int MAX_SELECTIONS = 128;
	private static final Map<String, Selection> selections =
			new LinkedHashMap<>(32, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Selection> eldest) {
					return size() > MAX_SELECTIONS;
				}
			};

	private StremioSubtitleSelectionStore() {
	}

	public static synchronized void select(String videoKey, SubtitleDescriptor descriptor) {
		Objects.requireNonNull(descriptor, "descriptor");
		selections.put(key(videoKey), new Selection(descriptor.identity(),
				StremioPlaybackResource.stableTrackId(descriptor.identity()),
				descriptor.language().tag(), false));
	}

	public static synchronized void disable(String videoKey) {
		selections.put(key(videoKey), new Selection("", null, "", true));
	}

	public static synchronized void useDefault(String videoKey) {
		selections.remove(key(videoKey));
	}

	public static synchronized Selection get(String videoKey) {
		return selections.get(key(videoKey));
	}

	private static String key(String videoKey) {
		Objects.requireNonNull(videoKey, "videoKey");
		if (videoKey.isBlank() || videoKey.contains("://")) {
			throw new IllegalArgumentException("Invalid Stremio video key");
		}
		return videoKey;
	}

	public record Selection(String identity, Long trackId, String language, boolean disabled) {
		public Selection {
			identity = Objects.requireNonNull(identity, "identity");
			language = Objects.requireNonNull(language, "language");
			if (disabled && (trackId != null || !identity.isEmpty() || !language.isEmpty())) {
				throw new IllegalArgumentException("Disabled subtitle selection must be empty");
			}
		}
	}
}
