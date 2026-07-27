package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TorrentLoopbackProbeTest {
	@Test
	public void verifiesHeadAndTailRangesOnPrivateBridge() throws Exception {
		List<String> ranges = new ArrayList<>();
		try (ServerSocket server = new ServerSocket(0, 2,
				InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
			Thread responder = new Thread(() -> {
				try {
					for (int i = 0; i < 2; i++) try (var socket = server.accept()) {
						BufferedReader input = new BufferedReader(new InputStreamReader(
								socket.getInputStream(), StandardCharsets.US_ASCII));
						for (String line; (line = input.readLine()) != null && !line.isEmpty(); ) {
							if (line.regionMatches(true, 0, "Range:", 0, 6)) ranges.add(line);
						}
						socket.getOutputStream().write(("HTTP/1.1 206 Partial Content\r\n" +
								"Content-Range: bytes " + i + '-' + i + "/10\r\n" +
								"Content-Length: 1\r\nConnection: close\r\n\r\nx")
								.getBytes(StandardCharsets.US_ASCII));
					}
				} catch (Exception error) {
					throw new RuntimeException(error);
				}
			});
			responder.start();
			new TorrentLoopbackProbe().verify(URI.create(
					"http://127.0.0.1:" + server.getLocalPort() + "/torrent/token/key"), 10L);
			responder.join(2_000L);
			assertEquals(List.of("Range: bytes=0-0", "Range: bytes=9-9"), ranges);
		}
	}

	@Test
	public void rejectsNonLoopbackEndpoint() {
		assertThrows(java.io.IOException.class, () -> new TorrentLoopbackProbe().verify(
				URI.create("http://example.com/video"), 10L));
	}
}
