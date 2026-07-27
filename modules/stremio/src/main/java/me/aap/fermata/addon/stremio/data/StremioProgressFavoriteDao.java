package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StremioProgressFavoriteDao {
	void putProgress(SQLiteDatabase database, StremioProgressRecord progress) {
		database.beginTransaction();
		try {
			ContentValues values = StremioDataRows.progressValues(progress);
			int updated = database.update("stremio_progress", values, "video_key=?",
					new String[]{progress.videoKey()});
			if (updated == 0) database.insertOrThrow("stremio_progress", null, values);
			pruneDurableRows(database, StremioRepository.MAX_META_ROWS,
					StremioRepository.MAX_VIDEO_ROWS, StremioRepository.MAX_PROGRESS_ROWS);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	void setFavoriteRetention(SQLiteDatabase database, String videoKey, boolean retained,
			long updatedMs) {
		database.beginTransaction();
		try {
			if (retained) {
				ContentValues values = new ContentValues();
				values.put("video_key", videoKey);
				values.put("reason", "favorite");
				values.put("updated_ms", updatedMs);
				database.insertWithOnConflict("stremio_retention_pin", null, values,
						SQLiteDatabase.CONFLICT_REPLACE);
			} else {
				database.delete("stremio_retention_pin", "video_key=?",
						new String[]{videoKey});
			}
			pruneDurableRows(database, StremioRepository.MAX_META_ROWS,
					StremioRepository.MAX_VIDEO_ROWS, StremioRepository.MAX_PROGRESS_ROWS);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	StremioProgressRecord getProgress(SQLiteDatabase database, String videoKey) {
		try (Cursor cursor = database.query("stremio_progress", null, "video_key=?",
				new String[]{videoKey}, null, null, null)) {
			return cursor.moveToFirst() ? StremioDataRows.readProgress(cursor) : null;
		}
	}

	List<StremioFavoriteRecord> readFavorites(SQLiteDatabase database, int limit) {
		List<StremioFavoriteRecord> favorites = new ArrayList<>(limit);
		try (Cursor cursor = database.query("stremio_retention_pin",
				new String[]{"video_key", "updated_ms"}, "reason='favorite'",
				null, null, null, "updated_ms DESC,video_key ASC", Integer.toString(limit))) {
			while (cursor.moveToNext()) {
				favorites.add(new StremioFavoriteRecord(cursor.getString(0), cursor.getLong(1)));
			}
		}
		return favorites;
	}

	Set<String> readFavoriteIds(SQLiteDatabase database, Collection<String> ids) {
		Set<String> result = new LinkedHashSet<>();
		StremioDataRows.queryBatches(database, "stremio_retention_pin", "video_key", ids,
				cursor -> result.add(StremioDataRows.string(cursor, "video_key")));
		return Set.copyOf(result);
	}

	Map<String, StremioProgressRecord> readProgress(SQLiteDatabase database,
			Collection<String> keys) {
		Map<String, StremioProgressRecord> result = new LinkedHashMap<>();
		StremioDataRows.queryBatches(database, "stremio_progress", "video_key", keys, cursor -> {
			StremioProgressRecord progress = StremioDataRows.readProgress(cursor);
			result.put(progress.videoKey(), progress);
		});
		return result;
	}

	boolean deleteProgress(SQLiteDatabase database, String stableId) {
		return database.delete("stremio_progress", "video_key=?",
				new String[]{stableId}) != 0;
	}

	List<StremioProgressRecord> listContinue(SQLiteDatabase database, int limit) {
		List<StremioProgressRecord> result = new ArrayList<>(limit);
		try (Cursor cursor = database.query("stremio_progress", null,
				"completed=0 AND position_ms>0 AND duration_ms>0 AND " +
						"position_ms<duration_ms AND last_played_ms>=0",
				null, null, null,
				"last_played_ms DESC,updated_ms DESC,video_key ASC",
				Integer.toString(limit))) {
			while (cursor.moveToNext()) result.add(StremioDataRows.readProgress(cursor));
		}
		return List.copyOf(result);
	}

	Set<String> readContinueKeys(SQLiteDatabase database, int limit) {
		Set<String> keys = new LinkedHashSet<>();
		try (Cursor cursor = database.query("stremio_progress", new String[]{"video_key"},
				"completed=0 AND position_ms>0 AND duration_ms>0 AND " +
						"position_ms<duration_ms AND last_played_ms>=0",
				null, null, null,
				"last_played_ms DESC,updated_ms DESC,video_key ASC",
				Integer.toString(limit))) {
			while (cursor.moveToNext()) keys.add(cursor.getString(0));
		}
		return keys;
	}

	static void pruneDurableRows(SQLiteDatabase database,
			int maxMetaRows, int maxVideoRows, int maxProgressRows) {
		if ((maxMetaRows < 0) || (maxVideoRows < 0) || (maxProgressRows < 0)) {
			throw new IllegalArgumentException("Retention limits cannot be negative");
		}
		pruneProgress(database, maxProgressRows);
		pruneVideos(database, maxVideoRows);
		pruneMetadata(database, maxMetaRows);
	}

	private static void pruneProgress(SQLiteDatabase database, int limit) {
		int overflow = countRows(database, "stremio_progress") - limit;
		if (overflow <= 0) return;
		deleteSelected(database, "stremio_progress", "video_key",
				"SELECT progress.video_key FROM stremio_progress progress " +
						"LEFT JOIN stremio_session_state session " +
						"ON session.video_key=progress.video_key " +
						"WHERE session.video_key IS NULL " +
						"ORDER BY progress.completed DESC,progress.last_played_ms ASC," +
						"progress.updated_ms ASC,progress.video_key ASC LIMIT ?", overflow);
	}

	private static void pruneVideos(SQLiteDatabase database, int limit) {
		int overflow = countRows(database, "stremio_video") - limit;
		if (overflow <= 0) return;
		deleteSelected(database, "stremio_video", "video_key",
				"SELECT video.video_key FROM stremio_video video " +
						"LEFT JOIN stremio_session_state session " +
						"ON session.video_key=video.video_key " +
						"LEFT JOIN stremio_retention_pin pin " +
						"ON pin.video_key=video.video_key " +
						"LEFT JOIN stremio_progress progress " +
						"ON progress.video_key=video.video_key " +
						"WHERE session.video_key IS NULL AND pin.video_key IS NULL " +
						"AND progress.video_key IS NULL " +
						"ORDER BY video.updated_ms ASC,video.video_key ASC LIMIT ?", overflow);
	}

	private static void pruneMetadata(SQLiteDatabase database, int limit) {
		int overflow = countRows(database, "stremio_meta") - limit;
		if (overflow <= 0) return;
		deleteSelected(database, "stremio_meta", "meta_key",
				"SELECT meta.meta_key FROM stremio_meta meta " +
						"LEFT JOIN stremio_video video ON video.meta_key=meta.meta_key " +
						"WHERE video.meta_key IS NULL " +
						"ORDER BY meta.updated_ms ASC,meta.meta_key ASC LIMIT ?", overflow);
	}

	private static int countRows(SQLiteDatabase database, String table) {
		try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
			return cursor.moveToFirst() ? cursor.getInt(0) : 0;
		}
	}

	private static void deleteSelected(SQLiteDatabase database, String table, String column,
			String query, int limit) {
		List<String> keys = new ArrayList<>(limit);
		try (Cursor cursor = database.rawQuery(query, new String[]{Integer.toString(limit)})) {
			while (cursor.moveToNext()) keys.add(cursor.getString(0));
		}
		for (String key : keys) {
			database.delete(table, column + "=?", new String[]{key});
		}
	}
}
