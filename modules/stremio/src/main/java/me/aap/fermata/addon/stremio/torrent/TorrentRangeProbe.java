package me.aap.fermata.addon.stremio.torrent;

import java.util.LinkedHashSet;

import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;

/** Head/tail piece plan used to prove that a container can reach the player. */
final class TorrentRangeProbe {
	private static final long HEAD_BYTES = 1024L * 1024L;
	private static final long TAIL_BYTES = 512L * 1024L;
	private final int[] pieces;
	private final long targetBytes;

	TorrentRangeProbe(TorrentInfo info, int fileIndex) {
		Plan plan = plan(info.files().fileOffset(fileIndex), info.files().fileSize(fileIndex),
				info.pieceLength(), info.numPieces(), HEAD_BYTES, TAIL_BYTES);
		pieces = plan.pieces();
		long bytes = 0L;
		for (int piece : pieces) bytes += info.pieceSize(piece);
		targetBytes = bytes;
	}

	TorrentRangeProbe(Plan plan) {
		pieces = plan.pieces();
		targetBytes = plan.targetBytes();
	}

	int[] pieces() {
		return pieces.clone();
	}

	long targetBytes() {
		return targetBytes;
	}

	long verifiedBytes(TorrentHandle handle, TorrentInfo info) {
		long verified = 0L;
		for (int piece : pieces) {
			if (handle.havePiece(piece)) verified += info.pieceSize(piece);
		}
		return Math.min(verified, targetBytes);
	}

	boolean isReady(TorrentHandle handle) {
		for (int piece : pieces) if (!handle.havePiece(piece)) return false;
		return pieces.length != 0;
	}

	static Plan plan(long fileOffset, long fileSize, int pieceLength, int pieceCount,
			long headBytes, long tailBytes) {
		if ((fileOffset < 0L) || (fileSize <= 0L) || (pieceLength <= 0) || (pieceCount <= 0)) {
			throw new IllegalArgumentException("Invalid torrent file geometry");
		}
		long fileEnd = Math.addExact(fileOffset, fileSize);
		LinkedHashSet<Integer> selected = new LinkedHashSet<>();
		addRange(selected, fileOffset, Math.min(fileEnd, fileOffset + headBytes),
				pieceLength, pieceCount);
		addRange(selected, Math.max(fileOffset, fileEnd - tailBytes), fileEnd,
				pieceLength, pieceCount);
		int[] pieces = selected.stream().mapToInt(Integer::intValue).toArray();
		long target = Math.multiplyExact((long) pieces.length, pieceLength);
		return new Plan(pieces, target);
	}

	private static void addRange(LinkedHashSet<Integer> pieces, long start, long endExclusive,
			int pieceLength, int pieceCount) {
		if (endExclusive <= start) return;
		int first = Math.max(0, (int) (start / pieceLength));
		int last = Math.min(pieceCount - 1, (int) ((endExclusive - 1L) / pieceLength));
		for (int piece = first; piece <= last; piece++) pieces.add(piece);
	}

	record Plan(int[] pieces, long targetBytes) {
	}
}
