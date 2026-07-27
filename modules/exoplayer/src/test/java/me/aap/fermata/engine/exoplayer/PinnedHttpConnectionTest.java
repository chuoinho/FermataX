package me.aap.fermata.engine.exoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import me.aap.fermata.media.net.ValidatedPlaybackEndpoint;

public class PinnedHttpConnectionTest {
	@Test
	public void connectsToPinnedAddressWhilePreservingHttpHost() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			int port = server.getLocalPort();
			CompletableFuture<String> request = CompletableFuture.supplyAsync(() -> {
				try (var socket = server.accept()) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(
							socket.getInputStream(), StandardCharsets.ISO_8859_1));
					StringBuilder headers = new StringBuilder();
					for (String line; (line = reader.readLine()) != null && !line.isEmpty(); ) {
						headers.append(line).append('\n');
					}
					socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n" +
							"Content-Length: 4\r\nConnection: close\r\n\r\ntest")
							.getBytes(StandardCharsets.ISO_8859_1));
					return headers.toString();
				} catch (Exception error) {
					throw new RuntimeException(error);
				}
			});
			URI uri = URI.create("http://media.example:" + port + "/video.ts");
			try (PinnedHttpConnection connection = PinnedHttpConnection.open(
					new ValidatedPlaybackEndpoint(uri, InetAddress.getLoopbackAddress()),
					"GET", Map.of("Range", "bytes=10-20"), 2_000, 2_000)) {
				assertEquals(200, connection.status());
				assertEquals("test", new String(connection.body().readAllBytes(),
						StandardCharsets.ISO_8859_1));
			}
			String sent = request.get(2, TimeUnit.SECONDS);
			assertTrue(sent.contains("Host: media.example:" + port));
			assertTrue(sent.contains("Range: bytes=10-20"));
		}
	}
}
