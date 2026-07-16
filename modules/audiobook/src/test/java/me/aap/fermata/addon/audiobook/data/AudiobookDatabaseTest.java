package me.aap.fermata.addon.audiobook.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;
import android.database.DatabaseUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;

@RunWith(RobolectricTestRunner.class)
public class AudiobookDatabaseTest {
	private SQLiteDatabase database;

	@Before
	public void setUp() {
		database = SQLiteDatabase.create(null);
		AudiobookDatabase.initialize(database);
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void sourceBookChaptersAndProgressRoundTrip() {
		AudiobookSource source = source("source-1");
		AudiobookBook book = book("book-1", source.getId(), "Original title");
		List<AudiobookChapter> chapters = List.of(
				chapter(book.getId(), "chapter-1", 0),
				chapter(book.getId(), "chapter-2", 1));

		AudiobookDatabase.replaceSourceBook(database, source, book, chapters);

		assertEquals(1, AudiobookDatabase.listSources(database).size());
		assertEquals(1, AudiobookDatabase.listBooks(database).size());
		assertEquals(2, AudiobookDatabase.listChapters(database, book.getId()).size());
		assertNotNull(AudiobookDatabase.getChapter(database, book.getId(), "chapter-2"));
		assertFalse(AudiobookDatabase.getChapter(database, book.getId(), "chapter-1").isSegment());

		AudiobookDatabase.updateProgress(database, book.getId(), "chapter-2", 12_345,
				false, 1000);
		AudiobookBook progressed = AudiobookDatabase.getBook(database, book.getId());
		assertEquals("chapter-2", progressed.getProgressChapterId());
		assertEquals(12_345, progressed.getProgressMs());
		assertEquals(1, AudiobookDatabase.listContinue(database, 10).size());
	}

	@Test
	public void migratesVersionOneSegmentColumn() {
		database.close();
		database = SQLiteDatabase.create(null);
		database.execSQL("CREATE TABLE audiobook_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
		database.execSQL("INSERT INTO audiobook_meta VALUES ('schema_version', '1')");
		database.execSQL("CREATE TABLE audiobook_chapter (book_id TEXT, chapter_id TEXT, " +
				"chapter_index INTEGER, title TEXT, media_url TEXT, mime_type TEXT, " +
				"offset_ms INTEGER, duration_ms INTEGER, local_path TEXT, download_state INTEGER)");

		AudiobookDatabase.initialize(database);

		assertEquals("3", DatabaseUtils.stringForQuery(database,
				"SELECT value FROM audiobook_meta WHERE key='schema_version'", null));
		try (android.database.Cursor cursor = database.rawQuery(
				"SELECT is_segment FROM audiobook_chapter", null)) {
			assertNotNull(cursor);
		}
	}

	@Test
	public void migratesVersionTwoBookOffsetColumn() {
		database.close();
		database = SQLiteDatabase.create(null);
		database.execSQL("CREATE TABLE audiobook_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
		database.execSQL("INSERT INTO audiobook_meta VALUES ('schema_version', '2')");
		database.execSQL("CREATE TABLE audiobook_chapter (book_id TEXT, chapter_id TEXT, " +
				"chapter_index INTEGER, title TEXT, media_url TEXT, mime_type TEXT, " +
				"offset_ms INTEGER, duration_ms INTEGER, is_segment INTEGER, " +
				"local_path TEXT, download_state INTEGER)");

		AudiobookDatabase.initialize(database);

		assertEquals("3", DatabaseUtils.stringForQuery(database,
				"SELECT value FROM audiobook_meta WHERE key='schema_version'", null));
		try (android.database.Cursor cursor = database.rawQuery(
				"SELECT book_offset_ms FROM audiobook_chapter", null)) {
			assertNotNull(cursor);
		}
	}

	@Test
	public void newerProgressWinsWhenMergingRemoteBook() {
		AudiobookSource source = new AudiobookSource("remote-source",
				AudiobookSourceType.AUDIOBOOKSHELF, "ABS", "https://books.example", "credential",
				1, 1);
		AudiobookBook local = book("remote-book", source.getId(), "Local");
		AudiobookChapter first = chapter(local.getId(), "chapter-1", 0);
		AudiobookChapter second = chapter(local.getId(), "chapter-2", 1);
		AudiobookDatabase.replaceSourceBook(database, source, local, List.of(first, second));
		AudiobookDatabase.updateProgress(database, local.getId(), first.getId(), 20_000,
				false, 2_000);
		AudiobookDatabase.updateDownload(database, local.getId(), first.getId(), 2,
				"file:///downloaded.mp3");

		AudiobookBook olderRemote = remoteBook(local, second.getId(), 3_000, 1_000);
		AudiobookDatabase.replaceRemoteBook(database, source, olderRemote, List.of(first, second),
				1_000);
		AudiobookBook preserved = AudiobookDatabase.getBook(database, local.getId());
		assertEquals(first.getId(), preserved.getProgressChapterId());
		assertEquals(20_000, preserved.getProgressMs());
		assertEquals("file:///downloaded.mp3", AudiobookDatabase.getChapter(database,
				local.getId(), first.getId()).getLocalPath());

		AudiobookBook newerRemote = remoteBook(local, second.getId(), 4_000, 3_000);
		AudiobookDatabase.replaceRemoteBook(database, source, newerRemote, List.of(first, second),
				3_000);
		AudiobookBook merged = AudiobookDatabase.getBook(database, local.getId());
		assertEquals(second.getId(), merged.getProgressChapterId());
		assertEquals(4_000, merged.getProgressMs());
	}

	@Test
	public void metadataRefreshPreservesBookProgress() {
		AudiobookSource source = source("source-2");
		AudiobookBook book = book("book-2", source.getId(), "Before");
		AudiobookChapter chapter = chapter(book.getId(), "chapter-1", 0);
		AudiobookDatabase.replaceSourceBook(database, source, book, List.of(chapter));
		AudiobookDatabase.updateProgress(database, book.getId(), chapter.getId(), 9000,
				false, 2000);

		AudiobookBook refreshed = book("book-2", source.getId(), "After");
		AudiobookDatabase.replaceSourceBook(database, source, refreshed, List.of(chapter));

		AudiobookBook stored = AudiobookDatabase.getBook(database, book.getId());
		assertEquals("After", stored.getTitle());
		assertEquals(chapter.getId(), stored.getProgressChapterId());
		assertEquals(9000, stored.getProgressMs());
	}

	@Test
	public void deletingSourceCascadesBooksAndChapters() {
		AudiobookSource source = source("source-3");
		AudiobookBook book = book("book-3", source.getId(), "Book");
		AudiobookDatabase.replaceSourceBook(database, source, book,
				List.of(chapter(book.getId(), "chapter-1", 0)));

		assertTrue(AudiobookDatabase.deleteSource(database, source.getId()));
		assertNull(AudiobookDatabase.getSource(database, source.getId()));
		assertNull(AudiobookDatabase.getBook(database, book.getId()));
		assertTrue(AudiobookDatabase.listChapters(database, book.getId()).isEmpty());
		assertFalse(AudiobookDatabase.deleteSource(database, source.getId()));
	}

	@Test
	public void catalogBookCanExistWithoutUserSource() {
		AudiobookBook book = book("lv-200", null, "Alice");
		AudiobookDatabase.upsertCatalogBook(database, book,
				List.of(chapter(book.getId(), "chapter-1", 0)));

		assertEquals("Alice", AudiobookDatabase.getBook(database, book.getId()).getTitle());
		assertTrue(AudiobookDatabase.listSources(database).isEmpty());
	}

	@Test
	public void clearingDownloadsPreservesBookAndChapters() {
		AudiobookSource source = source("source-download");
		AudiobookBook book = book("book-download", source.getId(), "Downloaded");
		AudiobookChapter chapter = chapter(book.getId(), "chapter-download", 0);
		AudiobookDatabase.replaceSourceBook(database, source, book, List.of(chapter));
		AudiobookDatabase.updateDownload(database, book.getId(), chapter.getId(), 2,
				"file:///download.mp3");

		AudiobookDatabase.clearDownloads(database, book.getId());

		AudiobookChapter stored = AudiobookDatabase.getChapter(database, book.getId(),
				chapter.getId());
		assertNotNull(stored);
		assertFalse(stored.isDownloaded());
		assertEquals(0, stored.getDownloadState());
	}

	private static AudiobookSource source(String id) {
		return new AudiobookSource(id, AudiobookSourceType.LOCAL, "Folder", "file:///books",
				null, 1, 2);
	}

	private static AudiobookBook book(String id, String sourceId, String title) {
		return new AudiobookBook(id, sourceId, null, title, "Author", "Narrator", "", "",
				"en", 100_000, null, 0, 0, false, 1, 2);
	}

	private static AudiobookChapter chapter(String bookId, String id, int index) {
		return new AudiobookChapter(bookId, id, index, "Chapter " + index,
				"https://example.test/" + id + ".mp3", "audio/mpeg", 0, index * 50_000L,
				50_000, false,
				null, 0);
	}

	private static AudiobookBook remoteBook(AudiobookBook base, String chapterId,
			long positionMs, long updatedMs) {
		return new AudiobookBook(base.getId(), base.getSourceId(), "remote-id", base.getTitle(),
				base.getAuthor(), base.getNarrator(), base.getDescription(), base.getArtworkUrl(),
				base.getLanguage(), base.getDurationMs(), chapterId, positionMs, updatedMs, false,
				base.getAddedMs(), updatedMs);
	}
}
