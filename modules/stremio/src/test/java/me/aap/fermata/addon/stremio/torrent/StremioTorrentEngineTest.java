package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.addon.stremio.net.NetworkConsent;

public class StremioTorrentEngineTest {
	@Test
	public void resumeDataIsReplacedAtomically() throws Exception {
		File directory = Files.createTempDirectory("stremio-resume").toFile();
		File target = new File(directory, ".fastresume");
		try {
			StremioTorrentEngine.writeAtomically(target, new byte[] {1, 2});
			StremioTorrentEngine.writeAtomically(target, new byte[] {3, 4, 5});
			assertArrayEquals(new byte[] {3, 4, 5}, Files.readAllBytes(target.toPath()));
			assertFalse(new File(directory, ".fastresume.tmp").exists());
		} finally {
			Files.deleteIfExists(target.toPath());
			deleteTemporaryDirectory(directory);
		}
	}

	private static void deleteTemporaryDirectory(File directory) throws Exception {
		for (int attempt = 0; attempt < 10; attempt++) {
			try {
				Files.deleteIfExists(directory.toPath());
				return;
			} catch (DirectoryNotEmptyException busy) {
				if (attempt == 9) throw busy;
				Thread.sleep(25L);
			}
		}
	}

	@Test
	public void closeCancelsQueuedPreparation() {
		AtomicReference<Runnable> queued = new AtomicReference<>();
		Executor executor = queued::set;
		StremioTorrentEngine engine = new StremioTorrentEngine(
				new File("build/test-torrent-cache"), executor);
		var future = engine.prepare(new InfoHashStreamTarget(
				"0123456789abcdef0123456789abcdef01234567", 0, List.of()));

		engine.close();

		assertTrue(future.isCancelled());
	}

	@Test
	public void trackerValidationHonorsCleartextAndLanConsent() throws Exception {
		StremioTorrentEngine publicEngine = new StremioTorrentEngine(
				new File("build/test-torrent-cache"), Runnable::run,
				host -> List.of(java.net.InetAddress.getByName("8.8.8.8")));
		assertTrue(publicEngine.isAllowedTracker(
				"https://tracker.example/announce", NetworkConsent.STRICT));
		assertFalse(publicEngine.isAllowedTracker(
				"http://tracker.example/announce", NetworkConsent.STRICT));
		assertTrue(publicEngine.isAllowedTracker("http://tracker.example/announce",
				new NetworkConsent(true, false)));
		assertTrue(publicEngine.isAllowedTracker(
				"udp://tracker.example:80/announce", NetworkConsent.STRICT));
		assertTrue(publicEngine.isAllowedTracker("udp://tracker.example:80/announce",
				new NetworkConsent(true, false)));
		assertFalse(publicEngine.isAllowedTracker(
				"udp://tracker.example/announce", NetworkConsent.STRICT));
		var fallback = publicEngine.allowedTrackers(new InfoHashStreamTarget(
				"0123456789abcdef0123456789abcdef01234567", 0, List.of()),
				NetworkConsent.STRICT);
		assertEquals(4, fallback.size());
		assertEquals(4, publicEngine.allowedTrackers(new InfoHashStreamTarget(
				"0123456789abcdef0123456789abcdef01234567", 0,
				List.of("tracker:udp://tracker.opentrackr.org:1337/announce")),
				NetworkConsent.STRICT).size());
		publicEngine.close();

		StremioTorrentEngine privateEngine = new StremioTorrentEngine(
				new File("build/test-torrent-cache"), Runnable::run,
				host -> List.of(java.net.InetAddress.getByName("192.168.1.2")));
		assertFalse(privateEngine.isAllowedTracker("https://tracker.lan/announce",
				NetworkConsent.STRICT));
		assertFalse(privateEngine.isAllowedTracker("udp://tracker.lan:80/announce",
				NetworkConsent.STRICT));
		assertTrue(privateEngine.isAllowedTracker("https://tracker.lan/announce",
				new NetworkConsent(false, true)));
		assertTrue(privateEngine.isAllowedTracker("udp://tracker.lan:80/announce",
				new NetworkConsent(false, true)));
		privateEngine.close();
	}

	@Test
	public void requestedFileIndexMustMatchTorrentMetadata() {
		StremioTorrentEngine.validateRequestedFileIndex(0, 1);
		assertThrows(IllegalArgumentException.class,
				() -> StremioTorrentEngine.validateRequestedFileIndex(1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> StremioTorrentEngine.validateRequestedFileIndex(-1, 1));
	}
}
