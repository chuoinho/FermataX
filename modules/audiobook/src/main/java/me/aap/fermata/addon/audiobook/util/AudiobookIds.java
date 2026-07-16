package me.aap.fermata.addon.audiobook.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public final class AudiobookIds {
	private AudiobookIds() {
	}

	public static String source(String kind, String identity) {
		return kind + '-' + hash(identity, 12);
	}

	public static String book(String kind, String identity) {
		return kind + '-' + hash(identity, 16);
	}

	public static String chapter(String identity) {
		return "c-" + hash(identity, 16);
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
