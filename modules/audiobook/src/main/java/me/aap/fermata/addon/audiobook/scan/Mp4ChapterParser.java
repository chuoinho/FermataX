package me.aap.fermata.addon.audiobook.scan;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.audiobook.scan.EmbeddedChapterParser.ChapterMark;

/**
 * Bounded QuickTime chpl/chapter-track parser.
 * Parsing strategy adapted from Voice (Paul Woitaschek), GPL-3.0.
 */
final class Mp4ChapterParser {
	private static final int HEADER_SIZE = 8;
	private static final int LONG_HEADER_SIZE = 16;
	private static final int MAX_VISITOR_PAYLOAD = 16 * 1024 * 1024;
	private static final int MAX_ENTRIES = 100_000;
	private static final int MAX_CHAPTERS = 10_000;
	private static final String[] TARGETS = {
			"moov/udta/chpl", "moov/trak/tref/chap", "moov/trak/mdia/mdhd",
			"moov/trak/mdia/minf/stbl/stco", "moov/trak/mdia/minf/stbl/co64",
			"moov/trak/mdia/minf/stbl/stsc", "moov/trak/mdia/minf/stbl/stts"
	};
	private final Context context;

	Mp4ChapterParser(Context context) {
		this.context = context.getApplicationContext();
	}

	List<ChapterMark> parse(Uri uri) {
		try (SeekableMediaInput input = SeekableMediaInput.open(context, uri)) {
			long length = input.length();
			if (length <= HEADER_SIZE) return List.of();
			Output output = new Output();
			parseBoxes(input, "", 0, length, output);
			if (!output.chpl.isEmpty()) return output.chpl;
			return (output.chapterTrackId == null) ? List.of() :
					readChapterTrack(input, output.chapterTrackId, output);
		} catch (Throwable ignore) {
			return List.of();
		}
	}

	private static void parseBoxes(SeekableMediaInput input, String parentPath, long start,
			long parentEnd, Output output) throws IOException {
		byte[] header = new byte[LONG_HEADER_SIZE];
		long position = start;
		while (position + HEADER_SIZE <= parentEnd) {
			input.readFully(position, header, 0, HEADER_SIZE);
			long atomSize = unsignedInt(header, 0);
			String atomType = new String(header, 4, 4, StandardCharsets.ISO_8859_1);
			int headerSize = HEADER_SIZE;
			if (atomSize == 1) {
				if (position + LONG_HEADER_SIZE > parentEnd) return;
				input.readFully(position + HEADER_SIZE, header, HEADER_SIZE,
						LONG_HEADER_SIZE - HEADER_SIZE);
				atomSize = signedLong(header, HEADER_SIZE);
				headerSize = LONG_HEADER_SIZE;
			} else if (atomSize == 0) {
				atomSize = parentEnd - position;
			}
			if (atomSize < headerSize) return;
			long payloadStart = position + headerSize;
			long payloadEnd = position + atomSize;
			if ((payloadEnd < payloadStart) || (payloadEnd > parentEnd)) return;
			String path = parentPath.isEmpty() ? atomType : parentPath + '/' + atomType;

			if (isTarget(path)) {
				long payloadSize = payloadEnd - payloadStart;
				if ((payloadSize > MAX_VISITOR_PAYLOAD) || (payloadSize > Integer.MAX_VALUE)) return;
				byte[] payload = new byte[(int) payloadSize];
				input.readFully(payloadStart, payload);
				visit(path, payload, output);
				if (!output.chpl.isEmpty()) return;
			} else if (hasTargetBelow(path)) {
				parseBoxes(input, path, payloadStart, payloadEnd, output);
				if (!output.chpl.isEmpty()) return;
			}
			position = payloadEnd;
		}
	}

