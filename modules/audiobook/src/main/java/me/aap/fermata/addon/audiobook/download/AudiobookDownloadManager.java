package me.aap.fermata.addon.audiobook.download;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.audiobook.data.AudiobookRepository;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

public final class AudiobookDownloadManager implements Closeable {
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 60_000;
	private final File directory;
	private final AudiobookDownloadStore store;
	private final DownloadHeaderProvider headers;
	private final DownloadClient client;
	private final Map<String, FutureSupplier<File>> active = new HashMap<>();
	private final Map<String, DownloadAttempt> attempts = new HashMap<>();
	private boolean closed;

	public AudiobookDownloadManager(Context context, AudiobookRepository repository) {
		this(context, repository, book -> me.aap.utils.async.Completed.completed(Map.of()));
	}

	public AudiobookDownloadManager(Context context, AudiobookRepository repository,
			DownloadHeaderProvider headers) {
		this(new File(context.getFilesDir(), "audiobook/downloads"), repository,
				AudiobookDownloadManager::downloadFile, headers);
	}

	AudiobookDownloadManager(File directory, AudiobookDownloadStore store,
			DownloadClient client) {
		this(directory, store, client, book -> me.aap.utils.async.Completed.completed(Map.of()));
	}

	AudiobookDownloadManager(File directory, AudiobookDownloadStore store,
			DownloadClient client, DownloadHeaderProvider headers) {
		this.directory = directory;
		this.store = store;
		this.client = client;
		this.headers = headers;
	}

	public FutureSupplier<Void> download(AudiobookBook book) {
		if (closed) return me.aap.utils.async.Completed.failed(
				new IllegalStateException("Audiobook download manager is closed"));
		return store.listChapters(book.getId()).then(chapters -> headers.headers(book)
				.then(requestHeaders -> download(book, chapters, requestHeaders, 0)));
	}

	private FutureSupplier<Void> download(AudiobookBook book, List<AudiobookChapter> chapters,
			Map<String, String> requestHeaders, int index) {
		if (index >= chapters.size()) return completedVoid();
		return download(book, chapters.get(index), requestHeaders).then(ignored ->
				download(book, chapters, requestHeaders, index + 1));
	}

	private synchronized FutureSupplier<File> download(AudiobookBook book,
			AudiobookChapter chapter, Map<String, String> requestHeaders) {
		String key = key(book, chapter);
		FutureSupplier<File> running = active.get(key);
		if (running != null) return running.fork();
		if (closed) return me.aap.utils.async.Completed.failed(
				new IllegalStateException("Audiobook download manager is closed"));

		File complete = complete(book, chapter);
		if (chapter.isDownloaded() && complete.isFile()) return
				me.aap.utils.async.Completed.completed(complete);
		File partial = new File(complete.getPath() + ".partial");
		DownloadAttempt attempt = new DownloadAttempt();
		FutureSupplier<File> task = store.updateDownload(book.getId(), chapter.getId(),
				AudiobookDownloadState.DOWNLOADING, null).then(ignored -> App.get().execute(() -> {
			attempt.checkCancelled();
			return client.download(chapter.getMediaUrl(), requestHeaders, partial, complete, attempt);
		})).then(file -> store.updateDownload(book.getId(), chapter.getId(),
				AudiobookDownloadState.COMPLETE, file.toURI().toString()).map(ignored -> file));
		active.put(key, task);
		attempts.put(key, attempt);
		task.onCompletion((file, error) -> completed(book, chapter, key, task, attempt, error));
		return task.fork();
	}

	public FutureSupplier<Void> delete(AudiobookBook book) {
		List<FutureSupplier<?>> cancelled = new ArrayList<>();
		String prefix = book.getId() + ':';
		synchronized (this) {
			for (Map.Entry<String, FutureSupplier<File>> entry : new ArrayList<>(active.entrySet())) {
				if (!entry.getKey().startsWith(prefix)) continue;
				active.remove(entry.getKey());
				DownloadAttempt attempt = attempts.remove(entry.getKey());
				if (attempt != null) attempt.cancelled = true;
				entry.getValue().cancel();
				cancelled.add(entry.getValue());
			}
		}
		return App.get().execute(() -> deleteTreeOwned(new File(directory, book.getId())))
				.then(ignored -> store.clearDownloads(book.getId()));
	}

