package me.aap.fermata.addon.stremio.torrent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/** Owns bounded, path-safe maintenance of {@code files/stremio/torrents}. */
final class TorrentCacheCleaner {
	private static final String ACCESS_MARKER = ".last-used";
	private static final String RESUME_TEMP = ".fastresume.tmp";
	private final File root;
	private final TorrentCachePolicy policy;
	private final LongSupplier clock;

	TorrentCacheCleaner(File root, TorrentCachePolicy policy, LongSupplier clock) {
		this.root = Objects.requireNonNull(root, "root");
		this.policy = Objects.requireNonNull(policy, "policy");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	synchronized CleanupResult cleanup(Set<File> protectedPaths) {
		if (!root.isDirectory()) return CleanupResult.EMPTY;
		Set<File> protectedEntries = protectedEntries(protectedPaths);
		File[] children = root.listFiles();
		if (children == null) return CleanupResult.EMPTY;
		List<Entry> entries = new ArrayList<>();
		long total = 0L;
		int temporaryFiles = 0;

		for (File child : children) {
			if (!isOwnedEntry(child)) {
				if (isRootTemporary(child) && deletePath(child)) temporaryFiles++;
				continue;
			}
			boolean protectedEntry = protectedEntries.contains(canonical(child));
			if (!protectedEntry) temporaryFiles += deleteTemporaryFiles(child);
			long size = size(child);
			total += size;
			entries.add(new Entry(child, size, lastUsed(child), protectedEntry));
		}

		entries.sort(Comparator.comparingLong(Entry::lastUsed));
		long now = clock.getAsLong();
		long removedBytes = 0L;
		int removedEntries = 0;
		for (Entry entry : entries) {
			if (entry.protectedEntry() || ((now - entry.lastUsed()) < policy.ttlMillis())) continue;
			if (deletePath(entry.directory())) {
				removedBytes += entry.size();
				removedEntries++;
				total -= entry.size();
			}
		}

		long freeDeficit = Math.max(0L, policy.minFreeBytes() - usableSpace());
		long targetBytes = Math.min(policy.maxBytes(), Math.max(0L, total - freeDeficit));
		if (total > targetBytes) {
			for (Entry entry : entries) {
				if ((total <= targetBytes) || entry.protectedEntry() ||
						!entry.directory().exists()) continue;
				if (deletePath(entry.directory())) {
					removedBytes += entry.size();
					removedEntries++;
					total -= entry.size();
				}
			}
		}
		return new CleanupResult(removedEntries, removedBytes, temporaryFiles, Math.max(total, 0L));
	}

	synchronized boolean deleteFailedEntry(File directory, boolean existedBefore) {
		if (!isOwnedEntry(directory)) return false;
		if (existedBefore) {
			deleteTemporaryFiles(directory);
			return false;
		}
		return deletePath(directory);
	}

	synchronized void touch(File path) {
		File entry = ownedEntry(path);
		if (entry == null || (!entry.isDirectory() && !entry.mkdirs())) return;
		File marker = new File(entry, ACCESS_MARKER);
		try {
			Files.write(marker.toPath(), new byte[0], StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			if (!marker.setLastModified(clock.getAsLong())) entry.setLastModified(clock.getAsLong());
		} catch (IOException ignored) {
			entry.setLastModified(clock.getAsLong());
		}
	}

	private Set<File> protectedEntries(Set<File> paths) {
		Set<File> entries = new HashSet<>();
		if (paths == null) return entries;
		for (File path : paths) {
			File entry = ownedEntry(path);
			if (entry != null) entries.add(canonical(entry));
		}
		return entries;
	}

	private File ownedEntry(File path) {
		if (path == null) return null;
		File rootPath = canonical(root);
		File current = canonical(path);
		while ((current != null) && !current.equals(rootPath)) {
			File parent = current.getParentFile();
			if ((parent != null) && canonical(parent).equals(rootPath)) {
				return isOwnedEntry(current) ? current : null;
			}
			current = parent;
		}
		return null;
	}

	private boolean isOwnedEntry(File file) {
		if ((file == null) || !isDirectChild(file) || !file.getName().matches("[0-9a-f]{32}")) {
			return false;
		}
		return !Files.isSymbolicLink(file.toPath());
	}

	private boolean isDirectChild(File file) {
		File parent = file.getParentFile();
		return (parent != null) && canonical(parent).equals(canonical(root));
	}

	private boolean isRootTemporary(File file) {
		String name = file.getName().toLowerCase(Locale.ROOT);
		return file.isFile() && (name.endsWith(".tmp") || name.endsWith(".part")) &&
				((clock.getAsLong() - file.lastModified()) >= policy.ttlMillis());
	}

	private int deleteTemporaryFiles(File directory) {
		File[] files = directory.listFiles();
		if (files == null) return 0;
		int deleted = 0;
		for (File file : files) {
			if (Files.isSymbolicLink(file.toPath())) continue;
			if (file.isDirectory()) deleted += deleteTemporaryFiles(file);
			else if ((RESUME_TEMP.equals(file.getName()) || file.getName().endsWith(".tmp")) &&
					deletePath(file)) deleted++;
		}
		return deleted;
	}

	private static long lastUsed(File directory) {
		File marker = new File(directory, ACCESS_MARKER);
		return Math.max(directory.lastModified(), marker.isFile() ? marker.lastModified() : 0L);
	}

	private long usableSpace() {
		long usable = root.getUsableSpace();
		return (usable <= 0L) ? Long.MAX_VALUE : usable;
	}

	private static long size(File file) {
		if (Files.isSymbolicLink(file.toPath())) return 0L;
		if (!file.isDirectory()) return Math.max(file.length(), 0L);
		File[] children = file.listFiles();
		if (children == null) return 0L;
		long result = 0L;
		for (File child : children) result += size(child);
		return result;
	}

	private static boolean deletePath(File file) {
		if ((file == null) || !file.exists()) return true;
		if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) for (File child : children) {
				if (!deletePath(child)) return false;
			}
		}
		return file.delete();
	}

	private static File canonical(File file) {
		try {
			return file.getCanonicalFile();
		} catch (IOException ignored) {
			return file.getAbsoluteFile();
		}
	}

	private record Entry(File directory, long size, long lastUsed, boolean protectedEntry) {
	}

	record CleanupResult(int removedEntries, long removedBytes, int temporaryFiles,
			long retainedBytes) {
		static final CleanupResult EMPTY = new CleanupResult(0, 0L, 0, 0L);
	}
}
