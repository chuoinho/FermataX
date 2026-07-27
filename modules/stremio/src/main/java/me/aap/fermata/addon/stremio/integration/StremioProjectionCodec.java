package me.aap.fermata.addon.stremio.integration;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.data.StremioCacheRecord;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

/** Versioned binary codec and opaque cache identity for durable item projections. */
final class StremioProjectionCodec {
	static final String ITEM_RESOURCE = "session-item-v1";
	private static final int VERSION = 1;
	private static final long CACHE_FOREVER = Long.MAX_VALUE;
	private static final Pattern URL_TITLE = Pattern.compile(
			"(?i)^(?:https?://|www\\.|file:|content:|javascript:|intent:).*");

	private StremioProjectionCodec() {
	}

	static String itemCacheKey(String stableId) {
		return "session:" + digest(ITEM_RESOURCE + ':' + stableId);
	}

	static StremioCacheRecord cache(String key, String owner,
			StremioPersistedItem projection) {
		long now = System.currentTimeMillis();
		return new StremioCacheRecord(key, owner, ITEM_RESOURCE, encode(projection),
				null, null, now, CACHE_FOREVER, CACHE_FOREVER);
	}

	static byte[] encode(StremioPersistedItem projection) {
		return encodeBytes(output -> {
			output.writeInt(VERSION);
			StremioSessionItem item = projection.item();
			output.writeUTF(item.stableId());
			output.writeUTF(item.canonicalContentKey());
			output.writeUTF(item.sourceUuid());
			output.writeUTF(item.title());
			output.writeUTF(item.subtitle());
			output.writeLong(item.durationMs());
			output.writeUTF(item.backToListId());
			writeNullable(output, item.episodeQueueId());
			output.writeInt(item.seasonNumber());
			output.writeInt(item.episodeNumber());
			output.writeUTF(projection.type());
			output.writeUTF(projection.contentId());
			writeNullable(output, projection.videoId());
		});
	}

	static StremioPersistedItem decode(byte[] payload) {
		return decodeBytes(payload, input -> {
			if (input.readInt() != VERSION) throw new IOException("Unsupported item record");
			String stableId = input.readUTF();
			String contentKey = input.readUTF();
			String sourceUuid = input.readUTF();
			String title = safeTitle(input.readUTF(), "Stremio");
			String subtitle = input.readUTF();
			long duration = input.readLong();
			String back = input.readUTF();
			String queue = readNullable(input);
			int season = input.readInt();
			int episodeNumber = input.readInt();
			String type = input.readUTF();
			String contentId = input.readUTF();
			String videoId = readNullable(input);
			StremioSessionItem item = new StremioSessionItem(stableId, contentKey,
					sourceUuid, title, subtitle, null, duration, back, queue,
					season, episodeNumber);
			BrowseMedia media = new BrowseMedia(sourceUuid, type, contentId, title,
					null, null, "", "", duration(duration), List.of(), null);
			BrowseEpisode episode = ((videoId == null) || (season < 0) ||
					(episodeNumber < 0)) ? null : new BrowseEpisode(sourceUuid,
					type, contentId, videoId, title, season, episodeNumber, null,
					null, "", duration(duration));
			return new StremioPersistedItem(item, type, contentId, videoId, media, episode);
		});
	}

	static String digest(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder(32);
			for (int i = 0; i < 16; i++) {
				result.append(String.format(Locale.ROOT, "%02x", bytes[i]));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static String safeTitle(@Nullable String title, @Nullable String fallback) {
		String value = (title == null) ? "" : title.strip();
		if (value.isEmpty() || URL_TITLE.matcher(value).matches()) {
			value = (fallback == null) ? "" : fallback.strip();
		}
		return (value.isEmpty() || URL_TITLE.matcher(value).matches()) ? "Stremio" : value;
	}

	@Nullable
	private static StremioDuration duration(long millis) {
		return (millis < 0) ? null : new StremioDuration(Long.toString(millis), millis);
	}

	private static void writeNullable(DataOutputStream output, @Nullable String value)
			throws IOException {
		output.writeBoolean(value != null);
		if (value != null) output.writeUTF(value);
	}

	@Nullable
	private static String readNullable(DataInputStream input) throws IOException {
		return input.readBoolean() ? input.readUTF() : null;
	}

	private static byte[] encodeBytes(IoWriter writer) {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				DataOutputStream output = new DataOutputStream(bytes)) {
			writer.write(output);
			output.flush();
			return bytes.toByteArray();
		} catch (IOException impossible) {
			throw new IllegalStateException("Unable to encode Stremio session state", impossible);
		}
	}

	private static <T> T decodeBytes(byte[] payload, IoReader<T> reader) {
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
			T value = reader.read(input);
			if (input.available() != 0) throw new IOException("Trailing session data");
			return value;
		} catch (IOException failure) {
			throw new IllegalStateException("Invalid Stremio session state", failure);
		}
	}

	@FunctionalInterface
	private interface IoWriter {
		void write(DataOutputStream output) throws IOException;
	}

	@FunctionalInterface
	private interface IoReader<T> {
		T read(DataInputStream input) throws IOException;
	}
}
