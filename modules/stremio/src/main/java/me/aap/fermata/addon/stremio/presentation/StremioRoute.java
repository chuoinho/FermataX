package me.aap.fermata.addon.stremio.presentation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Secret-free navigation routes. Raw provider URLs and stream targets never belong here. */
public sealed interface StremioRoute permits StremioRoute.Home, StremioRoute.Discover,
		StremioRoute.Search, StremioRoute.Details, StremioRoute.Streams,
		StremioRoute.Library {

	record Home() implements StremioRoute {
	}

	record Discover(String catalogKey, String genre, int skip,
			Map<String, List<String>> extras)
			implements StremioRoute {
		public Discover {
			catalogKey = validatedStableId(catalogKey);
			genre = Objects.requireNonNullElse(genre, "");
			if (skip < 0) throw new IllegalArgumentException("skip must not be negative");
			extras = immutableExtras(extras);
		}

		public Discover(String catalogKey, String genre, int skip) {
			this(catalogKey, genre, skip, Map.of());
		}
	}

	record Search(String query) implements StremioRoute {
		public Search {
			query = text(query, "query");
		}
	}

	record Details(String stableId, int season) implements StremioRoute {
		public Details {
			stableId = validatedStableId(stableId);
			if (season < -1) throw new IllegalArgumentException("season must not be less than -1");
		}

		public Details(String stableId) {
			this(stableId, -1);
		}
	}

	record Streams(String stableId, String providerSourceUuid) implements StremioRoute {
		public Streams {
			stableId = validatedStableId(stableId);
			providerSourceUuid = optionalStableId(providerSourceUuid);
		}

		public Streams(String stableId) {
			this(stableId, "");
		}
	}

	record Library(LibraryType type, LibrarySort sort) implements StremioRoute {
		public Library {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(sort, "sort");
		}

		public Library() {
			this(LibraryType.ALL, LibrarySort.RECENT);
		}
	}

	enum LibraryType { ALL, MOVIES, SERIES }

	enum LibrarySort { RECENT, TITLE }

	private static String validatedStableId(String value) {
		String id = text(value, "stableId");
		if (id.contains("://")) {
			throw new IllegalArgumentException("stableId must not contain a transport URL");
		}
		return id;
	}

	private static String optionalStableId(String value) {
		if ((value == null) || value.isBlank()) return "";
		return validatedStableId(value);
	}

	private static Map<String, List<String>> immutableExtras(
			Map<String, List<String>> extras) {
		Objects.requireNonNull(extras, "extras");
		if (extras.isEmpty()) return Map.of();
		Map<String, List<String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : extras.entrySet()) {
			String name = text(entry.getKey(), "extra name");
			if (name.equalsIgnoreCase("genre") || name.equalsIgnoreCase("skip")) {
				throw new IllegalArgumentException("genre and skip have dedicated route fields");
			}
			List<String> values = Objects.requireNonNull(entry.getValue(), "extra values")
					.stream().map(value -> text(value, "extra value")).toList();
			if (!values.isEmpty()) copy.put(name, values);
		}
		return copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
	}

	private static String text(String value, String name) {
		Objects.requireNonNull(value, name);
		String normalized = value.trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
		return normalized;
	}
}
