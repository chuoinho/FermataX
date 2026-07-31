package me.aap.fermata.addon.stremio.torrent;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.PartialPieceInfo;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;

import me.aap.fermata.media.net.RemotePlaybackProgress;
import me.aap.utils.log.Log;

/** Owns one registered torrent stream and all HTTP read leases for that stream. */
final class TorrentStreamLease {
	private static final long NO_PEERS_TIMEOUT_MILLIS = 30_000L;
	private static final long NO_PROGRESS_TIMEOUT_MILLIS = 20_000L;
	private static final long HARD_READ_TIMEOUT_MILLIS = 90_000L;
	private static final long IDLE_KEEP_ALIVE_MILLIS = 15_000L;
	private static final long PRIORITY_REFRESH_MILLIS = 5_000L;

	private final File file;
	private final long size;
	private final TorrentHandle handle;
	private final TorrentInfo info;
	private final int fileIndex;
	private final ScheduledExecutorService lifecycle;
	private final AtomicBoolean serverClosed;
	private final Map<Long, PieceWindow> activeWindows = new LinkedHashMap<>();
	private final Set<Integer> boostedPieces = new LinkedHashSet<>();
	private final TorrentProgressMapper.Publisher progress = new TorrentProgressMapper.Publisher();
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final AtomicBoolean initialRangeReady = new AtomicBoolean();
	private final TorrentWaiter alerts;
	private long nextSessionId;
	private long idleGeneration;
	private long lastResumeRequest;
	private long lastPriorityRefresh;

	TorrentStreamLease(File file, long size, TorrentHandle handle, TorrentInfo info, int fileIndex,
			ScheduledExecutorService lifecycle, AtomicBoolean serverClosed,
			TorrentAlertRouter alertRouter) {
		this.file = file;
		this.size = size;
		this.handle = handle;
		this.info = info;
		this.fileIndex = fileIndex;
		this.lifecycle = lifecycle;
		this.serverClosed = serverClosed;
		alerts = new TorrentWaiter(alertRouter, handle.infoHash().toHex());
	}

	File file() {
		return file;
	}

	long size() {
		return size;
	}

	void observe(Consumer<RemotePlaybackProgress> observer) {
		progress.observe(observer);
	}

	synchronized ReadSession openSession() {
		if (cancelled.get()) throw new IllegalStateException("P2P playback session is cancelled");
		if (progress.hasFailed()) throw new IllegalStateException("P2P playback session failed");
		idleGeneration++;
		if (handle.isValid()) {
			handle.queuePositionTop();
			handle.resume();
		}
		return new ReadSession(++nextSessionId);
	}

	int initialBufferLength(TorrentHttpServer.ByteRange range) {
		long rate = handle.isValid() ? handle.status().downloadRate() : 0L;
		return TorrentStreamPolicy.initialBufferLength(range.length(), rate);
	}

