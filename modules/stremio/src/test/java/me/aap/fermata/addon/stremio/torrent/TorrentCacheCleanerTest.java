package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

import org.junit.Test;

public class TorrentCacheCleanerTest {
	private static final String FIRST = "11111111111111111111111111111111";
	private static final String SECOND = "22222222222222222222222222222222";

	@Test
	public void removesExpiredEntryButKeepsActiveAndUnknownData() throws Exception {
		File root = Files.createTempDirectory("torrent-cache-cleaner").toFile();
		try {
			File expired = entry(root, FIRST, 16, 1_000L);
			File active = entry(root, SECOND, 16, 1_000L);
			File unknown = entry(root, "not-owned", 16, 1_000L);
			TorrentCacheCleaner cleaner = new TorrentCacheCleaner(root,
					new TorrentCachePolicy(1_000L, 1_000L, 0L), () -> 10_000L);

			TorrentCacheCleaner.CleanupResult result = cleaner.cleanup(
					Set.of(new File(active, "movie.mkv")));

			assertFalse(expired.exists());
			assertTrue(active.exists());
			assertTrue(unknown.exists());
			assertEquals(1, result.removedEntries());
		} finally {
			delete(root);
		}
	}

	@Test
	public void quotaDeletesOldestUnusedEntryFirst() throws Exception {
		File root = Files.createTempDirectory("torrent-cache-quota").toFile();
		try {
			File oldest = entry(root, FIRST, 16, 1_000L);
			File newest = entry(root, SECOND, 16, 2_000L);
			TorrentCacheCleaner cleaner = new TorrentCacheCleaner(root,
					new TorrentCachePolicy(100_000L, 20L, 0L), () -> 3_000L);

			TorrentCacheCleaner.CleanupResult result = cleaner.cleanup(Set.of());

			assertFalse(oldest.exists());
			assertTrue(newest.exists());
			assertEquals(1, result.removedEntries());
			assertTrue(result.retainedBytes() <= 20L);
		} finally {
			delete(root);
		}
	}

	@Test
	public void failedAttemptPreservesExistingPayloadAndOnlyRemovesTemporaryFile()
			throws Exception {
		File root = Files.createTempDirectory("torrent-cache-failure").toFile();
		try {
			File existing = entry(root, FIRST, 16, 1_000L);
			File temporary = new File(existing, ".fastresume.tmp");
			Files.write(temporary.toPath(), new byte[] {1});
			TorrentCacheCleaner cleaner = new TorrentCacheCleaner(root,
					new TorrentCachePolicy(1_000L, 1_000L, 0L), () -> 10_000L);

			assertFalse(cleaner.deleteFailedEntry(existing, true));
			assertTrue(existing.exists());
			assertTrue(new File(existing, "movie.mkv").exists());
			assertFalse(temporary.exists());
		} finally {
			delete(root);
		}
	}

	@Test
	public void failedNewAttemptRemovesOnlyOwnedHashDirectory() throws Exception {
		File root = Files.createTempDirectory("torrent-cache-owned").toFile();
		File outside = Files.createTempDirectory("torrent-cache-outside").toFile();
		try {
			File failed = entry(root, FIRST, 16, 1_000L);
			File external = entry(outside, SECOND, 16, 1_000L);
			TorrentCacheCleaner cleaner = new TorrentCacheCleaner(root,
					new TorrentCachePolicy(1_000L, 1_000L, 0L), () -> 10_000L);

			assertTrue(cleaner.deleteFailedEntry(failed, false));
			assertFalse(failed.exists());
			assertFalse(cleaner.deleteFailedEntry(external, false));
			assertTrue(external.exists());
		} finally {
			delete(root);
			delete(outside);
		}
	}

	private static File entry(File root, String name, int bytes, long lastUsed) throws Exception {
		File directory = new File(root, name);
		assertTrue(directory.mkdirs());
		Files.write(new File(directory, "movie.mkv").toPath(), new byte[bytes]);
		assertTrue(directory.setLastModified(lastUsed));
		return directory;
	}

	private static void delete(File file) throws Exception {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		Files.deleteIfExists(file.toPath());
	}
}
