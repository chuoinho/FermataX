package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.security.SecretTaintDetector;

final class StremioCacheDao {
	void put(SQLiteDatabase database, StremioCacheRecord cache) {
		if (cache.payload().length > StremioRepository.MAX_CACHE_ENTRY_BYTES) {
			throw new IllegalArgumentException("Stremio cache entry is too large");
		}
		assertUntainted(new String(cache.payload(), StandardCharsets.UTF_8));
		database.beginTransaction();
		try {
			assertOwner(database, cache);
			ContentValues values = StremioDataRows.cacheValues(cache);
			int updated = database.update("stremio_response_cache", values, "cache_key=?",
					new String[]{cache.cacheKey()});
			if (updated == 0) database.insertOrThrow("stremio_response_cache", null, values);
			prune(database);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	void delete(SQLiteDatabase database, String cacheKey) {
		database.delete("stremio_response_cache", "cache_key=?", new String[]{cacheKey});
	}

	StremioCacheRecord get(SQLiteDatabase database, String cacheKey) {
		try (Cursor cursor = database.query("stremio_response_cache", null, "cache_key=?",
				new String[]{cacheKey}, null, null, null)) {
			return cursor.moveToFirst() ? StremioDataRows.readCache(cursor) : null;
		}
	}

	private static void assertOwner(SQLiteDatabase database, StremioCacheRecord cache) {
		try (Cursor cursor = database.query("stremio_response_cache", new String[]{"source_uuid"},
				"cache_key=?", new String[]{cache.cacheKey()}, null, null, null)) {
			if (cursor.moveToFirst() && !cache.sourceUuid().equals(cursor.getString(0))) {
				throw new IllegalStateException("Stremio cache key collision: " + cache.cacheKey());
			}
		}
	}

	private static void prune(SQLiteDatabase database) {
		long rows;
		long bytes;
		try (Cursor cursor = database.rawQuery(
				"SELECT COUNT(*),COALESCE(SUM(LENGTH(payload)),0) " +
						"FROM stremio_response_cache", null)) {
			if (!cursor.moveToFirst()) return;
			rows = cursor.getLong(0);
			bytes = cursor.getLong(1);
		}
		if ((rows <= StremioRepository.MAX_CACHE_ROWS) &&
				(bytes <= StremioRepository.MAX_CACHE_BYTES)) return;

		List<String> evictions = new ArrayList<>();
		try (Cursor cursor = database.query("stremio_response_cache",
				new String[]{"cache_key", "LENGTH(payload)"}, null, null, null, null,
				"stored_ms ASC, cache_key ASC")) {
			while (cursor.moveToNext() &&
					((rows > StremioRepository.MAX_CACHE_ROWS) ||
							(bytes > StremioRepository.MAX_CACHE_BYTES))) {
				evictions.add(cursor.getString(0));
				rows--;
				bytes -= cursor.getLong(1);
			}
		}
		for (String key : evictions) {
			database.delete("stremio_response_cache", "cache_key=?", new String[]{key});
		}
	}

	private static void assertUntainted(String payload) {
		if (SecretTaintDetector.isTainted(payload)) {
			throw new SecurityException("Refusing tainted Stremio cache payload");
		}
	}
}
