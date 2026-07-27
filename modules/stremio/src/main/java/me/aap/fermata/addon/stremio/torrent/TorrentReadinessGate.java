package me.aap.fermata.addon.stremio.torrent;

import java.net.SocketTimeoutException;
import java.util.function.Consumer;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;

import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.engine.PlaybackFailureException.Reason;
import me.aap.fermata.media.net.RemotePlaybackProgress;

/** Requires verified head/tail data before a loopback URL is handed to a decoder. */
final class TorrentReadinessGate {
	private static final long NO_PEERS_TIMEOUT_MILLIS = 20_000L;
	private static final long STALL_TIMEOUT_MILLIS = 20_000L;
	private static final long HARD_TIMEOUT_MILLIS = 35_000L;

	void await(TorrentHandle handle, TorrentInfo info, int fileIndex,
			TorrentAlertRouter alerts, Consumer<RemotePlaybackProgress> progress,
			TorrentPreparationCoordinator.Cancellation cancellation) throws Exception {
		TorrentRangeProbe probe = new TorrentRangeProbe(info, fileIndex);
		int deadline = 0;
		for (int piece : probe.pieces()) {
			handle.piecePriority(piece, Priority.SEVEN);
			handle.setPieceDeadline(piece, deadline);
			deadline += 50;
		}
		long started = System.currentTimeMillis();
		long lastAdvance = started;
		long lastDone = -1L;
		try (TorrentWaiter waiter = new TorrentWaiter(alerts, handle.infoHash().toHex())) {
			while (true) {
				cancellation.throwIfCancelled();
				if (!handle.isValid()) throw new PlaybackFailureException(
						Reason.P2P_ENGINE_UNAVAILABLE);
				TorrentStatus status = handle.status();
				if (status.errorCode().isError()) throw new PlaybackFailureException(
						Reason.P2P_ENGINE_UNAVAILABLE);
				long now = System.currentTimeMillis();
				long verified = probe.verifiedBytes(handle, info);
				if (probe.isReady(handle)) {
					TorrentProgressMapper.publish(progress, TorrentProgressMapper.ready(
							status, verified, probe.targetBytes(), true));
					return;
				}
				long downloaded = status.totalWantedDone();
				if (downloaded > lastDone) {
					lastDone = downloaded;
					lastAdvance = now;
				}
				TorrentProgressMapper.publish(progress, TorrentProgressMapper.waiting(
						status, false, verified, probe.targetBytes()));
				if ((status.numPeers() == 0) && (status.numSeeds() == 0) &&
						(now - started >= NO_PEERS_TIMEOUT_MILLIS)) {
					throw new PlaybackFailureException(Reason.P2P_NO_PEERS);
				}
				if ((now - lastAdvance >= STALL_TIMEOUT_MILLIS) ||
						(now - started >= HARD_TIMEOUT_MILLIS)) {
					throw new PlaybackFailureException(Reason.P2P_DATA_TIMEOUT,
							new SocketTimeoutException("Torrent readiness timed out"));
				}
				long next = Math.min(started + HARD_TIMEOUT_MILLIS,
						Math.min(lastAdvance + STALL_TIMEOUT_MILLIS,
								started + NO_PEERS_TIMEOUT_MILLIS));
				waiter.awaitSignal(Math.max(1L, Math.min(1_000L, next - now)));
			}
		}
	}
}
