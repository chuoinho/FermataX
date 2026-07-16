package me.aap.fermata.addon.podcast.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import me.aap.fermata.addon.podcast.model.PodcastEpisode;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.fermata.addon.podcast.download.PodcastDownloadInfo;

final class PodcastDatabase {
	static final int SCHEMA_VERSION = 1;

	private PodcastDatabase() {
	}

	static void initialize(SQLiteDatabase database) {
		database.execSQL("PRAGMA foreign_keys=ON");
		database.beginTransaction();
		try {
			if (!tableExists(database, "podcast_meta")) createSchema(database);
			int version = schemaVersion(database);
			if (version != SCHEMA_VERSION) {
				throw new SQLiteException("Unsupported Podcast database schema: " + version);
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static void upsert(SQLiteDatabase database, PodcastSubscription subscription,
			List<PodcastStoredEpisode> episodes, long now) {
		database.beginTransaction();
		try {
			ContentValues insert = subscriptionValues(subscription, now);
			database.insertWithOnConflict("podcast_subscription", null, insert,
					SQLiteDatabase.CONFLICT_IGNORE);
			ContentValues update = subscriptionValues(subscription, now);
			update.remove("feed_key");
			update.remove("identity_hash");
			update.remove("subscribed_ms");
			database.update("podcast_subscription", update, "feed_key=?",
					new String[]{subscription.getFeedKey()});

			for (PodcastStoredEpisode stored : episodes) upsertEpisode(database,
					subscription.getFeedKey(), stored, now);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static List<PodcastSubscription> listSubscriptions(SQLiteDatabase database) {
		List<PodcastSubscription> result = new ArrayList<>();
		try (Cursor cursor = database.query("podcast_subscription", null, null, null,
				null, null, "title COLLATE NOCASE, subscribed_ms DESC")) {
			while (cursor.moveToNext()) result.add(readSubscription(cursor));
		}
		return result;
	}

	static PodcastSubscription getSubscription(SQLiteDatabase database, String feedKey) {
		try (Cursor cursor = database.query("podcast_subscription", null, "feed_key=?",
				new String[]{feedKey}, null, null, null, "1")) {
			return cursor.moveToFirst() ? readSubscription(cursor) : null;
		}
	}

	static int deleteSubscription(SQLiteDatabase database, String feedKey) {
		database.delete("podcast_download", "feed_key=?", new String[]{feedKey});
		return database.delete("podcast_subscription", "feed_key=?", new String[]{feedKey});
	}

	static Set<String> credentialRefs(SQLiteDatabase database, String feedKey) {
		Set<String> refs = new LinkedHashSet<>();
		try (Cursor cursor = database.query("podcast_subscription",
				new String[]{"credential_ref", "artwork_credential_ref"}, "feed_key=?",
				new String[]{feedKey}, null, null, null)) {
			if (cursor.moveToFirst()) {
				addRef(refs, cursor, 0);
				addRef(refs, cursor, 1);
			}
		}
		try (Cursor cursor = database.query("podcast_episode",
				new String[]{"media_credential_ref", "artwork_credential_ref"}, "feed_key=?",
				new String[]{feedKey}, null, null, null)) {
			while (cursor.moveToNext()) {
				addRef(refs, cursor, 0);
				addRef(refs, cursor, 1);
			}
		}
		return refs;
	}

	static List<PodcastEpisodeRecord> listEpisodes(SQLiteDatabase database, String feedKey,
			int limit, int offset) {
		List<PodcastEpisodeRecord> episodes = new ArrayList<>();
		String sql = episodeSelect() + " WHERE e.feed_key=? " +
				"ORDER BY e.publication_ms DESC, e.discovered_ms DESC LIMIT ? OFFSET ?";
		try (Cursor cursor = database.rawQuery(sql, new String[]{feedKey,
				Integer.toString(Math.max(1, Math.min(limit, 500))),
				Integer.toString(Math.max(offset, 0))})) {
			while (cursor.moveToNext()) episodes.add(readEpisode(cursor));
		}
		return episodes;
	}

	static List<PodcastEpisodeRecord> listContinue(SQLiteDatabase database, int limit) {
		String sql = episodeSelect() + " WHERE e.played=0 AND e.progress_ms>0 " +
				"ORDER BY e.last_played_ms DESC LIMIT ?";
		return queryEpisodes(database, sql,
				new String[]{Integer.toString(Math.max(1, Math.min(limit, 100)))});
	}

	static List<PodcastEpisodeRecord> listNewEpisodes(SQLiteDatabase database, int limit) {
		String sql = episodeSelect() + " WHERE e.played=0 ORDER BY e.publication_ms DESC, " +
				"e.discovered_ms DESC LIMIT ?";
		return queryEpisodes(database, sql,
				new String[]{Integer.toString(Math.max(1, Math.min(limit, 100)))});
	}

	static PodcastEpisodeRecord getEpisode(SQLiteDatabase database, String feedKey,
			String episodeKey) {
		String sql = episodeSelect() + " WHERE e.feed_key=? AND e.episode_key=? LIMIT 1";
		try (Cursor cursor = database.rawQuery(sql, new String[]{feedKey, episodeKey})) {
			return cursor.moveToFirst() ? readEpisode(cursor) : null;
		}
	}

	static void updateProgress(SQLiteDatabase database, String feedKey, String episodeKey,
			long positionMs, boolean played, long lastPlayedMs) {
		ContentValues values = new ContentValues();
		values.put("progress_ms", Math.max(positionMs, 0));
		values.put("played", played ? 1 : 0);
		values.put("last_played_ms", Math.max(lastPlayedMs, 0));
		values.put("updated_ms", System.currentTimeMillis());
		database.update("podcast_episode", values, "feed_key=? AND episode_key=?",
				new String[]{feedKey, episodeKey});
	}

	static void setPlayed(SQLiteDatabase database, String feedKey, String episodeKey,
			boolean played) {
		ContentValues values = new ContentValues();
		values.put("played", played ? 1 : 0);
		if (!played) {
			values.put("progress_ms", 0);
			values.put("last_played_ms", 0);
		}
		values.put("updated_ms", System.currentTimeMillis());
		database.update("podcast_episode", values, "feed_key=? AND episode_key=?",
				new String[]{feedKey, episodeKey});
	}

	static void refreshNotModified(SQLiteDatabase database, String feedKey, long checkedMs,
			String etag, String lastModified) {
		ContentValues values = new ContentValues();
		values.put("last_checked_ms", checkedMs);
		values.put("next_refresh_ms", 0);
		values.put("failure_count", 0);
		values.putNull("last_error_code");
		values.put("updated_ms", checkedMs);
		if (etag != null) values.put("etag", etag);
		if (lastModified != null) values.put("last_modified", lastModified);
		database.update("podcast_subscription", values, "feed_key=?", new String[]{feedKey});
	}

	static void refreshFailed(SQLiteDatabase database, String feedKey, long checkedMs,
			long nextRefreshMs, String errorCode) {
		database.execSQL("UPDATE podcast_subscription SET last_checked_ms=?, " +
				"next_refresh_ms=?, failure_count=failure_count+1, last_error_code=?, " +
				"updated_ms=? WHERE feed_key=?",
				new Object[]{checkedMs, nextRefreshMs, errorCode, checkedMs, feedKey});
	}

	static void updateDownload(SQLiteDatabase database, String feedKey, String episodeKey,
			int state, String localPath, String tempPath, long downloaded, long total,
			String etag, String lastModified, String errorCode) {
		ContentValues values = new ContentValues();
		values.put("feed_key", feedKey);
		values.put("episode_key", episodeKey);
		values.put("state", state);
		values.put("local_path", localPath);
		values.put("temp_path", tempPath);
		values.put("bytes_downloaded", Math.max(downloaded, 0));
		values.put("total_bytes", total);
		values.put("etag", etag);
		values.put("last_modified", lastModified);
		values.put("error_code", errorCode);
		values.put("updated_ms", System.currentTimeMillis());
		database.insertWithOnConflict("podcast_download", null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
	}

	static PodcastDownloadInfo getDownloadInfo(SQLiteDatabase database, String feedKey,
			String episodeKey) {
		try (Cursor cursor = database.query("podcast_download",
				new String[]{"etag", "last_modified"}, "feed_key=? AND episode_key=?",
				new String[]{feedKey, episodeKey}, null, null, null, "1")) {
			return cursor.moveToFirst() ? new PodcastDownloadInfo(
					nullable(cursor, "etag"), nullable(cursor, "last_modified")) :
					PodcastDownloadInfo.EMPTY;
		}
	}

	static void deleteDownload(SQLiteDatabase database, String feedKey, String episodeKey) {
		database.delete("podcast_download", "feed_key=? AND episode_key=?",
				new String[]{feedKey, episodeKey});
	}

	private static void upsertEpisode(SQLiteDatabase database, String feedKey,
			PodcastStoredEpisode stored, long now) {
		PodcastEpisode episode = stored.episode;
		String identityHash = PodcastIds.fullHash(
				episode.getIdentityKind().name() + '\n' + episode.getIdentity());
		try (Cursor cursor = database.query("podcast_episode", new String[]{"identity_hash"},
				"feed_key=? AND episode_key=?", new String[]{feedKey, episode.getKey()},
				null, null, null, "1")) {
			if (cursor.moveToFirst() && !identityHash.equals(cursor.getString(0))) {
				throw new SQLiteException("Podcast episode identity collision");
			}
		}
		ContentValues values = new ContentValues();
		values.put("identity_kind", episode.getIdentityKind().ordinal());
		values.put("identity_hash", identityHash);
		values.put("guid", episode.getGuid());
		values.put("title", episode.getTitle());
		values.put("description", episode.getDescription());
		values.put("author", episode.getAuthor());
		values.put("permalink", me.aap.fermata.addon.podcast.security.PodcastUrlRedactor
				.forStorage(episode.getPermalink()));
		values.put("media_url", stored.mediaUrl);
		values.put("media_credential_ref", stored.mediaCredentialRef);
		values.put("mime_type", episode.getMimeType());
		values.put("artwork_url", stored.artworkUrl);
		values.put("artwork_credential_ref", stored.artworkCredentialRef);
		values.put("publication_ms", episode.getPublicationMs());
		values.put("duration_ms", episode.getDurationMs());
		values.put("media_length", episode.getMediaLength());
		values.put("season_no", episode.getSeasonNumber());
		values.put("episode_no", episode.getEpisodeNumber());
		values.put("explicit", episode.isExplicit() ? 1 : 0);
		values.put("updated_ms", now);
		int changed = database.update("podcast_episode", values,
				"feed_key=? AND episode_key=?", new String[]{feedKey, episode.getKey()});
		if (changed != 0) return;

		values.put("feed_key", feedKey);
		values.put("episode_key", episode.getKey());
		values.put("played", 0);
		values.put("progress_ms", 0);
		values.put("last_played_ms", 0);
		values.put("discovered_ms", now);
		database.insertOrThrow("podcast_episode", null, values);
	}

	private static ContentValues subscriptionValues(PodcastSubscription subscription, long now) {
		ContentValues values = new ContentValues();
		values.put("feed_key", subscription.getFeedKey());
		values.put("identity_hash", PodcastIds.hash("feed\n" + subscription.getFeedKey()));
		values.put("canonical_url", subscription.getCanonicalUrl());
		values.put("credential_ref", subscription.getCredentialRef());
		values.put("title", subscription.getTitle());
		values.put("author", subscription.getAuthor());
		values.put("description", subscription.getDescription());
		values.put("artwork_url", subscription.getArtworkUrl());
		values.put("artwork_credential_ref", subscription.getArtworkCredentialRef());
		values.put("site_url", subscription.getWebsiteUrl());
		values.put("language", subscription.getLanguage());
		values.put("explicit", subscription.isExplicit() ? 1 : 0);
		values.put("etag", subscription.getEtag());
		values.put("last_modified", subscription.getLastModified());
		values.put("last_checked_ms", subscription.getLastCheckedMs());
		values.put("last_success_ms", subscription.getLastSuccessMs());
		values.put("next_refresh_ms", 0);
		values.put("failure_count", 0);
		values.putNull("last_error_code");
		values.put("subscribed_ms", subscription.getSubscribedMs());
		values.put("updated_ms", now);
		return values;
	}

	private static PodcastSubscription readSubscription(Cursor cursor) {
		return new PodcastSubscription(text(cursor, "feed_key"), text(cursor, "canonical_url"),
				nullable(cursor, "credential_ref"), text(cursor, "title"), text(cursor, "author"),
				text(cursor, "description"), text(cursor, "artwork_url"),
				nullable(cursor, "artwork_credential_ref"), text(cursor, "site_url"),
				text(cursor, "language"), number(cursor, "explicit") != 0,
				nullable(cursor, "etag"), nullable(cursor, "last_modified"),
				number(cursor, "last_checked_ms"), number(cursor, "last_success_ms"),
				number(cursor, "next_refresh_ms"), (int) number(cursor, "failure_count"),
				nullable(cursor, "last_error_code"),
				number(cursor, "subscribed_ms"));
	}

	private static PodcastEpisodeRecord readEpisode(Cursor cursor) {
		String artwork = text(cursor, "artwork_url");
		String artworkRef = nullable(cursor, "artwork_credential_ref");
		if (artwork.isEmpty()) {
			artwork = text(cursor, "feed_artwork");
			artworkRef = nullable(cursor, "feed_artwork_ref");
		}
		return new PodcastEpisodeRecord(text(cursor, "feed_key"), text(cursor, "episode_key"),
				text(cursor, "feed_title"), text(cursor, "title"), text(cursor, "description"),
				text(cursor, "author"), text(cursor, "media_url"),
				nullable(cursor, "media_credential_ref"), text(cursor, "mime_type"), artwork,
				artworkRef, number(cursor, "publication_ms"), number(cursor, "duration_ms"),
				number(cursor, "media_length"), number(cursor, "played") != 0,
				number(cursor, "progress_ms"), number(cursor, "last_played_ms"),
				(int) number(cursor, "download_state"), nullable(cursor, "download_local_path"));
	}

	private static String episodeSelect() {
		return "SELECT e.*, s.title AS feed_title, s.artwork_url AS feed_artwork, " +
				"s.artwork_credential_ref AS feed_artwork_ref, " +
				"COALESCE(d.state, 0) AS download_state, d.local_path AS download_local_path " +
				"FROM podcast_episode e JOIN podcast_subscription s ON s.feed_key=e.feed_key " +
				"LEFT JOIN podcast_download d ON d.feed_key=e.feed_key " +
				"AND d.episode_key=e.episode_key";
	}

	private static List<PodcastEpisodeRecord> queryEpisodes(SQLiteDatabase database, String sql,
			String[] arguments) {
		List<PodcastEpisodeRecord> episodes = new ArrayList<>();
		try (Cursor cursor = database.rawQuery(sql, arguments)) {
			while (cursor.moveToNext()) episodes.add(readEpisode(cursor));
		}
		return episodes;
	}

	private static String text(Cursor cursor, String column) {
		String value = nullable(cursor, column);
		return (value == null) ? "" : value;
	}

	private static String nullable(Cursor cursor, String column) {
		int index = cursor.getColumnIndexOrThrow(column);
		return cursor.isNull(index) ? null : cursor.getString(index);
	}

	private static long number(Cursor cursor, String column) {
		return cursor.getLong(cursor.getColumnIndexOrThrow(column));
	}

	private static void addRef(Set<String> refs, Cursor cursor, int column) {
		if (!cursor.isNull(column)) {
			String value = cursor.getString(column);
			if ((value != null) && !value.isEmpty()) refs.add(value);
		}
	}

	private static boolean tableExists(SQLiteDatabase database, String table) {
		return DatabaseUtils.longForQuery(database,
				"SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
				new String[]{table}) != 0;
	}

	private static int schemaVersion(SQLiteDatabase database) {
		String value = DatabaseUtils.stringForQuery(database,
				"SELECT value FROM podcast_meta WHERE key='schema_version'", null);
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw new SQLiteException("Invalid Podcast database schema version", ex);
		}
	}

	private static void createSchema(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE podcast_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
		database.execSQL("CREATE TABLE podcast_subscription (" +
				"feed_key TEXT PRIMARY KEY, identity_hash TEXT NOT NULL UNIQUE, " +
				"canonical_url TEXT NOT NULL, credential_ref TEXT, title TEXT NOT NULL DEFAULT '', " +
				"author TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', " +
				"artwork_url TEXT NOT NULL DEFAULT '', artwork_credential_ref TEXT, " +
				"site_url TEXT NOT NULL DEFAULT '', language TEXT NOT NULL DEFAULT '', " +
				"explicit INTEGER NOT NULL DEFAULT 0, etag TEXT, last_modified TEXT, " +
				"last_checked_ms INTEGER NOT NULL DEFAULT 0, last_success_ms INTEGER NOT NULL DEFAULT 0, " +
				"next_refresh_ms INTEGER NOT NULL DEFAULT 0, failure_count INTEGER NOT NULL DEFAULT 0, " +
				"last_error_code TEXT, subscribed_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)");
		database.execSQL("CREATE TABLE podcast_episode (" +
				"feed_key TEXT NOT NULL, episode_key TEXT NOT NULL, identity_kind INTEGER NOT NULL, " +
				"identity_hash TEXT NOT NULL, guid TEXT, title TEXT NOT NULL DEFAULT '', " +
				"description TEXT NOT NULL DEFAULT '', author TEXT NOT NULL DEFAULT '', permalink TEXT, " +
				"media_url TEXT NOT NULL, media_credential_ref TEXT, mime_type TEXT, " +
				"artwork_url TEXT NOT NULL DEFAULT '', artwork_credential_ref TEXT, " +
				"publication_ms INTEGER NOT NULL DEFAULT 0, duration_ms INTEGER NOT NULL DEFAULT -1, " +
				"media_length INTEGER NOT NULL DEFAULT -1, season_no INTEGER, episode_no INTEGER, " +
				"explicit INTEGER NOT NULL DEFAULT 0, " +
				"played INTEGER NOT NULL DEFAULT 0, progress_ms INTEGER NOT NULL DEFAULT 0, " +
				"last_played_ms INTEGER NOT NULL DEFAULT 0, discovered_ms INTEGER NOT NULL, " +
				"updated_ms INTEGER NOT NULL, PRIMARY KEY(feed_key, episode_key), " +
				"FOREIGN KEY(feed_key) REFERENCES podcast_subscription(feed_key) ON DELETE CASCADE)");
		database.execSQL("CREATE INDEX podcast_episode_new ON podcast_episode" +
				"(feed_key, played, publication_ms DESC)");
		database.execSQL("CREATE INDEX podcast_episode_continue ON podcast_episode" +
				"(played, last_played_ms DESC)");
		database.execSQL("CREATE TABLE podcast_download (feed_key TEXT NOT NULL, " +
				"episode_key TEXT NOT NULL, state INTEGER NOT NULL, local_path TEXT, temp_path TEXT, " +
				"bytes_downloaded INTEGER NOT NULL DEFAULT 0, total_bytes INTEGER NOT NULL DEFAULT -1, " +
				"etag TEXT, last_modified TEXT, error_code TEXT, updated_ms INTEGER NOT NULL, " +
				"PRIMARY KEY(feed_key, episode_key))");
		ContentValues version = new ContentValues();
		version.put("key", "schema_version");
		version.put("value", Integer.toString(SCHEMA_VERSION));
		database.insertOrThrow("podcast_meta", null, version);
	}
}
