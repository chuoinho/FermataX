package me.aap.fermata.addon.podcast.download;

import android.content.Context;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.net.PodcastDownloadClient;
import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

public final class PodcastDownloadCoordinator implements Closeable {
	private static final long PROGRESS_STEP = 1024 * 1024;
	private final File directory;
	private final PodcastDownloadStore store;
	private final PodcastDownloadClient client;
	private final Map<String, FutureSupplier<File>> downloads = new HashMap<>();
	private final Map<String, DownloadAttempt> attempts = new HashMap<>();
	private boolean closed;

	public PodcastDownloadCoordinator(Context context, PodcastDownloadStore store) {
		this(PodcastDownloadFiles.directory(context), store,
				new PodcastDownloadClient());
	}

	PodcastDownloadCoordinator(File directory, PodcastDownloadStore store,
			PodcastDownloadClient client) {
		this.directory = directory;
		this.store = store;
		this.client = client;
	}

	public synchronized FutureSupplier<File> download(PodcastEpisodeRecord episode) {
		if (closed) return me.aap.utils.async.Completed.failed(
				new IllegalStateException("Podcast download coordinator is closed"));
		String key = key(episode);
		FutureSupplier<File> existing = downloads.get(key);
		if (existing != null) return existing.fork();

		try {
			File complete = PodcastDownloadFiles.complete(directory, episode);
			File partial = PodcastDownloadFiles.partial(directory, episode);
			DownloadAttempt attempt = new DownloadAttempt(episode, partial);
			FutureSupplier<File> task = store.getDownloadInfo(episode.getFeedKey(),
					episode.getEpisodeKey()).then(info -> {
				attempt.checkCancelled();
				attempt.info = (info == null) ? PodcastDownloadInfo.EMPTY : info;
				PodcastPlaybackSource playback = store.resolveNetworkPlayback(episode);
				return store.updateDownload(episode.getFeedKey(), episode.getEpisodeKey(),
						PodcastDownloadState.DOWNLOADING, null, partial.getAbsolutePath(),
						partial.isFile() ? partial.length() : 0, episode.getMediaLength(),
						attempt.info.etag(), attempt.info.lastModified(), null).then(ignored ->
						App.get().execute(() -> {
							attempt.checkCancelled();
							return client.download(playback.getUrl(), playback.getHeaders(), partial,
									complete, attempt.info.etag(), attempt.info.lastModified(),
									new Progress(attempt));
						}));
			}).then(result -> {
				attempt.checkCancelled();
				return store.updateDownload(episode.getFeedKey(), episode.getEpisodeKey(),
							PodcastDownloadState.COMPLETE, result.file().getAbsolutePath(), null,
							result.bytes(), result.totalBytes(), result.etag(), result.lastModified(),
							null).map(ignored -> result.file());
			});
			downloads.put(key, task);
			attempts.put(key, attempt);
			task.onCompletion((result, error) -> completed(key, task, attempt, error));
			return task.fork();
		} catch (Throwable ex) {
			return me.aap.utils.async.Completed.failed(ex);
		}
	}

	public FutureSupplier<Void> delete(PodcastEpisodeRecord episode) {
		String key = key(episode);
		FutureSupplier<?> running;
		synchronized (this) {
			running = downloads.remove(key);
			DownloadAttempt attempt = attempts.remove(key);
			if (attempt != null) attempt.cancelled = true;
		}
		if (running != null) running.cancel();
		return App.get().execute(() -> {
			deleteOwned(PodcastDownloadFiles.complete(directory, episode));
			deleteOwned(PodcastDownloadFiles.partial(directory, episode));
		}).then(ignored -> store.deleteDownloadState(episode.getFeedKey(),
				episode.getEpisodeKey()));
	}

	public FutureSupplier<Void> deleteSubscription(String feedKey) {
		String prefix = feedKey + ':';
		synchronized (this) {
			for (var entry : new java.util.ArrayList<>(downloads.entrySet())) {
				if (!entry.getKey().startsWith(prefix)) continue;
				downloads.remove(entry.getKey());
				DownloadAttempt attempt = attempts.remove(entry.getKey());
				if (attempt != null) attempt.cancelled = true;
				entry.getValue().cancel();
			}
		}
		return App.get().execute(() -> {
			deleteTreeOwned(new File(directory, feedKey));
			return null;
		});
	}

	private synchronized void completed(String key, FutureSupplier<File> task,
			DownloadAttempt attempt, Throwable error) {
		downloads.remove(key, task);
		attempts.remove(key, attempt);
		if ((error == null) || task.isCancelled() || attempt.cancelled) return;
		String code = (error instanceof PodcastException podcast) ? podcast.getCode().name() :
				PodcastErrorCode.HTTP.name();
		PodcastEpisodeRecord episode = attempt.episode;
		File partial = attempt.partial;
		store.updateDownload(episode.getFeedKey(), episode.getEpisodeKey(),
				PodcastDownloadState.FAILED, null, partial.getAbsolutePath(),
				partial.isFile() ? partial.length() : 0, episode.getMediaLength(),
				attempt.info.etag(), attempt.info.lastModified(), code);
	}

	private void deleteOwned(File file) throws IOException {
		String root = directory.getCanonicalPath() + File.separator;
		String target = file.getCanonicalPath();
		if (!target.startsWith(root)) throw new IOException("Invalid Podcast download path");
		if (file.isFile() && !file.delete()) throw new IOException("Cannot delete Podcast download");
	}

	private void deleteTreeOwned(File target) throws IOException {
		String root = directory.getCanonicalPath() + File.separator;
		String path = target.getCanonicalPath();
		if (!path.startsWith(root)) throw new IOException("Invalid Podcast download path");
		deleteTree(target);
	}

	private static void deleteTree(File file) throws IOException {
		if (!file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) deleteTree(child);
		if (!file.delete()) throw new IOException("Cannot delete Podcast download files");
	}

	private static String key(PodcastEpisodeRecord episode) {
		return episode.getFeedKey() + ':' + episode.getEpisodeKey();
	}

	@Override
	public synchronized void close() {
		closed = true;
		for (DownloadAttempt attempt : attempts.values()) attempt.cancelled = true;
		for (FutureSupplier<?> download : downloads.values()) download.cancel();
		downloads.clear();
		attempts.clear();
	}

	private static final class DownloadAttempt {
		final PodcastEpisodeRecord episode;
		final File partial;
		PodcastDownloadInfo info = PodcastDownloadInfo.EMPTY;
		volatile boolean cancelled;

		DownloadAttempt(PodcastEpisodeRecord episode, File partial) {
			this.episode = episode;
			this.partial = partial;
		}

		void checkCancelled() {
			if (cancelled) throw new CancellationException();
		}
	}

	private final class Progress implements PodcastDownloadClient.ProgressListener {
		private final DownloadAttempt attempt;
		private long reported;

		Progress(DownloadAttempt attempt) {
			this.attempt = attempt;
		}

		@Override
		public void onProgress(long downloaded, long total) {
			synchronized (PodcastDownloadCoordinator.this) {
				if (attempt.cancelled || ((downloaded - reported) < PROGRESS_STEP)) return;
				reported = downloaded;
				PodcastEpisodeRecord episode = attempt.episode;
				store.updateDownload(episode.getFeedKey(), episode.getEpisodeKey(),
						PodcastDownloadState.DOWNLOADING, null, attempt.partial.getAbsolutePath(),
						downloaded, total, attempt.info.etag(), attempt.info.lastModified(), null);
			}
		}

		@Override
		public boolean isCancelled() {
			return attempt.cancelled;
		}
	}
}
