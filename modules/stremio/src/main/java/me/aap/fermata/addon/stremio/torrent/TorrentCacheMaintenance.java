package me.aap.fermata.addon.stremio.torrent;

import android.util.Log;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import me.aap.fermata.media.engine.PlaybackFailureException;

/** Coordinates cleaner policy with cache entries currently owned by preparation/playback. */
final class TorrentCacheMaintenance {
	private static final String TAG = "StremioTorrent";
	private final File root;
	private final Executor executor;
	private final TorrentCachePolicy policy;
	private final TorrentCacheCleaner cleaner;
	private final BooleanSupplier closed;
	private final Set<File> preparingDirectories = new HashSet<>();
	private final Set<File> activeDirectories = new HashSet<>();
	private final AtomicBoolean maintenanceQueued = new AtomicBoolean();

	TorrentCacheMaintenance(File root, Executor executor, TorrentCachePolicy policy,
			LongSupplier clock, BooleanSupplier closed) {
		this.root = root;
		this.executor = executor;
		this.policy = policy;
		this.closed = closed;
		cleaner = new TorrentCacheCleaner(root, policy, clock);
	}

	void beginPreparation(File directory) throws PlaybackFailureException {
		synchronized (preparingDirectories) {
			preparingDirectories.add(directory);
		}
		cleaner.cleanup(protectedCacheFiles(directory));
		long usableSpace = root.getUsableSpace();
		if ((usableSpace > 0L) && (usableSpace < policy.minFreeBytes())) {
			throw new PlaybackFailureException(
					PlaybackFailureException.Reason.P2P_LOW_STORAGE);
		}
	}

	void finishPreparation(File directory) {
		synchronized (preparingDirectories) {
			preparingDirectories.remove(directory);
		}
	}

	void prepared(File directory) {
		synchronized (activeDirectories) {
			activeDirectories.add(directory);
		}
		cleaner.touch(directory);
	}

	void released(File directory) {
		synchronized (activeDirectories) {
			activeDirectories.removeIf(active -> contains(active, directory));
		}
		cleaner.touch(directory);
	}

	private static boolean contains(File directory, File path) {
		try {
			String root = directory.getCanonicalPath();
			String candidate = path.getCanonicalPath();
			return candidate.equals(root) || candidate.startsWith(root + File.separator);
		} catch (java.io.IOException ignored) {
			String root = directory.getAbsolutePath();
			String candidate = path.getAbsolutePath();
			return candidate.equals(root) || candidate.startsWith(root + File.separator);
		}
	}

	void failed(File directory, boolean existedBefore) {
		cleaner.deleteFailedEntry(directory, existedBefore);
	}

	void schedule() {
		if (closed.getAsBoolean() || !maintenanceQueued.compareAndSet(false, true)) return;
		try {
			CompletableFuture.runAsync(this::run, executor).whenComplete(
					(ignored, failure) -> maintenanceQueued.set(false));
		} catch (RuntimeException rejected) {
			maintenanceQueued.set(false);
		}
	}

	void run() {
		TorrentCacheCleaner.CleanupResult result = cleaner.cleanup(protectedCacheFiles(null));
		if ((result.removedEntries() > 0) || (result.temporaryFiles() > 0)) {
			Log.i(TAG, "P2P cache cleanup: entries=" + result.removedEntries() +
					" bytes=" + result.removedBytes() + " temporary=" +
					result.temporaryFiles() + " retained=" + result.retainedBytes());
		}
	}

	void close() {
		synchronized (preparingDirectories) {
			preparingDirectories.clear();
		}
		synchronized (activeDirectories) {
			activeDirectories.clear();
		}
		try {
			CompletableFuture.runAsync(() -> cleaner.cleanup(Set.of()), executor);
		} catch (RuntimeException ignored) {
			// Startup maintenance handles cleanup if the runtime executor is already stopping.
		}
	}

	private Set<File> protectedCacheFiles(File current) {
		Set<File> files = new HashSet<>();
		if (current != null) files.add(current);
		synchronized (preparingDirectories) {
			files.addAll(preparingDirectories);
		}
		synchronized (activeDirectories) {
			files.addAll(activeDirectories);
		}
		return Set.copyOf(files);
	}
}
