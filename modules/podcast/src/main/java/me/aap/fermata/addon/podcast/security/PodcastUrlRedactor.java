package me.aap.fermata.addon.podcast.security;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.podcast.util.PodcastUrls;

public final class PodcastUrlRedactor {
	private PodcastUrlRedactor() {
	}

	public static boolean containsSecrets(@Nullable String value) {
		String normalized = PodcastUrls.normalizeHttpUrl(value);
		if (normalized == null) return false;
		try {
			URI uri = new URI(normalized);
			return (uri.getRawUserInfo() != null) || (uri.getRawQuery() != null);
		} catch (URISyntaxException ex) {
			return false;
		}
	}

	@Nullable
	public static String forStorage(@Nullable String value) {
		String normalized = PodcastUrls.normalizeHttpUrl(value);
		if (normalized == null) return null;
		try {
			URI uri = new URI(normalized);
			return PodcastUrls.composeHttpUrl(uri.getScheme(), null, uri.getHost(), uri.getPort(),
					uri.getRawPath(), queryNames(uri.getRawQuery()));
		} catch (URISyntaxException ex) {
			return null;
		}
	}

	public static String forMessage(@Nullable String value) {
		String redacted = forStorage(value);
		return (redacted == null) ? "<invalid podcast URL>" : redacted;
	}

	@Nullable
	private static String queryNames(@Nullable String query) {
		if ((query == null) || query.isEmpty()) return null;
		List<String> names = new ArrayList<>();
		for (String field : query.split("&")) {
			if (field.isEmpty()) continue;
			int equals = field.indexOf('=');
			String name = (equals == -1) ? field : field.substring(0, equals);
			if (!name.isEmpty()) names.add(name + "=%3Credacted%3E");
		}
		return names.isEmpty() ? null : String.join("&", names);
	}
}