	private static void visit(String path, byte[] data, Output output) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
			switch (path) {
				case "moov/udta/chpl" -> visitChpl(buffer, output);
				case "moov/trak/tref/chap" -> output.chapterTrackId = buffer.getInt();
				case "moov/trak/mdia/mdhd" -> visitMdhd(buffer, output);
				case "moov/trak/mdia/minf/stbl/stco" -> visitStco(buffer, output, false);
				case "moov/trak/mdia/minf/stbl/co64" -> visitStco(buffer, output, true);
				case "moov/trak/mdia/minf/stbl/stsc" -> visitStsc(buffer, output);
				case "moov/trak/mdia/minf/stbl/stts" -> visitStts(buffer, output);
				default -> {
				}
			}
		} catch (RuntimeException ignore) {
			// Skip the malformed atom while preserving playback of the enclosing file.
		}
	}

	private static void visitChpl(ByteBuffer buffer, Output output) {
		int version = buffer.get() & 0xff;
		buffer.position(buffer.position() + 3);
		if ((version != 0) && (version != 1)) return;
		if (version == 1) buffer.position(buffer.position() + 4);
		int count = Math.min(buffer.get() & 0xff, MAX_CHAPTERS);
		List<ChapterMark> chapters = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			long timestamp = (version == 0) ? unsignedInt(buffer.getInt()) : buffer.getLong();
			int titleLength = buffer.get() & 0xff;
			byte[] title = new byte[titleLength];
			buffer.get(title);
			chapters.add(new ChapterMark(timestamp / 10_000,
					new String(title, StandardCharsets.UTF_8).trim()));
		}
		output.chpl = chapters;
	}

	private static void visitMdhd(ByteBuffer buffer, Output output) {
		int version = buffer.get() & 0xff;
		if ((version != 0) && (version != 1)) return;
		buffer.position(buffer.position() + 3 + ((version == 0) ? 8 : 16));
		output.timeScales.add(unsignedInt(buffer.getInt()));
	}

	private static void visitStco(ByteBuffer buffer, Output output, boolean wide) {
		if ((buffer.get() & 0xff) != 0) return;
		buffer.position(buffer.position() + 3);
		int count = checkedCount(buffer.getInt());
		List<Long> offsets = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			offsets.add(wide ? buffer.getLong() : unsignedInt(buffer.getInt()));
		}
		output.chunkOffsets.add(offsets);
	}

	private static void visitStsc(ByteBuffer buffer, Output output) {
		if ((buffer.get() & 0xff) != 0) return;
		buffer.position(buffer.position() + 3);
		int count = checkedCount(buffer.getInt());
		List<StscEntry> entries = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			entries.add(new StscEntry(unsignedInt(buffer.getInt()), buffer.getInt()));
			buffer.getInt();
		}
		output.stsc.add(entries);
	}

	private static void visitStts(ByteBuffer buffer, Output output) {
		if ((buffer.get() & 0xff) != 0) return;
		buffer.position(buffer.position() + 3);
		int count = checkedCount(buffer.getInt());
		List<SttsEntry> entries = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			entries.add(new SttsEntry(unsignedInt(buffer.getInt()), unsignedInt(buffer.getInt())));
		}
		output.durations.add(entries);
	}

	private static List<ChapterMark> readChapterTrack(SeekableMediaInput input, int trackId,
			Output output) throws IOException {
		int trackIndex = trackId - 1;
		if ((trackIndex < 0) || (trackIndex >= output.chunkOffsets.size()) ||
				(trackIndex >= output.timeScales.size()) ||
				(trackIndex >= output.durations.size()) || (trackIndex >= output.stsc.size())) {
			return List.of();
		}
		List<Long> offsets = output.chunkOffsets.get(trackIndex);
		if (offsets.size() > MAX_CHAPTERS) return List.of();
		List<String> names = new ArrayList<>(offsets.size());
		for (long offset : offsets) names.add(readChapterName(input, offset));
		long timeScale = output.timeScales.get(trackIndex);
		if (timeScale <= 0) return List.of();

		List<SttsEntry> durations = output.durations.get(trackIndex);
		List<StscEntry> stsc = output.stsc.get(trackIndex);
		List<ChapterMark> result = new ArrayList<>(offsets.size());
		long position = 0;
		int durationIndex = 0;
		long consumedInDuration = 0;
		for (int chunk = 0; chunk < offsets.size(); chunk++) {
			result.add(new ChapterMark(position * 1000 / timeScale, names.get(chunk)));
			long remaining = samplesPerChunk(chunk, stsc);
			while ((remaining > 0) && (durationIndex < durations.size())) {
				SttsEntry entry = durations.get(durationIndex);
				long available = entry.sampleCount - consumedInDuration;
				if (available <= 0) {
					durationIndex++;
					consumedInDuration = 0;
					continue;
				}
				long consumed = Math.min(remaining, available);
				position += consumed * entry.sampleDuration;
				remaining -= consumed;
				consumedInDuration += consumed;
			}
		}
		return result;
	}

	private static String readChapterName(SeekableMediaInput input, long offset)
			throws IOException {
		byte[] lengthBytes = new byte[2];
		input.readFully(offset, lengthBytes);
		int length = ((lengthBytes[0] & 0xff) << 8) | (lengthBytes[1] & 0xff);
		if (length > 64 * 1024) throw new IOException("Invalid chapter title length");
		byte[] title = new byte[length];
		input.readFully(offset + 2, title);
		return new String(title, StandardCharsets.UTF_8).trim();
	}

	private static int samplesPerChunk(int chunkIndex, List<StscEntry> entries) {
		for (int index = 0; index < entries.size(); index++) {
			StscEntry current = entries.get(index);
			StscEntry next = (index + 1 < entries.size()) ? entries.get(index + 1) : null;
			long chunk = chunkIndex + 1L;
			if ((chunk >= current.firstChunk) &&
					((next == null) || (chunk < next.firstChunk))) return current.samplesPerChunk;
		}
		return 1;
	}

	private static int checkedCount(int count) {
		if ((count < 0) || (count > MAX_ENTRIES)) {
			throw new IllegalArgumentException("Invalid MP4 table size");
		}
		return count;
	}

	private static boolean isTarget(String path) {
		for (String target : TARGETS) if (target.equals(path)) return true;
		return false;
	}

	private static boolean hasTargetBelow(String path) {
		String prefix = path + '/';
		for (String target : TARGETS) if (target.startsWith(prefix)) return true;
		return false;
	}

	private static long unsignedInt(byte[] value, int offset) {
		return ((long) (value[offset] & 0xff) << 24) |
				((long) (value[offset + 1] & 0xff) << 16) |
				((long) (value[offset + 2] & 0xff) << 8) |
				(value[offset + 3] & 0xff);
	}

	private static long unsignedInt(int value) {
		return value & 0xffffffffL;
	}

	private static long signedLong(byte[] value, int offset) {
		return ByteBuffer.wrap(value, offset, 8).order(ByteOrder.BIG_ENDIAN).getLong();
	}

	private static final class Output {
		private final List<List<Long>> chunkOffsets = new ArrayList<>();
		private final List<List<SttsEntry>> durations = new ArrayList<>();
		private final List<List<StscEntry>> stsc = new ArrayList<>();
		private final List<Long> timeScales = new ArrayList<>();
		private List<ChapterMark> chpl = List.of();
		private Integer chapterTrackId;
	}

	private record SttsEntry(long sampleCount, long sampleDuration) {
	}

	private record StscEntry(long firstChunk, int samplesPerChunk) {
	}
}
