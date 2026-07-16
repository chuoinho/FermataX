package me.aap.fermata.addon.podcast.data;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient.DocumentRequest;
import me.aap.utils.async.FutureSupplier;

final class PodcastArtworkCache {
	private final File directory;
	private final PodcastHttpClient client;
	private final Map<String, FutureSupplier<String>> inFlight = new HashMap<>();

	PodcastArtworkCache(File directory, PodcastHttpClient client) {
		this.directory = directory;
		this.client = client;
	}

	FutureSupplier<String> resolve(PodcastPlaybackSource source) {
		String authorization = source.getHeaders().get("Authorization");
		if (authorization == null) return completed(source.getUrl());

		String key;
		try {
			key = hash(source.getUrl() + '\n' + authorization);
		} catch (Throwable error) {
			return failed(error);
		}

		File target = new File(directory, key + ".img");
		if (target.isFile()) return completed(Uri.fromFile(target).toString());
		return load(key, source, target, authorization);
	}

	private synchronized FutureSupplier<String> load(String key, PodcastPlaybackSource source,
			File target, String authorization) {
		if (target.isFile()) return completed(Uri.fromFile(target).toString());
		FutureSupplier<String> active = inFlight.get(key);
		if (active != null) return active;

		DocumentRequest request = new DocumentRequest(source.getUrl(), "image/*, */*;q=0.1",
				authorization, null, null, false);
		FutureSupplier<String> task = client.getDocument(request,
				(input, contentType, finalUrl) -> store(input, contentType, target))
				.map(response -> Uri.fromFile(response.getBody()).toString());
		inFlight.put(key, task);
		task.onCompletion((result, error) -> requestCompleted(key, task));
		return task;
	}

	private synchronized void requestCompleted(String key, FutureSupplier<String> task) {
		if (inFlight.get(key) == task) inFlight.remove(key);
	}

	private File store(InputStream input, String contentType, File target) throws IOException {
		if ((contentType != null) && contentType.toLowerCase(java.util.Locale.ROOT)
				.startsWith("text/")) {
			throw new IOException("Podcast artwork response is not an image");
		}
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Cannot create the Podcast artwork cache");
		}

		File partial = new File(directory, target.getName() + ".partial");
		if (partial.exists() && !partial.delete()) {
			throw new IOException("Cannot replace an incomplete Podcast artwork file");
		}
		try {
			try (FileOutputStream output = new FileOutputStream(partial)) {
				input.transferTo(output);
				output.getFD().sync();
			}
			if (!isSupportedImage(partial)) {
				throw new IOException("Podcast artwork response is not a supported image");
			}
			Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			return target;
		} catch (Throwable error) {
			partial.delete();
			if (error instanceof IOException io) throw io;
			throw new IOException(error);
		}
	}

	private static boolean isSupportedImage(File file) throws IOException {
		byte[] signature = new byte[12];
		int length;
		try (FileInputStream input = new FileInputStream(file)) {
			length = input.read(signature);
		}
		if (length >= 8 && matches(signature, 0, new int[] {137, 80, 78, 71, 13, 10, 26, 10})) {
			return true;
		}
		if (length >= 3 && matches(signature, 0, new int[] {255, 216, 255})) return true;
		if (length >= 6 && (matches(signature, 0, "GIF87a") || matches(signature, 0, "GIF89a"))) {
			return true;
		}
		if (length >= 12 && matches(signature, 0, "RIFF") && matches(signature, 8, "WEBP")) {
			return true;
		}
		return length >= 2 && signature[0] == 'B' && signature[1] == 'M';
	}

	private static boolean matches(byte[] value, int offset, String expected) {
		return matches(value, offset, expected.chars().toArray());
	}

	private static boolean matches(byte[] value, int offset, int[] expected) {
		if (offset + expected.length > value.length) return false;
		for (int i = 0; i < expected.length; i++) {
			if ((value[offset + i] & 0xff) != expected[i]) return false;
		}
		return true;
	}

	private static String hash(String value) throws NoSuchAlgorithmException {
		byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		StringBuilder result = new StringBuilder(digest.length * 2);
		for (byte part : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", part));
		return result.toString();
	}
}
