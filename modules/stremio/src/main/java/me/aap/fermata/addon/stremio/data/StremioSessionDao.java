package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class StremioSessionDao {
	private final StremioSourceDao sourceDao;
	private final StremioMetaDao metaDao;
	private final StremioVideoDao videoDao;
	private final StremioProgressFavoriteDao progressDao;

	StremioSessionDao(StremioSourceDao sourceDao, StremioMetaDao metaDao,
			StremioVideoDao videoDao, StremioProgressFavoriteDao progressDao) {
		this.sourceDao = sourceDao;
		this.metaDao = metaDao;
		this.videoDao = videoDao;
		this.progressDao = progressDao;
	}

	StremioSessionData readData(SQLiteDatabase database, Collection<String> videoKeys) {
		Map<String, StremioVideoRecord> videos = videoDao.readVideos(database, videoKeys);
		Set<String> metaKeys = new LinkedHashSet<>();
		for (StremioVideoRecord video : videos.values()) metaKeys.add(video.metaKey());
		return new StremioSessionData(videos, metaDao.readMetadata(database, metaKeys),
				metaDao.readProviders(database, metaKeys), progressDao.readProgress(database, videoKeys),
				sourceDao.readState(database));
	}

	void putState(SQLiteDatabase database, StremioSessionRecord session) {
		database.beginTransaction();
		try {
			ContentValues values = new ContentValues();
			values.put("slot", 1);
			values.put("video_key", session.videoKey());
			values.put("back_to_list_id", session.backToListId());
			values.put("playback_generation", session.playbackGeneration());
			values.put("updated_ms", session.updatedMs());
			database.insertWithOnConflict("stremio_session_state", null, values,
					SQLiteDatabase.CONFLICT_REPLACE);
			StremioProgressFavoriteDao.pruneDurableRows(database,
					StremioRepository.MAX_META_ROWS, StremioRepository.MAX_VIDEO_ROWS,
					StremioRepository.MAX_PROGRESS_ROWS);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	StremioSessionRecord getState(SQLiteDatabase database) {
		try (Cursor cursor = database.query("stremio_session_state", null,
				"slot=1", null, null, null, null)) {
			if (!cursor.moveToFirst()) return null;
			return new StremioSessionRecord(StremioDataRows.string(cursor, "video_key"),
					StremioDataRows.string(cursor, "back_to_list_id"),
					cursor.getLong(cursor.getColumnIndexOrThrow("playback_generation")),
					cursor.getLong(cursor.getColumnIndexOrThrow("updated_ms")));
		}
	}
}
