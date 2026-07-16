package me.aap.fermata.addon.audiobook.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;

final class AudiobookDatabase {
	static final int SCHEMA_VERSION = 3;

	private AudiobookDatabase() {
	}

	static void initialize(SQLiteDatabase database) {
		database.execSQL("PRAGMA foreign_keys=ON");
		database.beginTransaction();
		try {
			if (!tableExists(database, "audiobook_meta")) createSchema(database);
			int version = migrate(database, schemaVersion(database));
			if (version != SCHEMA_VERSION) {
				throw new SQLiteException("Unsupported Audiobook database schema: " + version);
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static void replaceSourceBook(SQLiteDatabase database, AudiobookSource source,
			AudiobookBook book, List<AudiobookChapter> chapters) {
		database.beginTransaction();
		try {
			upsertSource(database, source);
			upsertBook(database, book);
			replaceChapters(database, book.getId(), chapters);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static void upsertCatalogBook(SQLiteDatabase database, AudiobookBook book,
			List<AudiobookChapter> chapters) {
		database.beginTransaction();
		try {
			upsertBook(database, book);
			for (AudiobookChapter chapter : chapters) upsertChapter(database, chapter);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static void upsertSourceBooks(SQLiteDatabase database, AudiobookSource source,
			List<AudiobookBook> books) {
		database.beginTransaction();
		try {
			upsertSource(database, source);
			for (AudiobookBook book : books) upsertBook(database, book);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static void replaceRemoteBook(SQLiteDatabase database, AudiobookSource source,
			AudiobookBook book, List<AudiobookChapter> chapters, long remoteProgressUpdatedMs) {
		database.beginTransaction();
		try {
			AudiobookBook current = getBook(database, book.getId());
			upsertSource(database, source);
			upsertBook(database, book);
			replaceChapters(database, book.getId(), chapters);
			if ((book.getProgressChapterId() != null) && ((current == null) ||
					(remoteProgressUpdatedMs > current.getLastPlayedMs()))) {
				updateProgress(database, book.getId(), book.getProgressChapterId(),
						book.getProgressMs(), book.isFinished(), remoteProgressUpdatedMs);
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	static List<AudiobookSource> listSources(SQLiteDatabase database) {
		List<AudiobookSource> result = new ArrayList<>();
		try (Cursor cursor = database.query("audiobook_source", null, null, null,
				null, null, "name COLLATE NOCASE, created_ms")) {
			while (cursor.moveToNext()) result.add(readSource(cursor));
		}
		return result;
	}

	static AudiobookSource getSource(SQLiteDatabase database, String id) {
		try (Cursor cursor = database.query("audiobook_source", null, "source_id=?",
				new String[]{id}, null, null, null, "1")) {
			return cursor.moveToFirst() ? readSource(cursor) : null;
		}
	}

	static boolean deleteSource(SQLiteDatabase database, String id) {
		return database.delete("audiobook_source", "source_id=?", new String[]{id}) != 0;
	}

	static List<AudiobookBook> listBooks(SQLiteDatabase database) {
		return listBooks(database, null, null,
				"title COLLATE NOCASE, added_ms DESC", 500);
	}

	static List<AudiobookBook> listBooksBySource(SQLiteDatabase database, String sourceId) {
		return listBooks(database, "source_id=?", new String[]{sourceId},
				"title COLLATE NOCASE, added_ms DESC", 500);
	}

	static List<AudiobookBook> listContinue(SQLiteDatabase database, int limit) {
		return listBooks(database, "last_played_ms>0 AND finished=0", null,
				"last_played_ms DESC", limit);
	}

	static List<AudiobookBook> listDownloaded(SQLiteDatabase database, int limit) {
		String where = "EXISTS (SELECT 1 FROM audiobook_chapter c " +
				"WHERE c.book_id=audiobook_book.book_id AND c.local_path IS NOT NULL)";
		return listBooks(database, where, null, "updated_ms DESC", limit);
	}

	static AudiobookBook getBook(SQLiteDatabase database, String id) {
		try (Cursor cursor = database.query("audiobook_book", null, "book_id=?",
				new String[]{id}, null, null, null, "1")) {
			return cursor.moveToFirst() ? readBook(cursor) : null;
		}
	}

	static List<AudiobookChapter> listChapters(SQLiteDatabase database, String bookId) {
		List<AudiobookChapter> result = new ArrayList<>();
		try (Cursor cursor = database.query("audiobook_chapter", null, "book_id=?",
				new String[]{bookId}, null, null, "chapter_index, chapter_id")) {
			while (cursor.moveToNext()) result.add(readChapter(cursor));
		}
		return result;
	}

	static AudiobookChapter getChapter(SQLiteDatabase database, String bookId, String chapterId) {
		try (Cursor cursor = database.query("audiobook_chapter", null,
				"book_id=? AND chapter_id=?", new String[]{bookId, chapterId},
				null, null, null, "1")) {
			return cursor.moveToFirst() ? readChapter(cursor) : null;
		}
	}

	static void updateProgress(SQLiteDatabase database, String bookId, String chapterId,
			long positionMs, boolean finished, long now) {
		ContentValues values = new ContentValues();
		values.put("progress_chapter_id", chapterId);
		values.put("progress_ms", Math.max(positionMs, 0));
		values.put("last_played_ms", now);
		values.put("finished", finished ? 1 : 0);
		values.put("updated_ms", now);
		database.update("audiobook_book", values, "book_id=?", new String[]{bookId});
	}

	static void updateDownload(SQLiteDatabase database, String bookId, String chapterId,
			int state, String localPath) {
		ContentValues values = new ContentValues();
		values.put("download_state", state);
		if (localPath == null) values.putNull("local_path");
		else values.put("local_path", localPath);
		database.update("audiobook_chapter", values, "book_id=? AND chapter_id=?",
				new String[]{bookId, chapterId});
	}

	static void clearDownloads(SQLiteDatabase database, String bookId) {
		ContentValues values = new ContentValues();
		values.put("download_state", 0);
		values.putNull("local_path");
		database.update("audiobook_chapter", values, "book_id=?", new String[]{bookId});
	}

	private static void upsertSource(SQLiteDatabase database, AudiobookSource source) {
		ContentValues values = sourceValues(source);
		database.insertWithOnConflict("audiobook_source", null, values,
				SQLiteDatabase.CONFLICT_IGNORE);
		values.remove("source_id");
		values.remove("created_ms");
		database.update("audiobook_source", values, "source_id=?",
				new String[]{source.getId()});
	}

	private static void upsertBook(SQLiteDatabase database, AudiobookBook book) {
		ContentValues values = bookValues(book);
		database.insertWithOnConflict("audiobook_book", null, values,
				SQLiteDatabase.CONFLICT_IGNORE);
		values.remove("book_id");
		values.remove("progress_chapter_id");
		values.remove("progress_ms");
		values.remove("last_played_ms");
		values.remove("finished");
		values.remove("added_ms");
		database.update("audiobook_book", values, "book_id=?", new String[]{book.getId()});
	}

	private static void upsertChapter(SQLiteDatabase database, AudiobookChapter chapter) {
		ContentValues values = chapterValues(chapter);
		database.insertWithOnConflict("audiobook_chapter", null, values,
				SQLiteDatabase.CONFLICT_IGNORE);
		values.remove("book_id");
		values.remove("chapter_id");
		values.remove("local_path");
		values.remove("download_state");
		database.update("audiobook_chapter", values, "book_id=? AND chapter_id=?",
				new String[]{chapter.getBookId(), chapter.getId()});
	}

	private static void replaceChapters(SQLiteDatabase database, String bookId,
			List<AudiobookChapter> chapters) {
		if (chapters.isEmpty()) {
			database.delete("audiobook_chapter", "book_id=?", new String[]{bookId});
			return;
		}
		StringBuilder selection = new StringBuilder("book_id=? AND chapter_id NOT IN (");
		String[] args = new String[chapters.size() + 1];
		args[0] = bookId;
		for (int index = 0; index < chapters.size(); index++) {
			if (index != 0) selection.append(',');
			selection.append('?');
			args[index + 1] = chapters.get(index).getId();
		}
		selection.append(')');
		database.delete("audiobook_chapter", selection.toString(), args);
		for (AudiobookChapter chapter : chapters) upsertChapter(database, chapter);
	}

	private static List<AudiobookBook> listBooks(SQLiteDatabase database, String selection,
			String[] args, String order, int limit) {
		List<AudiobookBook> result = new ArrayList<>();
		try (Cursor cursor = database.query("audiobook_book", null, selection, args,
				null, null, order, Integer.toString(Math.max(limit, 1)))) {
			while (cursor.moveToNext()) result.add(readBook(cursor));
		}
		return result;
	}

	private static ContentValues sourceValues(AudiobookSource source) {
		ContentValues values = new ContentValues();
		values.put("source_id", source.getId());
		values.put("source_type", source.getType().id());
		values.put("name", source.getName());
		values.put("endpoint", source.getEndpoint());
		values.put("credential_ref", source.getCredentialRef());
		values.put("created_ms", source.getCreatedMs());
		values.put("updated_ms", source.getUpdatedMs());
		return values;
	}

	private static ContentValues bookValues(AudiobookBook book) {
		ContentValues values = new ContentValues();
		values.put("book_id", book.getId());
		values.put("source_id", book.getSourceId());
		values.put("remote_id", book.getRemoteId());
		values.put("title", book.getTitle());
		values.put("author", book.getAuthor());
		values.put("narrator", book.getNarrator());
		values.put("description", book.getDescription());
		values.put("artwork_url", book.getArtworkUrl());
		values.put("language", book.getLanguage());
		values.put("duration_ms", book.getDurationMs());
		values.put("progress_chapter_id", book.getProgressChapterId());
		values.put("progress_ms", book.getProgressMs());
		values.put("last_played_ms", book.getLastPlayedMs());
		values.put("finished", book.isFinished() ? 1 : 0);
		values.put("added_ms", book.getAddedMs());
		values.put("updated_ms", book.getUpdatedMs());
		return values;
	}

	private static ContentValues chapterValues(AudiobookChapter chapter) {
		ContentValues values = new ContentValues();
		values.put("book_id", chapter.getBookId());
		values.put("chapter_id", chapter.getId());
		values.put("chapter_index", chapter.getIndex());
		values.put("title", chapter.getTitle());
		values.put("media_url", chapter.getMediaUrl());
		values.put("mime_type", chapter.getMimeType());
		values.put("offset_ms", chapter.getOffsetMs());
		values.put("book_offset_ms", chapter.getBookOffsetMs());
		values.put("duration_ms", chapter.getDurationMs());
		values.put("is_segment", chapter.isSegment() ? 1 : 0);
		values.put("local_path", chapter.getLocalPath());
		values.put("download_state", chapter.getDownloadState());
		return values;
	}

	private static AudiobookSource readSource(Cursor cursor) {
		return new AudiobookSource(string(cursor, "source_id"),
				AudiobookSourceType.fromId(integer(cursor, "source_type")), string(cursor, "name"),
				string(cursor, "endpoint"), nullable(cursor, "credential_ref"),
				longValue(cursor, "created_ms"), longValue(cursor, "updated_ms"));
	}

	private static AudiobookBook readBook(Cursor cursor) {
		return new AudiobookBook(string(cursor, "book_id"), nullable(cursor, "source_id"),
				nullable(cursor, "remote_id"), string(cursor, "title"), string(cursor, "author"),
				string(cursor, "narrator"), string(cursor, "description"),
				string(cursor, "artwork_url"), string(cursor, "language"),
				longValue(cursor, "duration_ms"), nullable(cursor, "progress_chapter_id"),
				longValue(cursor, "progress_ms"), longValue(cursor, "last_played_ms"),
				integer(cursor, "finished") != 0, longValue(cursor, "added_ms"),
				longValue(cursor, "updated_ms"));
	}

	private static AudiobookChapter readChapter(Cursor cursor) {
		return new AudiobookChapter(string(cursor, "book_id"), string(cursor, "chapter_id"),
				integer(cursor, "chapter_index"), string(cursor, "title"),
				string(cursor, "media_url"), string(cursor, "mime_type"),
				longValue(cursor, "offset_ms"), longValue(cursor, "book_offset_ms"),
				longValue(cursor, "duration_ms"),
				integer(cursor, "is_segment") != 0,
				nullable(cursor, "local_path"), integer(cursor, "download_state"));
	}

	private static int migrate(SQLiteDatabase database, int version) {
		if (version == 1) {
			database.execSQL("ALTER TABLE audiobook_chapter ADD COLUMN " +
					"is_segment INTEGER NOT NULL DEFAULT 0");
			ContentValues values = new ContentValues();
			values.put("value", "2");
			database.update("audiobook_meta", values, "key='schema_version'", null);
			version = 2;
		}
		if (version == 2) {
			database.execSQL("ALTER TABLE audiobook_chapter ADD COLUMN " +
					"book_offset_ms INTEGER NOT NULL DEFAULT 0");
			ContentValues values = new ContentValues();
			values.put("value", "3");
			database.update("audiobook_meta", values, "key='schema_version'", null);
			version = 3;
		}
		return version;
	}

	private static int schemaVersion(SQLiteDatabase database) {
		try {
			return Integer.parseInt(DatabaseUtils.stringForQuery(database,
					"SELECT value FROM audiobook_meta WHERE key='schema_version'", null));
		} catch (RuntimeException ex) {
			throw new SQLiteException("Invalid Audiobook database schema", ex);
		}
	}

	private static boolean tableExists(SQLiteDatabase database, String table) {
		return DatabaseUtils.longForQuery(database,
				"SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
				new String[]{table}) != 0;
	}

	private static void createSchema(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE audiobook_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
		database.execSQL("CREATE TABLE audiobook_source (" +
				"source_id TEXT PRIMARY KEY, source_type INTEGER NOT NULL, name TEXT NOT NULL, " +
				"endpoint TEXT NOT NULL, credential_ref TEXT, created_ms INTEGER NOT NULL, " +
				"updated_ms INTEGER NOT NULL)");
		database.execSQL("CREATE TABLE audiobook_book (" +
				"book_id TEXT PRIMARY KEY, source_id TEXT, remote_id TEXT, title TEXT NOT NULL, " +
				"author TEXT NOT NULL DEFAULT '', narrator TEXT NOT NULL DEFAULT '', " +
				"description TEXT NOT NULL DEFAULT '', artwork_url TEXT NOT NULL DEFAULT '', " +
				"language TEXT NOT NULL DEFAULT '', duration_ms INTEGER NOT NULL DEFAULT 0, " +
				"progress_chapter_id TEXT, progress_ms INTEGER NOT NULL DEFAULT 0, " +
				"last_played_ms INTEGER NOT NULL DEFAULT 0, finished INTEGER NOT NULL DEFAULT 0, " +
				"added_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL, " +
				"FOREIGN KEY(source_id) REFERENCES audiobook_source(source_id) ON DELETE CASCADE)");
		database.execSQL("CREATE INDEX audiobook_book_source ON audiobook_book(source_id)");
		database.execSQL("CREATE INDEX audiobook_book_continue ON audiobook_book" +
				"(finished, last_played_ms DESC)");
		database.execSQL("CREATE TABLE audiobook_chapter (" +
				"book_id TEXT NOT NULL, chapter_id TEXT NOT NULL, chapter_index INTEGER NOT NULL, " +
				"title TEXT NOT NULL, media_url TEXT NOT NULL, mime_type TEXT NOT NULL DEFAULT '', " +
				"offset_ms INTEGER NOT NULL DEFAULT 0, duration_ms INTEGER NOT NULL DEFAULT 0, " +
				"book_offset_ms INTEGER NOT NULL DEFAULT 0, " +
				"is_segment INTEGER NOT NULL DEFAULT 0, " +
				"local_path TEXT, download_state INTEGER NOT NULL DEFAULT 0, " +
				"PRIMARY KEY(book_id, chapter_id), " +
				"FOREIGN KEY(book_id) REFERENCES audiobook_book(book_id) ON DELETE CASCADE)");
		ContentValues version = new ContentValues();
		version.put("key", "schema_version");
		version.put("value", Integer.toString(SCHEMA_VERSION));
		database.insertOrThrow("audiobook_meta", null, version);
	}

	private static String string(Cursor cursor, String name) {
		String value = cursor.getString(cursor.getColumnIndexOrThrow(name));
		return (value == null) ? "" : value;
	}

	private static String nullable(Cursor cursor, String name) {
		int index = cursor.getColumnIndexOrThrow(name);
		return cursor.isNull(index) ? null : cursor.getString(index);
	}

	private static int integer(Cursor cursor, String name) {
		return cursor.getInt(cursor.getColumnIndexOrThrow(name));
	}

	private static long longValue(Cursor cursor, String name) {
		return cursor.getLong(cursor.getColumnIndexOrThrow(name));
	}
}
