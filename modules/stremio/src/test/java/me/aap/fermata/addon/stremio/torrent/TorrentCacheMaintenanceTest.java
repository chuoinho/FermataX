package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class TorrentCacheMaintenanceTest {
	@Test
	public void releasingNestedMediaFileRemovesDirectoryOwnership() throws Exception {
		File root = Files.createTempDirectory("stremio-cache-maintenance").toFile();
		try {
			File entry = new File(root, "0123456789abcdef0123456789abcdef");
			File media = new File(entry, "folder/video.mkv");
			Files.createDirectories(media.getParentFile().toPath());
			Files.write(media.toPath(), new byte[]{1, 2, 3});
			long now = System.currentTimeMillis();
			AtomicLong clock = new AtomicLong(now);
			TorrentCacheMaintenance maintenance = new TorrentCacheMaintenance(root,
					Runnable::run, new TorrentCachePolicy(1L, 1024L, 0L),
					clock::get, () -> false);
			maintenance.prepared(entry);
			maintenance.released(media);
			clock.set(now + 10_000L);
			maintenance.run();
			assertFalse(entry.exists());
		} finally {
			delete(root);
		}
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		file.delete();
	}
}