	private void await(ReadSession session, long offset, int length)
			throws IOException, InterruptedException {
		if (cancelled.get()) throw new SocketException("P2P playback session is cancelled");
		long started = System.currentTimeMillis();
		long hardDeadline = started + HARD_READ_TIMEOUT_MILLIS;
		long noPeersDeadline = started + NO_PEERS_TIMEOUT_MILLIS;
		long lastAdvance = started;
		long fileOffset = info.files().fileOffset(fileIndex);
		int pieceLength = info.pieceLength();
		if (pieceLength <= 0) throw new IOException("Invalid torrent piece size");
		int first = (int) ((fileOffset + offset) / pieceLength);
		int last = (int) ((fileOffset + offset + Math.max(length - 1L, 0L)) / pieceLength);
		long lastCompleted = completedBytes(first, last, fileOffset, offset, length, pieceLength);
		long lastAvailable = availableBytes(first, last, fileOffset, offset, length, pieceLength,
				lastCompleted);
		long initialAvailable = lastAvailable;

		while (true) {
			if (cancelled.get()) throw new SocketException("P2P playback session is cancelled");
			if (!handle.isValid()) {
				progress.fail(RemotePlaybackProgress.Failure.ENGINE_UNAVAILABLE);
				throw new IOException("Torrent session is unavailable");
			}
			TorrentStatus status = handle.status();
			if (status.errorCode().isError()) {
				progress.fail(RemotePlaybackProgress.Failure.ENGINE_UNAVAILABLE);
				throw new IOException("Torrent engine reported an error");
			}
			long now = System.currentTimeMillis();
			long window = TorrentStreamPolicy.priorityWindowBytes(status.downloadRate());
			updateWindow(session.id, offset, Math.min(size, offset + Math.max(length, window)));
			if (TorrentStreamPolicy.isChecking(status.state())) {
				alerts.awaitSignal(1_000L);
				continue;
			}
			long completed = completedBytes(first, last, fileOffset, offset, length, pieceLength);
			long available = availableBytes(first, last, fileOffset, offset, length, pieceLength,
				completed);
			long target = Math.max(length, 1);
			if (completed >= target) {
				boolean firstReady = initialRangeReady.compareAndSet(false, true);
				progress.publish(TorrentProgressMapper.ready(status, completed, target, firstReady), false);
				requestResumeData();
				return;
			}

			if (TorrentStreamPolicy.requestedRangeAdvanced(available, lastAvailable)) {
				lastAdvance = now;
				lastCompleted = completed;
				lastAvailable = available;
			}
			progress.publish(TorrentProgressMapper.waiting(status, initialRangeReady.get(),
					completed, target), false);

			if ((status.numPeers() == 0) && (status.numSeeds() == 0) &&
					(now >= noPeersDeadline)) {
				progress.fail(RemotePlaybackProgress.Failure.NO_PEERS);
				throw new SocketTimeoutException("Timed out finding torrent peers");
			}
			long stallTimeout = Math.max(NO_PROGRESS_TIMEOUT_MILLIS,
					TorrentStreamPolicy.stallTimeoutMillis(pieceLength, status.downloadRate()));
			if ((now - lastAdvance >= stallTimeout) || (now >= hardDeadline)) {
				Log.w("P2P requested range stalled: offset=" + offset + " target=" +
					length + " completed=" + completed + " available=" + available +
					" pieceLength=" + pieceLength +
					" firstPiece=" + first + " lastPiece=" + last + " requestedDelta=" +
					Math.max(0L, available -
							initialAvailable) +
						" stall=" + (now - lastAdvance) + "ms hard=" +
						(now >= hardDeadline));
				progress.fail(RemotePlaybackProgress.Failure.DATA_TIMEOUT);
				throw new SocketTimeoutException("Timed out waiting for torrent progress");
			}
			long nextDeadline = Math.min(noPeersDeadline,
					Math.min(lastAdvance + stallTimeout, hardDeadline));
			alerts.awaitSignal(Math.max(1L, Math.min(1_000L, nextDeadline - now)));
		}
	}

	private long completedBytes(int first, int last, long fileOffset,
			long requestedOffset, int requestedLength, int pieceLength) {
		long requestedStart = fileOffset + requestedOffset;
		long requestedEnd = requestedStart + Math.max(requestedLength, 1);
		long completed = 0;
		for (int piece = first; piece <= last; piece++) {
			if (!handle.havePiece(piece)) continue;
			long pieceStart = (long) piece * pieceLength;
			long pieceEnd = pieceStart + pieceLength;
			completed += Math.max(0L,
					Math.min(pieceEnd, requestedEnd) - Math.max(pieceStart, requestedStart));
		}
		return completed;
	}

	/**
	 * Reports bytes that are either hash-verified or already downloaded inside a partial
	 * requested piece. Partial bytes are liveness evidence only; stream reads still require
	 * the complete hash-verified piece before returning data to VLC.
	 */
	private long availableBytes(int first, int last, long fileOffset,
			long requestedOffset, int requestedLength, int pieceLength, long verified) {
		long requestedStart = fileOffset + requestedOffset;
		long requestedEnd = requestedStart + Math.max(requestedLength, 1L);
		long available = verified;
		try {
			for (PartialPieceInfo partial : handle.getDownloadQueue()) {
				int piece = partial.pieceIndex();
				if (piece < first || piece > last || handle.havePiece(piece)) continue;
				int blocks = partial.blocksInPiece();
				int finished = partial.finished();
				if (blocks <= 0 || finished <= 0) continue;
				long pieceBytes = Math.min(info.pieceSize(piece),
						((long) info.pieceSize(piece) * finished) / blocks);
				long pieceStart = (long) piece * pieceLength;
				long pieceEnd = pieceStart + pieceBytes;
				available += Math.max(0L,
						Math.min(pieceEnd, requestedEnd) - Math.max(pieceStart, requestedStart));
			}
		} catch (RuntimeException ignored) {
			// A status snapshot can race torrent teardown. Verified bytes remain valid evidence.
		}
		return Math.min(Math.max(requestedLength, 0L), available);
	}

