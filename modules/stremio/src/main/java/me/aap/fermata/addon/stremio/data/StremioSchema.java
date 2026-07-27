package me.aap.fermata.addon.stremio.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.security.StremioDurableTextPolicy;

final class StremioSchema {
	static final int CURRENT_VERSION = 4;
	private static final String VERSION_KEY = "schema_version";

	private StremioSchema() {
	}

	static List<Migration> migrations() {
		return List.of(new Migration() {
			@Override
			public int version() {
				return 1;
			}

			@Override
			public void apply(SQLiteDatabase database) {
				createVersionOne(database);
			}
		}, new Migration() {
			@Override
			public int version() {
				return 2;
			}

			@Override
			public void apply(SQLiteDatabase database) {
				database.execSQL("ALTER TABLE stremio_addon ADD COLUMN " +
						"allow_cleartext INTEGER NOT NULL DEFAULT 0 " +
						"CHECK(allow_cleartext IN (0,1))");
				database.execSQL("ALTER TABLE stremio_addon ADD COLUMN " +
						"allow_lan INTEGER NOT NULL DEFAULT 0 CHECK(allow_lan IN (0,1))");
				// Preserve the only consent behavior that pre-v2 builds could infer after restart.
				database.execSQL("UPDATE stremio_addon SET allow_cleartext=1 " +
						"WHERE redacted_transport_url LIKE 'http://%'");
			}
		}, new Migration() {
			@Override
			public int version() {
				return 3;
			}

			@Override
			public void apply(SQLiteDatabase database) {
				database.execSQL("CREATE TABLE stremio_session_state (" +
						"slot INTEGER PRIMARY KEY CHECK(slot=1)," +
						"video_key TEXT NOT NULL," +
						"back_to_list_id TEXT NOT NULL," +
						"playback_generation INTEGER NOT NULL CHECK(playback_generation >= 0)," +
						"updated_ms INTEGER NOT NULL CHECK(updated_ms >= 0)," +
						"FOREIGN KEY(video_key) REFERENCES stremio_video(video_key) " +
						"ON DELETE CASCADE)");
			}
		}, new Migration() {
			@Override
			public int version() {
				return 4;
			}

			@Override
			public void apply(SQLiteDatabase database) {
				database.execSQL("CREATE TABLE stremio_retention_pin (" +
						"video_key TEXT PRIMARY KEY," +
						"reason TEXT NOT NULL CHECK(reason='favorite')," +
						"updated_ms INTEGER NOT NULL CHECK(updated_ms >= 0)," +
						"FOREIGN KEY(video_key) REFERENCES stremio_video(video_key) " +
						"ON DELETE CASCADE)");
				database.execSQL("CREATE INDEX stremio_meta_retention ON " +
						"stremio_meta(updated_ms,meta_key)");
				database.execSQL("CREATE INDEX stremio_video_retention ON " +
						"stremio_video(updated_ms,video_key)");
				scrubLegacyTaint(database);
			}
		});
	}

	static void migrate(SQLiteDatabase database, int targetVersion,
			List<Migration> migrations) {
		int version = readVersion(database);
		if (version > targetVersion) {
			throw new IllegalStateException("Stremio database is newer than this build: " + version);
		}

		while (version < targetVersion) {
			int next = version + 1;
			Migration migration = findMigration(migrations, next);
			if (migration == null) {
				throw new IllegalStateException("Missing Stremio migration to version " + next);
			}

			database.beginTransaction();
			try {
				migration.apply(database);
				writeVersion(database, next);
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
			}
			version = next;
		}
	}

	static int readVersion(SQLiteDatabase database) {
		if (!tableExists(database, "stremio_meta_state")) return 0;
		try (Cursor cursor = database.rawQuery(
				"SELECT value FROM stremio_meta_state WHERE key=?", new String[]{VERSION_KEY})) {
			if (!cursor.moveToFirst()) return 0;
			try {
				return Integer.parseInt(cursor.getString(0));
			} catch (NumberFormatException error) {
				throw new IllegalStateException("Invalid Stremio schema version", error);
			}
		}
	}

