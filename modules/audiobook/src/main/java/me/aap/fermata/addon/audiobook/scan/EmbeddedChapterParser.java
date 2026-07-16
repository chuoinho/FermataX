package me.aap.fermata.addon.audiobook.scan;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aap.utils.vfs.VirtualResource;

/** Reads platform metadata and normalizes ID3, Vorbis and MP4 chapter marks. */
final class EmbeddedChapterParser {
	private final Context context;
	private final TaggedChapterParser taggedParser;
	private final Mp4ChapterParser mp4Parser;

	EmbeddedChapterParser(Context context) {
		this.context = context.getApplicationContext();
		taggedParser = new TaggedChapterParser(this.context);
		mp4Parser = new Mp4ChapterParser(this.context);
	}

	Metadata analyze(VirtualResource resource) {
		Uri uri = uri(resource);
		Builder builder = new Builder(stripExtension(resource.getName()));
		readPlatformMetadata(resource, uri, builder);
		String extension = extension(resource.getName());
		builder.marks.addAll(taggedParser.parse(uri, extension));
		if (extension.equals("m4a") || extension.equals("m4b") || extension.equals("mp4")) {
			builder.marks.addAll(mp4Parser.parse(uri));
		}
		return builder.build();
	}

	private void readPlatformMetadata(VirtualResource resource, Uri uri, Builder builder) {
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			File file = resource.getLocalFile();
			if (file != null) retriever.setDataSource(file.getPath());
			else retriever.setDataSource(context, uri);
			builder.title = prefer(builder.title, value(retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_TITLE)));
			builder.album = value(retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_ALBUM));
			builder.artist = value(retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_ARTIST));
			builder.albumArtist = value(retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
			builder.composer = value(retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_COMPOSER));
			builder.durationMs = longValue(retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_DURATION));
		} catch (Throwable ignore) {
			// A malformed tag must not prevent the file from entering the library.
		} finally {
			try {
				retriever.release();
			} catch (Throwable ignore) {
			}
		}
	}

	static long parseVorbisTime(String value) {
		String[] fields = value.trim().split(":");
		if (fields.length != 3) return -1;
		try {
			long hours = Long.parseLong(fields[0]);
			long minutes = Long.parseLong(fields[1]);
			double seconds = Double.parseDouble(fields[2]);
			if ((hours < 0) || (minutes < 0) || (minutes > 59) ||
					(seconds < 0) || (seconds >= 60)) return -1;
			return Math.round((((hours * 60L) + minutes) * 60D + seconds) * 1000D);
		} catch (NumberFormatException ignore) {
			return -1;
		}
	}

	static List<EmbeddedChapter> normalize(List<ChapterMark> source, long durationMs) {
		if (source.isEmpty()) return List.of();
		List<ChapterMark> sorted = new ArrayList<>(source);
		sorted.sort(Comparator.comparingLong(ChapterMark::startMs));
		Map<Long, ChapterMark> unique = new LinkedHashMap<>();
		for (ChapterMark mark : sorted) {
			long start = mark.startMs;
			if ((start < 0) || ((durationMs > 0) && (start >= durationMs))) continue;
			ChapterMark existing = unique.get(start);
			if ((existing == null) || (existing.title.isEmpty() && !mark.title.isEmpty())) {
				unique.put(start, new ChapterMark(start, clean(mark.title)));
			}
		}
		List<ChapterMark> marks = new ArrayList<>(unique.values());
		List<EmbeddedChapter> result = new ArrayList<>(marks.size());
		for (int index = 0; index < marks.size(); index++) {
			ChapterMark mark = marks.get(index);
			long end = (index + 1 < marks.size()) ? marks.get(index + 1).startMs : durationMs;
			long duration = (end > mark.startMs) ? end - mark.startMs : 0;
			String title = mark.title.isEmpty() ? "Chapter " + (index + 1) : mark.title;
			result.add(new EmbeddedChapter(title, mark.startMs, duration));
		}
		return result;
	}

	private static Uri uri(VirtualResource resource) {
		File file = resource.getLocalFile();
		return (file == null) ? resource.getRid().toAndroidUri() : Uri.fromFile(file);
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return clean((dot <= 0) ? name : name.substring(0, dot));
	}

	private static String prefer(String current, String replacement) {
		return current.isEmpty() ? replacement : current;
	}

	private static String value(String value) {
		return (value == null) ? "" : clean(value);
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	private static long longValue(String value) {
		try {
			return (value == null) ? 0 : Long.parseLong(value);
		} catch (NumberFormatException ignore) {
			return 0;
		}
	}

	record ChapterMark(long startMs, String title) {
	}

	record EmbeddedChapter(String title, long offsetMs, long durationMs) {
	}

	record Metadata(String title, String album, String artist, String albumArtist,
			String composer, long durationMs, List<EmbeddedChapter> chapters) {
	}

	private static final class Builder {
		private String title;
		private String album = "";
		private String artist = "";
		private String albumArtist = "";
		private String composer = "";
		private long durationMs;
		private final List<ChapterMark> marks = new ArrayList<>();

		private Builder(String fallbackTitle) {
			title = fallbackTitle;
		}

		private Metadata build() {
			return new Metadata(clean(title), clean(album), clean(artist), clean(albumArtist),
					clean(composer), Math.max(durationMs, 0), normalize(marks, durationMs));
		}
	}
}
