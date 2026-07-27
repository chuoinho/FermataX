package me.aap.fermata.addon.stremio.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StremioMetaIdentity(
		String metaKey,
		String identityScope,
		String durableMetaId,
		String canonicalIdentity) {
	private static final Pattern IMDB = Pattern.compile("(?i)^(?:imdb:)?(tt[0-9]{5,12})$");
	private static final char SEPARATOR = '\u001F';

	public StremioMetaIdentity {
		Objects.requireNonNull(metaKey, "metaKey");
		Objects.requireNonNull(identityScope, "identityScope");
		Objects.requireNonNull(durableMetaId, "durableMetaId");
	}

	public static StremioMetaIdentity create(String sourceUuid, String type,
			String providerMetaId, String candidateCanonicalIdentity) {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(providerMetaId, "providerMetaId");
		if (sourceUuid.isBlank()) throw new IllegalArgumentException("sourceUuid is blank");
		String normalizedType = type.trim().toLowerCase(Locale.ROOT);
		if (normalizedType.isEmpty()) throw new IllegalArgumentException("type is blank");
		if (providerMetaId.isBlank()) throw new IllegalArgumentException("providerMetaId is blank");

		String scope = sourceUuid;
		String durableId = providerMetaId;
		String canonical = null;
		if (candidateCanonicalIdentity != null) {
			Matcher imdb = IMDB.matcher(candidateCanonicalIdentity.trim());
			if (imdb.matches()) {
				durableId = imdb.group(1).toLowerCase(Locale.ROOT);
				scope = "canonical:imdb";
				canonical = "imdb:" + durableId;
			}
		}

		String input = scope + SEPARATOR + normalizedType + SEPARATOR + durableId;
		return new StremioMetaIdentity(hash192(input), scope, durableId, canonical);
	}

	public static String videoKey(String metaKey, String videoId) {
		Objects.requireNonNull(metaKey, "metaKey");
		Objects.requireNonNull(videoId, "videoId");
		return hash192(metaKey + SEPARATOR + videoId);
	}

	private static String hash192(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			byte[] shortened = new byte[24];
			System.arraycopy(digest, 0, shortened, 0, shortened.length);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(shortened);
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}
}
