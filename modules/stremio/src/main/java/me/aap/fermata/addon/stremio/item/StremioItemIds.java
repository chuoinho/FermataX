package me.aap.fermata.addon.stremio.item;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;

/** Opaque deterministic IDs. Raw provider values are length-framed before hashing. */
public final class StremioItemIds {
	public static final String PREFIX = "stremio:";

	private StremioItemIds() {
	}

	public static String provider(String sourceUuid) {
		return id("provider", sourceUuid);
	}

	public static String catalog(CatalogDescriptor catalog) {
		var route = catalog.route();
		return id("catalog", route.sourceUuid(), route.type(), route.catalogId());
	}

	public static String genre(CatalogDescriptor catalog, String genre) {
		return id("genre", catalog(catalog), genre);
	}

	public static String page(CatalogDescriptor catalog, String genre, int skip) {
		return id("page", catalog(catalog), nullToEmpty(genre), Integer.toString(skip));
	}

	public static String meta(BrowseMedia media) {
		StremioCanonicalIdentity canonical =
				StremioCanonicalIdentity.from(media.type(), media.id());
		if (canonical != null) return id("meta", canonical.scope(), canonical.contentId());
		return id("meta", media.sourceUuid(), media.type(), media.id());
	}

	public static String season(BrowseMedia media, int season) {
		return id("season", meta(media), Integer.toString(season));
	}

	public static String episode(BrowseEpisode episode) {
		StremioCanonicalIdentity canonical = StremioCanonicalIdentity.from(
				episode.seriesType(), episode.seriesId());
		if (canonical != null) {
			return id("episode", canonical.scope(), canonical.contentId(),
					Integer.toString(episode.season()), Integer.toString(episode.episode()));
		}
		return id("episode", episode.sourceUuid(), episode.seriesType(), episode.seriesId(),
				episode.videoId());
	}

	public static String streamPicker(String sourceUuid, String type,
			String contentId, String videoId) {
		StremioCanonicalIdentity canonical = StremioCanonicalIdentity.from(type, contentId);
		if (canonical != null) {
			return id("streams", canonical.scope(), canonical.contentId(), videoId);
		}
		return id("streams", sourceUuid, type, contentId, videoId);
	}

	public static String stream(PlaybackDescriptor descriptor) {
		return id("stream", descriptor.descriptorId());
	}

	public static String search(String query) {
		return id("search", query);
	}

	public static boolean isStremioId(String id) {
		return (id != null) && id.startsWith(PREFIX);
	}

	private static String id(String kind, String... values) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, kind);
			for (String value : values) update(digest, Objects.requireNonNull(value, "id value"));
			byte[] hash = digest.digest();
			byte[] shortHash = java.util.Arrays.copyOf(hash, 12);
			return PREFIX + kind + ':' + Base64.getUrlEncoder().withoutPadding()
					.encodeToString(shortHash);
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("SHA-256 unavailable", ex);
		}
	}

	private static void update(MessageDigest digest, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update((byte) (bytes.length >>> 24));
		digest.update((byte) (bytes.length >>> 16));
		digest.update((byte) (bytes.length >>> 8));
		digest.update((byte) bytes.length);
		digest.update(bytes);
	}

	private static String nullToEmpty(String value) {
		return (value == null) ? "" : value;
	}
}