	private synchronized void completed(AudiobookBook book, AudiobookChapter chapter, String key,
			FutureSupplier<File> task, DownloadAttempt attempt, Throwable error) {
		active.remove(key, task);
		attempts.remove(key, attempt);
		if ((error == null) || task.isCancelled() || attempt.cancelled) return;
		store.updateDownload(book.getId(), chapter.getId(),
				AudiobookDownloadState.FAILED, null);
	}

	private static File downloadFile(String sourceUrl, Map<String, String> requestHeaders,
			File partial, File complete,
			CancellationCheck cancellation) throws IOException {
		if (!sourceUrl.regionMatches(true, 0, "https://", 0, 8) &&
				!sourceUrl.regionMatches(true, 0, "http://", 0, 7)) {
			throw new IOException("Audiobook download URL is invalid");
		}
		File parent = complete.getParentFile();
		if ((parent != null) && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IOException("Cannot create Audiobook download directory");
		}
		if (complete.isFile() && (complete.length() > 0)) return complete;
		long existing = partial.isFile() ? partial.length() : 0;
		HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Accept-Encoding", "identity");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
			String name = header.getKey();
			if (name.equalsIgnoreCase("Range") || name.equalsIgnoreCase("Accept-Encoding") ||
					name.equalsIgnoreCase("User-Agent")) continue;
			connection.setRequestProperty(name, header.getValue());
		}
		if (existing > 0) connection.setRequestProperty("Range", "bytes=" + existing + '-');
		try {
			int status = connection.getResponseCode();
			if ((status == 416) && (existing > 0)) {
				if (!partial.delete()) throw new IOException("Cannot restart Audiobook download");
				return downloadFile(sourceUrl, requestHeaders, partial, complete, cancellation);
			}
			if ((status != 200) && (status != 206)) {
				throw new IOException("Audiobook download returned HTTP " + status);
			}
			boolean append = (status == 206) && (existing > 0);
			try (InputStream input = new BufferedInputStream(connection.getInputStream());
				 BufferedOutputStream output = new BufferedOutputStream(
						new FileOutputStream(partial, append))) {
				byte[] buffer = new byte[64 * 1024];
				for (int read; (read = input.read(buffer)) != -1; ) {
					cancellation.checkCancelled();
					output.write(buffer, 0, read);
				}
			}
			cancellation.checkCancelled();
			move(partial, complete);
			return complete;
		} finally {
			connection.disconnect();
		}
	}

	private File complete(AudiobookBook book, AudiobookChapter chapter) {
		return new File(new File(directory, book.getId()), chapter.getId() + extension(
				chapter.getMediaUrl()));
	}

	private static String extension(String url) {
		try {
			String path = URI.create(url).getPath();
			int dot = (path == null) ? -1 : path.lastIndexOf('.');
			String extension = (dot < 0) ? "" : path.substring(dot).toLowerCase(Locale.ROOT);
			return switch (extension) {
				case ".mp3", ".m4a", ".m4b", ".aac", ".ogg", ".opus", ".flac", ".wav" ->
						extension;
				default -> ".media";
			};
		} catch (IllegalArgumentException ignore) {
			return ".media";
		}
	}

	private static void move(File source, File target) throws IOException {
		try {
			Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException atomicFailure) {
			Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void deleteTreeOwned(File target) throws IOException {
		String root = directory.getCanonicalPath() + File.separator;
		String path = target.getCanonicalPath();
		if (!path.startsWith(root)) throw new IOException("Invalid Audiobook download path");
		deleteTree(target);
	}

	private static void deleteTree(File file) throws IOException {
		if (!file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) deleteTree(child);
		if (!file.delete()) throw new IOException("Cannot delete Audiobook download file");
	}

	private static String key(AudiobookBook book, AudiobookChapter chapter) {
		return book.getId() + ':' + chapter.getId();
	}

	@Override
	public synchronized void close() {
		closed = true;
		for (DownloadAttempt attempt : attempts.values()) attempt.cancelled = true;
		for (FutureSupplier<?> task : active.values()) task.cancel();
		attempts.clear();
		active.clear();
	}

	interface DownloadClient {
		File download(String sourceUrl, Map<String, String> requestHeaders,
				File partial, File complete,
				CancellationCheck cancellation) throws IOException;
	}

	public interface DownloadHeaderProvider {
		FutureSupplier<Map<String, String>> headers(AudiobookBook book);
	}

	interface CancellationCheck {
		void checkCancelled();
	}

	private static final class DownloadAttempt implements CancellationCheck {
		private volatile boolean cancelled;

		@Override
		public void checkCancelled() {
			if (cancelled || Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
		}
	}
}
