package me.aap.fermata.addon.podcast.feed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public final class PodcastHtmlDiscovery {
	static final int MAX_HTML_BYTES = 2 * 1024 * 1024;
	private static final int MAX_CANDIDATES = 16;

	public List<String> discover(InputStream input, String baseUrl) throws IOException {
		String html = read(input);
		Map<String, String> candidates = new LinkedHashMap<>();
		int offset = 0;

		while ((offset = indexOfIgnoreCase(html, "<link", offset)) != -1) {
			int end = tagEnd(html, offset + 5);
			if (end == -1) break;
			Map<String, String> attributes = attributes(html, offset + 5, end);
			String href = attributes.get("href");
			String type = lower(attributes.get("type"));
			String rel = lower(attributes.get("rel"));
			if ((href != null) && isFeedLink(type, rel)) {
				String resolved = resolve(baseUrl, decodeEntities(href));
				if (resolved != null) candidates.putIfAbsent(PodcastUrls.identity(resolved), resolved);
				if (candidates.size() == MAX_CANDIDATES) break;
			}
			offset = end + 1;
		}

		return List.copyOf(candidates.values());
	}

	private static String read(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(16 * 1024);
		byte[] buffer = new byte[8192];
		int total = 0;
		for (int read; (read = input.read(buffer)) != -1; ) {
			total += read;
			if (total > MAX_HTML_BYTES) {
				throw new PodcastException(PodcastErrorCode.TOO_LARGE,
						"Podcast discovery page is too large");
			}
			output.write(buffer, 0, read);
		}
		return output.toString(StandardCharsets.UTF_8);
	}

	private static boolean isFeedLink(String type, String rel) {
		boolean feedType = "application/rss+xml".equals(type) ||
				"application/atom+xml".equals(type) ||
				"application/xml".equals(type) || "text/xml".equals(type);
		if (!feedType) return false;
		return (rel == null) || rel.isEmpty() || containsToken(rel, "alternate") ||
				containsToken(rel, "feed");
	}

	private static boolean containsToken(String value, String token) {
		for (String part : value.split("\\s+")) if (token.equals(part)) return true;
		return false;
	}

	private static Map<String, String> attributes(String html, int start, int end) {
		Map<String, String> values = new LinkedHashMap<>();
		int i = start;
		while (i < end) {
			while ((i < end) && (Character.isWhitespace(html.charAt(i)) ||
					html.charAt(i) == '/')) i++;
			int nameStart = i;
			while ((i < end) && isName(html.charAt(i))) i++;
			if (nameStart == i) {
				i++;
				continue;
			}
			String name = lower(html.substring(nameStart, i));
			while ((i < end) && Character.isWhitespace(html.charAt(i))) i++;
			String value = "";
			if ((i < end) && (html.charAt(i) == '=')) {
				i++;
				while ((i < end) && Character.isWhitespace(html.charAt(i))) i++;
				if (i < end) {
					char quote = html.charAt(i);
					if ((quote == '\'') || (quote == '"')) {
						int valueStart = ++i;
						while ((i < end) && (html.charAt(i) != quote)) i++;
						value = html.substring(valueStart, i);
						if (i < end) i++;
					} else {
						int valueStart = i;
						while ((i < end) && !Character.isWhitespace(html.charAt(i)) &&
								html.charAt(i) != '>') i++;
						value = html.substring(valueStart, i);
					}
				}
			}
			values.putIfAbsent(name, value.trim());
		}
		return values;
	}

	private static int tagEnd(String html, int offset) {
		char quote = 0;
		for (int i = offset; i < html.length(); i++) {
			char c = html.charAt(i);
			if (quote != 0) {
				if (c == quote) quote = 0;
			} else if ((c == '\'') || (c == '"')) {
				quote = c;
			} else if (c == '>') {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfIgnoreCase(String text, String needle, int offset) {
		int limit = text.length() - needle.length();
		for (int i = Math.max(offset, 0); i <= limit; i++) {
			if (text.regionMatches(true, i, needle, 0, needle.length())) return i;
		}
		return -1;
	}

	private static boolean isName(char c) {
		return Character.isLetterOrDigit(c) || (c == '-') || (c == '_') || (c == ':');
	}

	private static String resolve(String base, String href) {
		try {
			URI resolved = new URI(base).resolve(href.trim());
			return PodcastUrls.normalizeHttpUrl(resolved.toString());
		} catch (URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	private static String decodeEntities(String value) {
		return value.replace("&amp;", "&").replace("&#38;", "&");
	}

	private static String lower(String value) {
		return (value == null) ? null : value.trim().toLowerCase(Locale.ROOT);
	}
}
