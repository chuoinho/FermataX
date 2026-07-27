package me.aap.fermata.addon.stremio.torrent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.frostwire.jlibtorrent.TorrentStatus;

import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.net.RemotePlaybackProgress;

/** Maps native torrent state to the stable playback progress contract. */
final class TorrentProgressMapper {
	private TorrentProgressMapper() {
	}

	static RemotePlaybackProgress failure(Throwable error) {
		PlaybackFailureException failure = PlaybackFailureException.find(error);
		if (failure != null) {
			return RemotePlaybackProgress.failed(switch (failure.getReason()) {
				case P2P_METADATA_UNAVAILABLE -> RemotePlaybackProgress.Failure.METADATA_UNAVAILABLE;
				case P2P_NO_PEERS -> RemotePlaybackProgress.Failure.NO_PEERS;
				case P2P_DATA_TIMEOUT -> RemotePlaybackProgress.Failure.DATA_TIMEOUT;
				case P2P_ENGINE_UNAVAILABLE -> RemotePlaybackProgress.Failure.ENGINE_UNAVAILABLE;
				case P2P_FILE_UNAVAILABLE -> RemotePlaybackProgress.Failure.FILE_UNAVAILABLE;
				case P2P_LOW_STORAGE -> RemotePlaybackProgress.Failure.LOW_STORAGE;
			});
		}
		return RemotePlaybackProgress.failed(RemotePlaybackProgress.Failure.ENGINE_UNAVAILABLE);
	}

	static RemotePlaybackProgress initial(TorrentStatus status) {
		return (status.numPeers() == 0 && status.numSeeds() == 0) ?
				RemotePlaybackProgress.findingPeers() :
				RemotePlaybackProgress.buffering(status.numPeers(), status.numSeeds(),
						status.downloadRate(), 0, 0, 0);
	}

	static RemotePlaybackProgress ready(TorrentStatus status, long completed, long target,
			boolean firstReady) {
		return firstReady ? RemotePlaybackProgress.ready(status.numPeers(), status.numSeeds(),
				status.downloadRate(), completed, target) :
				RemotePlaybackProgress.streaming(status.numPeers(), status.numSeeds(),
						status.downloadRate());
	}

	static RemotePlaybackProgress waiting(TorrentStatus status, boolean initialRangeReady,
			long completed, long target) {
		if (status.numPeers() == 0 && status.numSeeds() == 0 && completed == 0) {
			return RemotePlaybackProgress.findingPeers();
		}
		if (initialRangeReady) {
			return RemotePlaybackProgress.rebuffering(status.numPeers(), status.numSeeds(),
					status.downloadRate());
		}
		return RemotePlaybackProgress.buffering(status.numPeers(), status.numSeeds(),
				status.downloadRate(), completed, target,
				TorrentStreamPolicy.bufferScore(status.numPeers(), status.downloadRate(),
						completed, target));
	}

	static void publish(Consumer<RemotePlaybackProgress> progress,
			RemotePlaybackProgress value) {
		if (progress == null) return;
		try {
			progress.accept(value);
		} catch (RuntimeException ignored) {
			// Playback progress is observational and must not break torrent preparation.
		}
	}

	static final class Publisher {
		private static final long PROGRESS_INTERVAL_MILLIS = 1_000L;
		private final AtomicBoolean failurePublished = new AtomicBoolean();
		private volatile Consumer<RemotePlaybackProgress> progress;
		private volatile RemotePlaybackProgress latestProgress =
				RemotePlaybackProgress.findingPeers();
		private long lastProgressEmit;
		private RemotePlaybackProgress lastEmittedProgress;

		void observe(Consumer<RemotePlaybackProgress> progress) {
			this.progress = progress;
			publish(latestProgress, true);
		}

		boolean hasFailed() {
			return failurePublished.get();
		}

		synchronized void publish(RemotePlaybackProgress value, boolean force) {
			if (failurePublished.get() &&
					(value.phase() != RemotePlaybackProgress.Phase.FAILED)) return;
			latestProgress = value;
			Consumer<RemotePlaybackProgress> observer = progress;
			if (observer == null) return;
			long now = System.currentTimeMillis();
			if (!force && value.equals(lastEmittedProgress) &&
					(now - lastProgressEmit < PROGRESS_INTERVAL_MILLIS)) return;
			if (!force && (now - lastProgressEmit < PROGRESS_INTERVAL_MILLIS)) return;
			lastProgressEmit = now;
			lastEmittedProgress = value;
			TorrentProgressMapper.publish(observer, value);
		}

		void fail(RemotePlaybackProgress.Failure failure) {
			if (!failurePublished.compareAndSet(false, true)) return;
			publish(RemotePlaybackProgress.failed(failure), true);
		}

		void clearObserver() {
			progress = null;
		}
	}
}
