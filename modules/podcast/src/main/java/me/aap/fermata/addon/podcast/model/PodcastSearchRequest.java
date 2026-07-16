package me.aap.fermata.addon.podcast.model;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.Locale;

public final class PodcastSearchRequest {
	private final String query;
	private final String country;
	private final String language;
	private final int limit;

	public PodcastSearchRequest(String query, Locale locale, int limit) {
		this.query = normalizeQuery(query);
		String country = locale.getCountry();
		this.country = (country == null) ? "" : country.toUpperCase(Locale.ROOT);
		String language = locale.getLanguage();
		this.language = (language == null) ? "" : language.toLowerCase(Locale.ROOT);
		this.limit = Math.max(1, Math.min(limit, 50));
	}

	@NonNull
	public String getQuery() {
		return query;
	}

	@NonNull
	public String getCountry() {
		return country;
	}

	@NonNull
	public String getLanguage() {
		return language;
	}

	public int getLimit() {
		return limit;
	}

	@NonNull
	public String cacheKey() {
		return query.toLowerCase(Locale.ROOT) + '\n' + country + '\n' + language + '\n' + limit;
	}

	private static String normalizeQuery(String query) {
		if (query == null) return "";
		query = Normalizer.normalize(query, Normalizer.Form.NFKC).trim();
		return (query.length() <= 200) ? query : query.substring(0, 200).trim();
	}
}
