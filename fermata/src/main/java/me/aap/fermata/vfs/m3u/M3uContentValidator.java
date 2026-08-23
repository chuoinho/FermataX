package me.aap.fermata.vfs.m3u;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.net.http.HttpFileDownloader.ContentValidator;
import me.aap.utils.net.http.HttpFileDownloader.Status;

/** Rejects common provider/CDN error bodies before they replace a valid playlist cache. */
public final class M3uContentValidator implements ContentValidator {
	private static final int MAX_VALIDATION_CHARS = 64 * 1024;

	@Override
	public void validate(Status status, File stagedFile) throws IOException {
		boolean header = false;
		boolean extInf = false;
		boolean media = false;
		int read = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				open(status, stagedFile), charset(status)))) {
			for (String line; (line = reader.readLine()) != null && read < MAX_VALIDATION_CHARS; ) {
				read += line.length() + 1;
				String value = line.trim();
				if (value.isEmpty()) continue;
				String lower = value.toLowerCase(Locale.ROOT);
				if (isErrorDocument(lower)) throw new IOException("Invalid M3U response body");
				if (lower.startsWith("#extm3u")) header = true;
				else if (lower.startsWith("#extinf:")) extInf = true;
				else if (!lower.startsWith("#") && (extInf || looksLikeMediaLocation(value)))
					media = true;
			}
		}

		if (!(media && (header || extInf || read > 0)))
			throw new IOException("Downloaded file does not contain playable M3U entries");
	}

	private static InputStream open(Status status, File file) throws IOException {
		InputStream input = new FileInputStream(file);
		String encoding = status.getContentEncoding();
		try {
			if ("gzip".equalsIgnoreCase(encoding)) return new GZIPInputStream(input);
			if ("deflate".equalsIgnoreCase(encoding)) return new InflaterInputStream(input);
			return input;
		} catch (IOException failure) {
			input.close();
			throw failure;
		}
	}

	private static Charset charset(Status status) {
		String name = status.getCharacterEncoding();
		if (name == null) return StandardCharsets.UTF_8;
		try {
			return Charset.forName(name);
		} catch (RuntimeException ignored) {
			return StandardCharsets.UTF_8;
		}
	}

	private static boolean looksLikeMediaLocation(String value) {
		return value.contains("://") || value.startsWith("/") || value.startsWith("content:");
	}

	private static boolean isErrorDocument(String lower) {
		return lower.startsWith("<!doctype") || lower.startsWith("<html") ||
				lower.startsWith("<?xml") || lower.startsWith("{") || lower.startsWith("[") ||
				lower.startsWith("403:") || lower.startsWith("404:") ||
				lower.startsWith("429:") || lower.startsWith("500:") ||
				lower.startsWith("502:") || lower.startsWith("503:") ||
				lower.contains("too many requests") || lower.contains("rate limit") ||
				lower.contains("access denied");
	}
}
