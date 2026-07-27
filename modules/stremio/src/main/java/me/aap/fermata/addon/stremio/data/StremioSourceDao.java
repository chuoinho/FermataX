package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.addon.stremio.security.SecretTaintDetector;

final class StremioSourceDao {
	private static final String SOURCE_REVISION_KEY = "source_index_revision";
	private static final String CINEMETA_HANDLED_KEY = "cinemeta_install_handled";

	void put(SQLiteDatabase database, StremioSourceRecord source) {
		assertUntainted(source.manifestJson());
		putRow(database, source);
	}

	StremioRepository.SourceState readState(SQLiteDatabase database) {
		long revision = parseLongState(database, SOURCE_REVISION_KEY, 0);
		boolean handled = parseLongState(database, CINEMETA_HANDLED_KEY, 0) != 0;
		List<StremioSourceRecord> sources = new ArrayList<>();
		try (Cursor cursor = database.query("stremio_addon", null, null, null,
				null, null, "position ASC")) {
			while (cursor.moveToNext()) sources.add(StremioDataRows.readSource(cursor));
		}
		return new StremioRepository.SourceState(revision, sources, handled);
	}

	boolean compareAndSet(SQLiteDatabase database, StremioRepository.SourceState expected,
			StremioRepository.SourceState replacement) {
		database.beginTransaction();
		try {
			if (!readState(database).equals(expected)) return false;
			for (StremioSourceRecord source : replacement.sources()) {
				assertUntainted(source.manifestJson());
				putRow(database, source);
			}

			Set<String> retained = new HashSet<>();
			for (StremioSourceRecord source : replacement.sources()) {
				retained.add(source.sourceUuid());
			}
			for (StremioSourceRecord source : expected.sources()) {
				if (!retained.contains(source.sourceUuid())) {
					deleteAndOrphans(database, source.sourceUuid());
				}
			}

			writeState(database, SOURCE_REVISION_KEY, Long.toString(replacement.revision()));
			writeState(database, CINEMETA_HANDLED_KEY,
					replacement.cinemetaInstallHandled() ? "1" : "0");
			database.setTransactionSuccessful();
			return true;
		} finally {
			database.endTransaction();
		}
	}

	StremioSourceRecord get(SQLiteDatabase database, String sourceUuid) {
		try (Cursor cursor = database.query("stremio_addon", null, "source_uuid=?",
				new String[]{sourceUuid}, null, null, null)) {
			return cursor.moveToFirst() ? StremioDataRows.readSource(cursor) : null;
		}
	}

	boolean delete(SQLiteDatabase database, String sourceUuid) {
		database.beginTransaction();
		try {
			boolean deleted = deleteAndOrphans(database, sourceUuid);
			database.setTransactionSuccessful();
			return deleted;
		} finally {
			database.endTransaction();
		}
	}

	static void validateState(StremioRepository.SourceState state) {
		Set<String> ids = new HashSet<>();
		Set<String> fingerprints = new HashSet<>();
		for (int i = 0; i < state.sources().size(); i++) {
			StremioSourceRecord source = state.sources().get(i);
			if ((source.position() != i) || !ids.add(source.sourceUuid()) ||
					!fingerprints.add(source.transportFingerprint())) {
				throw new IllegalArgumentException("Invalid Stremio source state");
			}
		}
	}

	private static void putRow(SQLiteDatabase database, StremioSourceRecord source) {
		ContentValues values = StremioDataRows.sourceValues(source);
		int updated = database.update("stremio_addon", values, "source_uuid=?",
				new String[]{source.sourceUuid()});
		if (updated == 0) database.insertOrThrow("stremio_addon", null, values);
	}

	/** Progress is retained only while at least one provider still owns its parent metadata. */
	private static boolean deleteAndOrphans(SQLiteDatabase database, String sourceUuid) {
		if (database.delete("stremio_addon", "source_uuid=?", new String[]{sourceUuid}) == 0) {
			return false;
		}
		database.execSQL("DELETE FROM stremio_meta WHERE NOT EXISTS (" +
				"SELECT 1 FROM stremio_meta_provider provider " +
				"WHERE provider.meta_key=stremio_meta.meta_key)");
		return true;
	}

	private static long parseLongState(SQLiteDatabase database, String key, long fallback) {
		try (Cursor cursor = database.rawQuery(
				"SELECT value FROM stremio_meta_state WHERE key=?", new String[]{key})) {
			if (!cursor.moveToFirst()) return fallback;
			try {
				return Long.parseLong(cursor.getString(0));
			} catch (NumberFormatException error) {
				throw new IllegalStateException("Invalid Stremio source state", error);
			}
		}
	}

	private static void writeState(SQLiteDatabase database, String key, String value) {
		database.execSQL("INSERT OR REPLACE INTO stremio_meta_state(key,value) VALUES(?,?)",
				new Object[]{key, value});
	}

	private static void assertUntainted(String manifest) {
		if (SecretTaintDetector.isManifestTainted(manifest)) {
			throw new SecurityException("Refusing tainted Stremio manifest");
		}
	}
}
