package me.aap.fermata.addon.audiobook.scan;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.util.AudiobookIds;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/** Blocking scanner. Call it only from the application worker pool. */
public final class AudiobookScanner {
	private static final int MAX_DEPTH = 16;
	private static final int MAX_FILES = 5_000;
	private static final long VFS_TIMEOUT_SECONDS = 30;
	private final EmbeddedChapterParser metadataParser;

	public AudiobookScanner(Context context) {
		metadataParser = new EmbeddedChapterParser(context);
	}

	public ScannedBook scan(VirtualFolder folder) throws Exception {
		String endpoint = folder.getRid().toString();
		List<VirtualResource> files = new ArrayList<>();
		collect(folder, files, new HashSet<>(), 0);
		files.sort(VirtualResource::compareTo);
		if (files.isEmpty()) throw new IOException("The selected folder has no supported audio files");

		String sourceId = AudiobookIds.source("local", endpoint);
		String bookId = AudiobookIds.book("local", endpoint);
		long now = System.currentTimeMillis();
		List<AudiobookChapter> chapters = new ArrayList<>(files.size());
		String title = clean(folder.getName());
		String author = "";
		String narrator = "";
		long totalDuration = 0;

		int chapterIndex = 0;
		for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
			VirtualResource file = files.get(fileIndex);
			EmbeddedChapterParser.Metadata metadata = metadataParser.analyze(file);
			if ((fileIndex == 0) && !metadata.album().isEmpty()) title = metadata.album();
			if (author.isEmpty()) author = first(metadata.albumArtist(), metadata.artist());
			if (narrator.isEmpty()) narrator = metadata.composer();
			long duration = Math.max(metadata.durationMs(), 0);
			long fileBookOffset = totalDuration;
			totalDuration += duration;
			String rid = file.getRid().toString();
			String mimeType = mimeType(file.getName());
			if (metadata.chapters().isEmpty()) {
				String chapterTitle = metadata.title().isEmpty() ? stripExtension(file.getName()) :
						metadata.title();
				chapters.add(new AudiobookChapter(bookId, AudiobookIds.chapter(rid),
						chapterIndex++, chapterTitle, rid, mimeType, 0, fileBookOffset, duration,
						false, null, 0));
			} else {
				for (EmbeddedChapterParser.EmbeddedChapter embedded : metadata.chapters()) {
					String chapterKey = rid + "#chapter=" + embedded.offsetMs() + ':' + chapterIndex;
					chapters.add(new AudiobookChapter(bookId, AudiobookIds.chapter(chapterKey),
							chapterIndex++, embedded.title(), rid, mimeType, embedded.offsetMs(),
							fileBookOffset + embedded.offsetMs(), embedded.durationMs(), true,
							null, 0));
				}
			}
		}

		if (title.isEmpty()) title = "Audiobook";
		AudiobookSource source = new AudiobookSource(sourceId, AudiobookSourceType.LOCAL,
				title, endpoint, null, now, now);
		AudiobookBook book = new AudiobookBook(bookId, sourceId, null, title, author,
				narrator, "", "", "", totalDuration, null, 0, 0, false, now, now);
		return new ScannedBook(source, book, chapters);
	}

	private void collect(VirtualFolder folder, List<VirtualResource> files, Set<String> visited,
			int depth) throws Exception {
		if (depth > MAX_DEPTH) throw new IOException("Audiobook folder nesting is too deep");
		if (!visited.add(folder.getRid().toString())) return;
		List<VirtualResource> children = folder.getChildren().get(VFS_TIMEOUT_SECONDS,
				TimeUnit.SECONDS);
		children.sort(VirtualResource::compareTo);
		for (VirtualResource child : children) {
			if (child.isFolder()) {
				collect((VirtualFolder) child, files, visited, depth + 1);
			} else if (child.isFile() && isAudio(child.getName())) {
				files.add(child);
				if (files.size() > MAX_FILES) {
					throw new IOException("The selected audiobook contains too many files");
				}
			}
		}
	}

	static boolean isAudio(String name) {
		String extension = extension(name);
		return switch (extension) {
			case "mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "wma" -> true;
			default -> false;
		};
	}

	static String mimeType(String name) {
		return switch (extension(name)) {
			case "mp3" -> "audio/mpeg";
			case "m4a", "m4b" -> "audio/mp4";
			case "aac" -> "audio/aac";
			case "ogg", "opus" -> "audio/ogg";
			case "flac" -> "audio/flac";
			case "wav" -> "audio/wav";
			case "wma" -> "audio/x-ms-wma";
			default -> "application/octet-stream";
		};
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String stripExtension(String value) {
		int dot = value.lastIndexOf('.');
		return clean((dot <= 0) ? value : value.substring(0, dot));
	}

	private static String first(String first, String second) {
		return first.isEmpty() ? second : first;
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	public record ScannedBook(AudiobookSource source, AudiobookBook book,
			List<AudiobookChapter> chapters) {
	}
}