	private synchronized void requestResumeData() {
		long now = System.currentTimeMillis();
		if ((now - lastResumeRequest < 5_000L) || !handle.needSaveResumeData()) return;
		lastResumeRequest = now;
		handle.saveResumeData();
	}

	int trackerCount() {
		return handle.isValid() ? handle.trackers().size() : 0;
	}

	int unavailableStatus() {
		if (!handle.isValid()) return 502;
		TorrentStatus status = handle.status();
		int peers = status.numPeers();
		int seeds = status.numSeeds();
		Log.w("P2P data timeout: trackers=" + trackerCount() + " peers=" + peers +
				" seeds=" + seeds + " rate=" + status.downloadRate() + "B/s");
		return (peers == 0 && seeds == 0) ? 503 : 504;
	}

	private synchronized void updateWindow(long sessionId, long start, long endExclusive) {
		if (!handle.isValid()) return;
		long fileOffset = info.files().fileOffset(fileIndex);
		int pieceLength = info.pieceLength();
		if (pieceLength <= 0) return;
		int first = Math.max(0, (int) ((fileOffset + start) / pieceLength));
		int last = Math.min(info.numPieces() - 1,
				(int) ((fileOffset + Math.max(start, endExclusive - 1)) / pieceLength));
		PieceWindow next = new PieceWindow(first, last);
		long now = System.currentTimeMillis();
		if (next.equals(activeWindows.get(sessionId)) &&
				(now - lastPriorityRefresh < PRIORITY_REFRESH_MILLIS)) return;
		activeWindows.put(sessionId, next);
		lastPriorityRefresh = now;
		applyPriorities();
	}

	private synchronized void closeSession(long sessionId) {
		if (activeWindows.remove(sessionId) == null) return;
		applyPriorities();
		if (!activeWindows.isEmpty()) return;
		long generation = ++idleGeneration;
		if (serverClosed.get() || cancelled.get()) {
			pauseIfIdle(generation);
			return;
		}
		try {
			lifecycle.schedule(() -> pauseIfIdle(generation),
					IDLE_KEEP_ALIVE_MILLIS, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException ignored) {
			pauseIfIdle(generation);
		}
	}

	private synchronized void pauseIfIdle(long generation) {
		if ((generation == idleGeneration) && activeWindows.isEmpty() && handle.isValid()) {
			handle.pause();
		}
	}

	private void applyPriorities() {
		if (!handle.isValid()) return;
		for (int piece : boostedPieces) {
			handle.resetPieceDeadline(piece);
			handle.piecePriority(piece, Priority.NORMAL);
		}
		handle.clearPieceDeadlines();
		// Preserve HTTP request order. Container probes may need a tail range before the head.
		Set<Integer> desired = new LinkedHashSet<>();
		for (PieceWindow window : activeWindows.values()) {
			for (int piece = window.first; piece <= window.last; piece++) desired.add(piece);
		}
		boostedPieces.clear();
		int deadline = 0;
		for (int piece : desired) {
			handle.piecePriority(piece, Priority.SEVEN);
			handle.setPieceDeadline(piece, deadline);
			deadline += 50;
			boostedPieces.add(piece);
		}
	}

	void cancel() {
		if (!cancelled.compareAndSet(false, true)) return;
		synchronized (this) {
			activeWindows.clear();
			applyPriorities();
			progress.clearObserver();
		}
		if (handle.isValid()) handle.pause();
		alerts.close();
	}

	final class ReadSession implements AutoCloseable {
		private final long id;
		private boolean closed;

		private ReadSession(long id) {
			this.id = id;
		}

		void await(long offset, int length) throws IOException, InterruptedException {
			if (closed) throw new IOException("Torrent read session is closed");
			TorrentStreamLease.this.await(this, offset, length);
		}

		@Override
		public void close() {
			if (closed) return;
			closed = true;
			closeSession(id);
		}
	}

	private record PieceWindow(int first, int last) {
	}
}
