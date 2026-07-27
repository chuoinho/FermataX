package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class StremioDataRows {
	private static final int MAX_IN_CLAUSE = 400;

	private StremioDataRows() {
	}

	static ContentValues sourceValues(StremioSourceRecord source) {
		ContentValues values = new ContentValues();
		values.put("source_uuid", source.sourceUuid());
		values.put("transport_fingerprint", source.transportFingerprint());
		values.put("addon_id", source.addonId());
		values.put("name", source.name());
		values.put("version", source.version());
		values.put("redacted_transport_url", source.redactedTransportUrl());
		putNullable(values, "secret_ref", source.secretRef());
		values.put("enabled", source.enabled() ? 1 : 0);
		values.put("position", source.position());
		values.put("manifest_json", source.manifestJson());
		putNullable(values, "manifest_etag", source.manifestEtag());
		putNullable(values, "manifest_last_modified", source.manifestLastModified());
		values.put("last_checked_ms", source.lastCheckedMs());
		values.put("last_success_ms", source.lastSuccessMs());
		putNullable(values, "last_error_code", source.lastErrorCode());
		values.put("installed_ms", source.installedMs());
		values.put("updated_ms", source.updatedMs());
		values.put("allow_cleartext", source.allowCleartext() ? 1 : 0);
		values.put("allow_lan", source.allowLan() ? 1 : 0);
		return values;
	}

	static ContentValues metaValues(StremioMetaRecord meta) {
		ContentValues values = new ContentValues();
		values.put("meta_key", meta.metaKey());
		values.put("identity_scope", meta.identityScope());
		values.put("type", meta.type());
		values.put("provider_meta_id", meta.providerMetaId());
		putNullable(values, "canonical_identity", meta.canonicalIdentity());
		values.put("name", meta.name());
		values.put("description", meta.description());
		putNullable(values, "poster_url", meta.posterUrl());
		putNullable(values, "background_url", meta.backgroundUrl());
		putNullable(values, "logo_url", meta.logoUrl());
		putNullable(values, "release_info", meta.releaseInfo());
		values.put("runtime_ms", meta.runtimeMs());
		values.put("genres_json", meta.genresJson());
		values.put("updated_ms", meta.updatedMs());
		return values;
	}

	static ContentValues metaProviderValues(StremioMetaProviderRecord provider) {
		ContentValues values = new ContentValues();
		values.put("meta_key", provider.metaKey());
		values.put("source_uuid", provider.sourceUuid());
		values.put("provider_meta_id", provider.providerMetaId());
		values.put("priority", provider.priority());
		values.put("updated_ms", provider.updatedMs());
		return values;
	}

	static ContentValues videoValues(StremioVideoRecord video) {
		ContentValues values = new ContentValues();
		values.put("video_key", video.videoKey());
		values.put("meta_key", video.metaKey());
		values.put("type", video.type());
		values.put("provider_video_id", video.providerVideoId());
		values.put("title", video.title());
		putNullable(values, "season_no", video.seasonNumber());
		putNullable(values, "episode_no", video.episodeNumber());
		values.put("released_ms", video.releasedMs());
		values.put("duration_ms", video.durationMs());
		putNullable(values, "thumbnail_url", video.thumbnailUrl());
		values.put("updated_ms", video.updatedMs());
		return values;
	}

	static ContentValues progressValues(StremioProgressRecord progress) {
		ContentValues values = new ContentValues();
		values.put("video_key", progress.videoKey());
		values.put("position_ms", progress.positionMs());
		values.put("duration_ms", progress.durationMs());
		values.put("completed", progress.completed() ? 1 : 0);
		values.put("last_played_ms", progress.lastPlayedMs());
		values.put("updated_ms", progress.updatedMs());
		return values;
	}

	static ContentValues cacheValues(StremioCacheRecord cache) {
		ContentValues values = new ContentValues();
		values.put("cache_key", cache.cacheKey());
		values.put("source_uuid", cache.sourceUuid());
		values.put("resource", cache.resource());
		values.put("payload", cache.payload());
		putNullable(values, "etag", cache.etag());
		putNullable(values, "last_modified", cache.lastModified());
		values.put("stored_ms", cache.storedMs());
		values.put("fresh_until_ms", cache.freshUntilMs());
		values.put("stale_until_ms", cache.staleUntilMs());
		return values;
	}

	static StremioSourceRecord readSource(Cursor cursor) {
		return new StremioSourceRecord(string(cursor, "source_uuid"),
				string(cursor, "transport_fingerprint"), string(cursor, "addon_id"),
				string(cursor, "name"), string(cursor, "version"),
				string(cursor, "redacted_transport_url"), nullableString(cursor, "secret_ref"),
				integer(cursor, "enabled") != 0, integer(cursor, "position"),
				string(cursor, "manifest_json"), nullableString(cursor, "manifest_etag"),
				nullableString(cursor, "manifest_last_modified"), longValue(cursor, "last_checked_ms"),
				longValue(cursor, "last_success_ms"), nullableString(cursor, "last_error_code"),
				longValue(cursor, "installed_ms"), longValue(cursor, "updated_ms"),
				integer(cursor, "allow_cleartext") != 0, integer(cursor, "allow_lan") != 0);
	}

	static StremioMetaRecord readMeta(Cursor cursor) {
		return new StremioMetaRecord(string(cursor, "meta_key"), string(cursor, "identity_scope"),
				string(cursor, "type"), string(cursor, "provider_meta_id"),
				nullableString(cursor, "canonical_identity"), string(cursor, "name"),
				string(cursor, "description"), nullableString(cursor, "poster_url"),
				nullableString(cursor, "background_url"), nullableString(cursor, "logo_url"),
				nullableString(cursor, "release_info"), longValue(cursor, "runtime_ms"),
				string(cursor, "genres_json"), longValue(cursor, "updated_ms"));
	}

	static StremioVideoRecord readVideo(Cursor cursor) {
		return new StremioVideoRecord(string(cursor, "video_key"), string(cursor, "meta_key"),
				string(cursor, "type"), string(cursor, "provider_video_id"),
				string(cursor, "title"), nullableInteger(cursor, "season_no"),
				nullableInteger(cursor, "episode_no"), longValue(cursor, "released_ms"),
				longValue(cursor, "duration_ms"), nullableString(cursor, "thumbnail_url"),
				longValue(cursor, "updated_ms"));
	}

	static StremioProgressRecord readProgress(Cursor cursor) {
		return new StremioProgressRecord(string(cursor, "video_key"),
				longValue(cursor, "position_ms"), longValue(cursor, "duration_ms"),
				integer(cursor, "completed") != 0, longValue(cursor, "last_played_ms"),
				longValue(cursor, "updated_ms"));
	}

	static StremioCacheRecord readCache(Cursor cursor) {
		return new StremioCacheRecord(string(cursor, "cache_key"), string(cursor, "source_uuid"),
				string(cursor, "resource"), cursor.getBlob(index(cursor, "payload")),
				nullableString(cursor, "etag"), nullableString(cursor, "last_modified"),
				longValue(cursor, "stored_ms"), longValue(cursor, "fresh_until_ms"),
				longValue(cursor, "stale_until_ms"));
	}

	static void queryBatches(SQLiteDatabase database, String table, String column,
			Collection<String> keys, RowConsumer consumer) {
		queryBatches(database, table, column, keys, null, consumer);
	}

	static void queryBatches(SQLiteDatabase database, String table, String column,
			Collection<String> keys, String orderBy, RowConsumer consumer) {
		if (keys.isEmpty()) return;
		List<String> values = new ArrayList<>(keys);
		for (int from = 0; from < values.size(); from += MAX_IN_CLAUSE) {
			int to = Math.min(from + MAX_IN_CLAUSE, values.size());
			List<String> batch = values.subList(from, to);
			String selection = column + " IN (" + placeholders(batch.size()) + ')';
			try (Cursor cursor = database.query(table, null, selection,
					batch.toArray(new String[0]), null, null, orderBy)) {
				while (cursor.moveToNext()) consumer.accept(cursor);
			}
		}
	}

	static String string(Cursor cursor, String column) {
		return cursor.getString(index(cursor, column));
	}

	static int integer(Cursor cursor, String column) {
		return cursor.getInt(index(cursor, column));
	}

	static long longValue(Cursor cursor, String column) {
		return cursor.getLong(index(cursor, column));
	}

	private static String placeholders(int count) {
		StringBuilder result = new StringBuilder(count * 2);
		for (int i = 0; i < count; i++) {
			if (i != 0) result.append(',');
			result.append('?');
		}
		return result.toString();
	}

	private static void putNullable(ContentValues values, String key, String value) {
		if (value == null) values.putNull(key);
		else values.put(key, value);
	}

	private static void putNullable(ContentValues values, String key, Integer value) {
		if (value == null) values.putNull(key);
		else values.put(key, value);
	}

	private static int index(Cursor cursor, String column) {
		return cursor.getColumnIndexOrThrow(column);
	}

	private static String nullableString(Cursor cursor, String column) {
		int index = index(cursor, column);
		return cursor.isNull(index) ? null : cursor.getString(index);
	}

	private static Integer nullableInteger(Cursor cursor, String column) {
		int index = index(cursor, column);
		return cursor.isNull(index) ? null : cursor.getInt(index);
	}

	@FunctionalInterface
	interface RowConsumer {
		void accept(Cursor cursor);
	}
}
