package me.aap.fermata.addon.stremio.torrent;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Verifies that the private bridge serves both head and tail byte ranges before handoff. */
final class TorrentLoopbackProbe {
	private static final int TIMEOUT_MILLIS = 3_000;

	void verify(URI endpoint, long size) throws IOException {
		if ((size <= 0L) || !"127.0.0.1".equals(endpoint.getHost()) ||
				(endpoint.getPort() <= 0)) throw new IOException("Invalid P2P loopback endpoint");
		verifyRange(endpoint, 0L);
		if (size > 1L) verifyRange(endpoint, size - 1L);
	}

	private void verifyRange(URI endpoint, long offset) throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()),
					TIMEOUT_MILLIS);
			socket.setSoTimeout(TIMEOUT_MILLIS);
			OutputStream output = socket.getOutputStream();
			String path = endpoint.getRawPath();
			if ((endpoint.getRawQuery() != null) && !endpoint.getRawQuery().isEmpty()) {
				path += '?' + endpoint.getRawQuery();
			}
			output.write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=" +
					offset + '-' + offset + "\r\nConnection: close\r\n\r\n")
					.getBytes(StandardCharsets.US_ASCII));
			output.flush();
			BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
			BufferedReader headers = new BufferedReader(new InputStreamReader(
					input, StandardCharsets.US_ASCII));
			String status = headers.readLine();
			if ((status == null) || !status.contains(" 206 ")) {
				throw new IOException("P2P loopback range probe failed");
			}
			boolean contentRange = false;
			for (String line; (line = headers.readLine()) != null && !line.isEmpty(); ) {
				if (line.regionMatches(true, 0, "Content-Range:", 0, 14)) contentRange = true;
			}
			if (!contentRange || (headers.read() == -1)) {
				throw new IOException("P2P loopback returned an incomplete range");
			}
		}
	}
}
