package me.aap.fermata.addon.stremio.presentation;

/** Localized copy supplied by the Android renderer; the domain gateway owns no resources. */
public interface StremioPresentationText {
	String action(StremioUiModel.ActionKind kind);

	String label(Label kind);

	/** Converts manifest-defined catalog extra names into a compact localized fallback label. */
	default String extra(String name) {
		if ((name == null) || name.isBlank()) return "Filter";
		StringBuilder label = new StringBuilder(name.length() + 8);
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (i > 0 && Character.isUpperCase(c) &&
					Character.isLowerCase(name.charAt(i - 1))) label.append(' ');
			if ((i == 0) || (i > 0 && name.charAt(i - 1) == '_')) {
				label.append(Character.toUpperCase(c));
			} else if (c == '_') {
				label.append(' ');
			} else {
				label.append(c);
			}
		}
		return label.toString();
	}

	enum Label {
		CONTINUE_WATCHING,
		POPULAR_MOVIES,
		POPULAR_SERIES,
		NEW_MOVIES,
		NEW_SERIES,
		FEATURED_MOVIES,
		FEATURED_SERIES,
		NO_SOURCES,
		NO_CATALOGS,
		NO_CONTENT,
		TYPE,
		CATALOG,
		GENRE,
		MOVIES,
		SERIES,
		ALL_GENRES,
		ALL,
		SEASON,
		NO_DIRECT_STREAMS,
		PROVIDER,
		ALL_PROVIDERS,
		SEE_ALL,
		SORT,
		RATING,
		RECENT,
		TITLE,
		SAVED_MOVIES,
		SAVED_SERIES,
		LIBRARY_EMPTY,
		FAVORITE_UNAVAILABLE,
		CATALOG_REQUIRES_INPUT
	}
}
