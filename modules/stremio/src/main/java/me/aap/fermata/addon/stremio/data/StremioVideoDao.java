package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import me.aap.fermata.addon.stremio.security.StremioDurableTextPolicy;

final class StremioVideoDao {
	void put(SQLiteDatabase database, StremioVideoRecord video) {
		assertUntainted(video);
		database.beginTransaction();
		try {
			assertIdentity(database, video);
			ContentValues values = StremioDataRows.videoValues(video);
			int updated = database.update("stremio_video", values, "video_key=?",
					new String[]{video.videoKey()});
			if (updated == 0) database.insertOrThrow("stremio_video", null, values);
			StremioProgressFavoriteDao.pruneDurableRows(database,
					StremioRepository.MAX_META_ROWS, StremioRepository.MAX_VIDEO_ROWS,
					StremioRepository.MAX_PROGRESS_ROWS);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	StremioVideoRecord get(SQLiteDatabase database, String videoKey) {
		try (Cursor cursor = database.query("stremio_video", null, "video_key=?",
				new String[]{videoKey}, null, null, null)) {
			return cursor.moveToFirst() ? StremioDataRows.readVideo(cursor) : null;
		}
	}

	Map<String, StremioVideoRecord> readVideos(SQLiteDatabase database,
			Collection<String> keys) {
		Map<String, StremioVideoRecord> result = new LinkedHashMap<>();
		StremioDataRows.queryBatches(database, "stremio_video", "video_key", keys, cursor -> {
			StremioVideoRecord video = StremioDataRows.readVideo(cursor);
			result.put(video.videoKey(), video);
		});
		return result;
	}

	private static void assertIdentity(SQLiteDatabase database, StremioVideoRecord video) {
		try (Cursor cursor = database.query("stremio_video",
				new String[]{"meta_key", "type", "provider_video_id"}, "video_key=?",
				new String[]{video.videoKey()}, null, null, null)) {
			if (!cursor.moveToFirst()) return;
			if (!video.metaKey().equals(cursor.getString(0)) ||
					!video.type().equals(cursor.getString(1)) ||
					!video.providerVideoId().equals(cursor.getString(2))) {
				throw new IllegalStateException("Stremio video key collision: " + video.videoKey());
			}
		}
	}

	private static void assertUntainted(StremioVideoRecord video) {
		StremioDurableTextPolicy.requireUntainted("video metadata", video.type(),
				video.providerVideoId(), video.title(), video.thumbnailUrl());
	}
}
