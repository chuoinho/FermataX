package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;

import org.junit.Test;

public class TorrentHttpServerRangeTest {
	@Test
	public void loopbackEndpointAcceptsIpv4PlayerConnections() throws Exception {
		TorrentHttpServer server = new TorrentHttpServer();
		try {
			assertEquals("127.0.0.1", TorrentHttpServer.bindAddress().getHostAddress());
			var endpoint = server.endpoint();
			assertEquals("127.0.0.1", endpoint.getHost());
			try (Socket client = new Socket()) {
				client.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), 1_000);
				assertTrue(client.isConnected());
			}
		} finally {
			server.close();
		}
	}

	@Test
	public void parsesFullOpenAndSuffixRanges() {
		var full = TorrentHttpServer.ByteRange.parse(null, 1_000L);
		assertEquals(0L, full.start());
		assertEquals(999L, full.end());
		assertEquals(1_000L, full.length());
		assertFalse(full.partial());

		var open = TorrentHttpServer.ByteRange.parse("bytes=100-", 1_000L);
		assertEquals(100L, open.start());
		assertEquals(999L, open.end());
		assertEquals(900L, open.length());
		assertTrue(open.partial());

		var suffix = TorrentHttpServer.ByteRange.parse("bytes=-128", 1_000L);
		assertEquals(872L, suffix.start());
		assertEquals(999L, suffix.end());
		assertEquals(128L, suffix.length());
	}

	@Test
	public void clampsEndAndRejectsInvalidOrMultipleRanges() {
		var clamped = TorrentHttpServer.ByteRange.parse("bytes=900-2000", 1_000L);
		assertEquals(900L, clamped.start());
		assertEquals(999L, clamped.end());

		assertThrows(IllegalArgumentException.class,
				() -> TorrentHttpServer.ByteRange.parse("items=0-1", 1_000L));
		assertThrows(IllegalArgumentException.class,
				() -> TorrentHttpServer.ByteRange.parse("bytes=0-1,4-5", 1_000L));
		assertThrows(IllegalArgumentException.class,
				() -> TorrentHttpServer.ByteRange.parse("bytes=1000-", 1_000L));
		assertThrows(IllegalArgumentException.class,
				() -> TorrentHttpServer.ByteRange.parse("bytes=-0", 1_000L));
	}

	@Test
	public void waitsForFirstPieceBeforeOpeningLazilyCreatedFile() throws Exception {
		File directory = Files.createTempDirectory("stremio-torrent").toFile();
		File file = new File(directory, "video.mkv");
		assertFalse(file.exists());

		try (var opened = TorrentHttpServer.openAfterAwait(file,
				() -> Files.write(file.toPath(), new byte[] {1, 2, 3}))) {
			assertEquals(3L, opened.length());
			assertEquals(1, opened.read());
		} finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	@Test
	public void rangeWindowAdaptsToThroughputWithinBounds() {
		assertEquals(16 * 1024 * 1024, TorrentHttpServer.priorityWindowBytes(0));
		assertEquals(64 * 1024 * 1024,
				TorrentHttpServer.priorityWindowBytes(8L * 1024L * 1024L));
		assertEquals(64 * 1024 * 1024,
				TorrentHttpServer.priorityWindowBytes(100L * 1024L * 1024L));
		assertEquals(512 * 1024,
				TorrentHttpServer.initialBufferLength(512L * 1024L, 0));
		assertEquals(8 * 1024 * 1024,
				TorrentHttpServer.initialBufferLength(100L * 1024L * 1024L, 0));
		assertEquals(8 * 1024 * 1024,
				TorrentHttpServer.initialBufferLength(100L * 1024L * 1024L,
						2L * 1024L * 1024L));
		assertEquals(16 * 1024 * 1024,
				TorrentHttpServer.initialBufferLength(100L * 1024L * 1024L,
						20L * 1024L * 1024L));
	}

	@Test
	public void largePiecesReceiveAdaptiveButBoundedStallTime() {
		assertEquals(45_000L, TorrentStreamPolicy.stallTimeoutMillis(32 * 1024 * 1024, 0));
		assertEquals(20_000L, TorrentStreamPolicy.stallTimeoutMillis(
				4 * 1024 * 1024, 1024L * 1024L));
		assertTrue(TorrentStreamPolicy.stallTimeoutMillis(
				32 * 1024 * 1024, 1024L * 1024L) > 20_000L);
		assertEquals(75_000L, TorrentStreamPolicy.stallTimeoutMillis(
				64 * 1024 * 1024, 128L * 1024L));
	}

	@Test
	public void bufferPercentOnlyRepresentsVerifiedRequestedBytes() {
		assertEquals(0, TorrentHttpServer.bufferScore(0, 0, 0, 1_000));
		assertEquals(99, TorrentHttpServer.bufferScore(
				8, 1024L * 1024L, 1_000, 1_000));
		assertEquals(50, TorrentHttpServer.bufferScore(
				4, 512L * 1024L, 500, 1_000));
		assertEquals(0, TorrentHttpServer.bufferScore(
				20, 8L * 1024L * 1024L, 0, 1_000));
	}

	@Test
	public void onlyRequestedRangeProgressKeepsAReadAlive() {
		assertTrue(TorrentStreamPolicy.requestedRangeAdvanced(1_024, 0));
		assertTrue(TorrentStreamPolicy.requestedRangeAdvanced(1, 0));
		assertFalse(TorrentStreamPolicy.requestedRangeAdvanced(0, 0));
		assertFalse(TorrentStreamPolicy.requestedRangeAdvanced(1_024, 1_024));
	}

}
