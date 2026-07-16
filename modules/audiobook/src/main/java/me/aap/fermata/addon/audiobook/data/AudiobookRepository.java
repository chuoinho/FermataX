package me.aap.fermata.addon.audiobook.data;

import static me.aap.utils.async.Completed.failed;

import android.content.Context;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.download.AudiobookDownloadStore;
import me.aap.fermata.addon.audiobook.scan.AudiobookScanner;
import me.aap.fermata.addon.audiobook.scan.AudiobookScanner.ScannedBook;
import me.aap.fermata.addon.audiobook.remote.AudiobookSourceSnapshot;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFolder;

public final class AudiobookRepository implements Closeable, AudiobookDownloadStore {
	private final SQLite database;
	private final VfsManager vfs;
	private final AudiobookScanner scanner;
	private final FutureSupplier<Void> initialized;

	public AudiobookRepository(Context context, VfsManager vfs) {
		this(SQLite.get(new File(context.getFilesDir(), "audiobook/audiobook.db")), vfs,
				new AudiobookScanner(context));
	}

	AudiobookRepository(SQLite database, VfsManager vfs, AudiobookScanner scanner) {
		this.database = database;
		this.vfs = vfs;
		this.scanner = scanner;
		initialized = database.execute(AudiobookDatabase::initialize);
	}

	public FutureSupplier<AudiobookBook> addLocalFolder(VirtualFolder folder) {
		return App.get().execute(() -> scanner.scan(folder)).then(this::replaceSourceBook);
	}

	public FutureSupplier<AudiobookBook> refreshSource(String sourceId) {
		return getSource(sourceId).then(source -> {
			if (source == null) return failed(new IOException("Audiobook source was not found"));
			return vfs.getFolder(source.getEndpoint()).then(folder -> {
				if (folder == null) return failed(new IOException(
						"Audiobook source is no longer available"));
				return addLocalFolder(folder);
			});
		});
	}

	public FutureSupplier<AudiobookBook> saveCatalogBook(AudiobookBook book,
			List<AudiobookChapter> chapters) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.upsertCatalogBook(db, book, chapters))).map(ignored -> book);
	}

	public FutureSupplier<AudiobookBook> saveCatalogBook(AudiobookSource source,
			AudiobookBook book, List<AudiobookChapter> chapters) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.replaceSourceBook(db, source, book, chapters))).map(ignored -> book);
	}

	public FutureSupplier<AudiobookSource> saveSourceSnapshot(AudiobookSourceSnapshot snapshot) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.upsertSourceBooks(db, snapshot.source(), snapshot.books())))
				.map(ignored -> snapshot.source());
	}

	public FutureSupplier<AudiobookBook> saveRemoteBook(AudiobookSource source,
			AudiobookBook book, List<AudiobookChapter> chapters, long remoteProgressUpdatedMs) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.replaceRemoteBook(db, source, book, chapters,
						remoteProgressUpdatedMs))).map(ignored -> book);
	}

	public FutureSupplier<List<AudiobookSource>> listSources() {
		return initialized.thenIgnoreResult(() -> database.query(AudiobookDatabase::listSources));
	}

	public FutureSupplier<AudiobookSource> getSource(String sourceId) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.getSource(db, sourceId)));
	}

	public FutureSupplier<Boolean> deleteSource(String sourceId) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.deleteSource(db, sourceId)));
	}

	public FutureSupplier<List<AudiobookBook>> listBooks() {
		return initialized.thenIgnoreResult(() -> database.query(AudiobookDatabase::listBooks));
	}

	public FutureSupplier<List<AudiobookBook>> listBooksBySource(String sourceId) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.listBooksBySource(db, sourceId)));
	}

	public FutureSupplier<List<AudiobookBook>> listContinue(int limit) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.listContinue(db, limit)));
	}

	public FutureSupplier<List<AudiobookBook>> listDownloaded(int limit) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.listDownloaded(db, limit)));
	}

	public FutureSupplier<AudiobookBook> getBook(String bookId) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.getBook(db, bookId)));
	}

	public FutureSupplier<List<AudiobookChapter>> listChapters(String bookId) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.listChapters(db, bookId)));
	}

	public FutureSupplier<AudiobookChapter> getChapter(String bookId, String chapterId) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				AudiobookDatabase.getChapter(db, bookId, chapterId)));
	}

	public FutureSupplier<Void> updateProgress(String bookId, String chapterId, long positionMs,
			boolean finished, long now) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.updateProgress(db, bookId, chapterId, positionMs, finished, now)));
	}

	public FutureSupplier<Void> updateDownload(String bookId, String chapterId, int state,
			String localPath) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.updateDownload(db, bookId, chapterId, state, localPath)));
	}

	public FutureSupplier<Void> clearDownloads(String bookId) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.clearDownloads(db, bookId)));
	}

	private FutureSupplier<AudiobookBook> replaceSourceBook(ScannedBook scanned) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				AudiobookDatabase.replaceSourceBook(db, scanned.source(), scanned.book(),
						scanned.chapters()))).map(ignored -> scanned.book());
	}

	@Override
	public void close() {
		database.close();
	}
}
