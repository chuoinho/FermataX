package me.aap.fermata.addon.podcast.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class PodcastUrls {
	private PodcastUrls() {
	}

	@Nullable
	public static String normalizeHttpUrl(@Nullable String value) {
		if (value == null) return null;
		value = value.trim();
		if (value.isEmpty()) return null;

		try {
			URI input = new URI(value).normalize();
			String scheme = lower(input.getScheme());
			if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
			String host = lower(input.getHost());
			if ((host == null) || host.isEmpty()) return null;
			int port = input.getPort();
			if (((port == 80) && "http".equals(scheme)) ||
					((port == 443) && "https".equals(scheme))) port = -1;
			String path = input.getRawPath();
			if ((path == null) || path.isEmpty()) path = "/";
			return composeHttpUrl(scheme, input.getRawUserInfo(), host, port, path,
					input.getRawQuery());
		} catch (URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	@NonNull
	public static String composeHttpUrl(String scheme, @Nullable String rawUserInfo, String host,
			int port, String rawPath, @Nullable String rawQuery) throws URISyntaxException {
		StringBuilder url = new StringBuilder(64).append(scheme).append("://");
		if ((rawUserInfo != null) && !rawUserInfo.isEmpty()) url.append(rawUserInfo).append('@');
		if ((host.indexOf(':') != -1) && !host.startsWith("[")) url.append('[').append(host).append(']');
		else url.append(host);
		if (port != -1) url.append(':').append(port);
		if ((rawPath == null) || rawPath.isEmpty()) url.append('/');
		else if (rawPath.charAt(0) == '/') url.append(rawPath);
		else url.append('/').append(rawPath);
		if ((rawQuery != null) && !rawQuery.isEmpty()) url.append('?').append(rawQuery);
		return new URI(url.toString()).normalize().toASCIIString();
	}

	@NonNull
	public static String identity(String normalizedUrl) {
		String normalized = normalizeHttpUrl(normalizedUrl);
		return (normalized == null) ? "" : normalized;
	}

	@Nullable
	private static String lower(@Nullable String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}
}