	private static Migration findMigration(List<Migration> migrations, int version) {
		for (Migration migration : migrations) {
			if (migration.version() == version) return migration;
		}
		return null;
	}

	private static void writeVersion(SQLiteDatabase database, int version) {
		database.execSQL("INSERT OR REPLACE INTO stremio_meta_state(key,value) VALUES(?,?)",
				new Object[]{VERSION_KEY, Integer.toString(version)});
	}

	private static boolean tableExists(SQLiteDatabase database, String table) {
		try (Cursor cursor = database.rawQuery(
				"SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table})) {
			return cursor.moveToFirst();
		}
	}

	private static void createVersionOne(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE stremio_meta_state (" +
				"key TEXT PRIMARY KEY," +
				"value TEXT NOT NULL)");
			database.execSQL("CREATE TABLE stremio_addon (" +
				"source_uuid TEXT PRIMARY KEY," +
				"transport_fingerprint TEXT NOT NULL UNIQUE," +
				"addon_id TEXT NOT NULL," +
				"name TEXT NOT NULL," +
				"version TEXT NOT NULL DEFAULT ''," +
				"redacted_transport_url TEXT NOT NULL," +
				"secret_ref TEXT," +
				"enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1))," +
				"position INTEGER NOT NULL," +
				"manifest_json TEXT NOT NULL," +
				"manifest_etag TEXT," +
				"manifest_last_modified TEXT," +
				"last_checked_ms INTEGER NOT NULL DEFAULT 0," +
				"last_success_ms INTEGER NOT NULL DEFAULT 0," +
				"last_error_code TEXT," +
				"installed_ms INTEGER NOT NULL," +
				"updated_ms INTEGER NOT NULL)");
		database.execSQL("CREATE TABLE stremio_meta (" +
				"meta_key TEXT PRIMARY KEY," +
				"identity_scope TEXT NOT NULL," +
				"type TEXT NOT NULL," +
				"provider_meta_id TEXT NOT NULL," +
				"canonical_identity TEXT," +
				"name TEXT NOT NULL," +
				"description TEXT NOT NULL DEFAULT ''," +
				"poster_url TEXT," +
				"background_url TEXT," +
				"logo_url TEXT," +
				"release_info TEXT," +
				"runtime_ms INTEGER NOT NULL DEFAULT -1," +
				"genres_json TEXT NOT NULL DEFAULT '[]'," +
				"updated_ms INTEGER NOT NULL," +
				"UNIQUE(identity_scope,type,provider_meta_id))");
		database.execSQL("CREATE TABLE stremio_meta_provider (" +
				"meta_key TEXT NOT NULL," +
				"source_uuid TEXT NOT NULL," +
				"provider_meta_id TEXT NOT NULL," +
				"priority INTEGER NOT NULL DEFAULT 0," +
				"updated_ms INTEGER NOT NULL," +
				"PRIMARY KEY(meta_key,source_uuid)," +
				"FOREIGN KEY(meta_key) REFERENCES stremio_meta(meta_key) ON DELETE CASCADE," +
				"FOREIGN KEY(source_uuid) REFERENCES stremio_addon(source_uuid) ON DELETE CASCADE)");
		database.execSQL("CREATE TABLE stremio_video (" +
				"video_key TEXT PRIMARY KEY," +
				"meta_key TEXT NOT NULL," +
				"type TEXT NOT NULL," +
				"provider_video_id TEXT NOT NULL," +
				"title TEXT NOT NULL," +
				"season_no INTEGER," +
				"episode_no INTEGER," +
				"released_ms INTEGER NOT NULL DEFAULT 0," +
				"duration_ms INTEGER NOT NULL DEFAULT -1," +
				"thumbnail_url TEXT," +
				"updated_ms INTEGER NOT NULL," +
				"UNIQUE(meta_key,provider_video_id)," +
				"FOREIGN KEY(meta_key) REFERENCES stremio_meta(meta_key) ON DELETE CASCADE)");
		database.execSQL("CREATE TABLE stremio_progress (" +
				"video_key TEXT PRIMARY KEY," +
				"position_ms INTEGER NOT NULL DEFAULT 0 CHECK(position_ms >= 0)," +
				"duration_ms INTEGER NOT NULL DEFAULT -1," +
				"completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1))," +
				"last_played_ms INTEGER NOT NULL DEFAULT 0," +
				"updated_ms INTEGER NOT NULL," +
				"FOREIGN KEY(video_key) REFERENCES stremio_video(video_key) ON DELETE CASCADE)");
		database.execSQL("CREATE TABLE stremio_response_cache (" +
				"cache_key TEXT PRIMARY KEY," +
				"source_uuid TEXT NOT NULL," +
				"resource TEXT NOT NULL," +
				"payload BLOB NOT NULL," +
				"etag TEXT," +
				"last_modified TEXT," +
				"stored_ms INTEGER NOT NULL," +
				"fresh_until_ms INTEGER NOT NULL," +
				"stale_until_ms INTEGER NOT NULL," +
				"CHECK(fresh_until_ms >= stored_ms)," +
				"CHECK(stale_until_ms >= fresh_until_ms)," +
				"FOREIGN KEY(source_uuid) REFERENCES stremio_addon(source_uuid) ON DELETE CASCADE)");
		database.execSQL("CREATE INDEX stremio_addon_position ON stremio_addon(enabled,position)");
		database.execSQL("CREATE INDEX stremio_meta_identity ON " +
				"stremio_meta(identity_scope,type,canonical_identity)");
		database.execSQL("CREATE INDEX stremio_video_series ON " +
				"stremio_video(meta_key,season_no,episode_no)");
		database.execSQL("CREATE INDEX stremio_progress_continue ON " +
				"stremio_progress(completed,last_played_ms DESC)");
		database.execSQL("CREATE INDEX stremio_cache_expiry ON " +
				"stremio_response_cache(stale_until_ms)");
	}

	private static void scrubLegacyTaint(SQLiteDatabase database) {
		List<String> taintedMeta = new ArrayList<>();
		try (Cursor cursor = database.query("stremio_meta", new String[]{"meta_key",
				"identity_scope", "type", "provider_meta_id", "canonical_identity", "name",
				"description", "poster_url", "background_url", "logo_url", "release_info",
				"genres_json"}, null, null, null, null, null)) {
			while (cursor.moveToNext()) {
				String[] fields = new String[cursor.getColumnCount() - 1];
				for (int i = 1; i < cursor.getColumnCount(); i++) {
					fields[i - 1] = cursor.isNull(i) ? null : cursor.getString(i);
				}
				if (StremioDurableTextPolicy.isTainted(fields)) taintedMeta.add(cursor.getString(0));
			}
		}
		for (String key : taintedMeta) {
			database.delete("stremio_meta", "meta_key=?", new String[]{key});
		}

		List<String> taintedVideo = new ArrayList<>();
		try (Cursor cursor = database.query("stremio_video", new String[]{"video_key", "type",
				"provider_video_id", "title", "thumbnail_url"}, null, null, null, null, null)) {
			while (cursor.moveToNext()) {
				String[] fields = new String[cursor.getColumnCount() - 1];
				for (int i = 1; i < cursor.getColumnCount(); i++) {
					fields[i - 1] = cursor.isNull(i) ? null : cursor.getString(i);
				}
				if (StremioDurableTextPolicy.isTainted(fields)) taintedVideo.add(cursor.getString(0));
			}
		}
		for (String key : taintedVideo) {
			database.delete("stremio_video", "video_key=?", new String[]{key});
		}
	}

	interface Migration {
		int version();

		void apply(SQLiteDatabase database);
	}
}
