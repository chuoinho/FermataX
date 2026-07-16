package me.aap.fermata.addon.podcast.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public final class PodcastIds {
	private PodcastIds() {
	}

	public static String hash(String value) {
		return hash(value, 16);
	}

	public static String fullHash(String value) {
		return hash(value, 32);
	}

	private static String hash(String value, int length) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(Arrays.copyOf(digest, length));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}
}
