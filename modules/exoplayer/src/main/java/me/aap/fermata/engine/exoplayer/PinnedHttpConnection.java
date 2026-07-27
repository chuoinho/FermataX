package me.aap.fermata.engine.exoplayer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import me.aap.fermata.media.net.ValidatedPlaybackEndpoint;

/** Small HTTP/1.1 transport used only when a playback profile requires DNS pinning. */
final class PinnedHttpConnection implements AutoCloseable {
	private static final int MAX_HEADER_BYTES = 64 * 1024;
	private final Socket socket;
	private final int status;
	private final Map<String, List<String>> headers;
	private final InputStream body;

	private PinnedHttpConnection(Socket socket, int status,
			Map<String, List<String>> headers, InputStream body) {
		this.socket = socket;
		this.status = status;
		this.headers = headers;
		this.body = body;
	}

	static PinnedHttpConnection open(ValidatedPlaybackEndpoint endpoint, String method,
			Map<String, String> requestHeaders, int connectTimeoutMillis,
			int readTimeoutMillis) throws IOException {
		URI uri = endpoint.uri();
		int port = uri.getPort();
		if (port == -1) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
		Socket raw = new Socket();
		try {
			raw.connect(new InetSocketAddress(endpoint.pinnedAddress(), port),
					connectTimeoutMillis);
			raw.setSoTimeout(readTimeoutMillis);
			Socket connected = raw;
			if ("https".equalsIgnoreCase(uri.getScheme())) {
				SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
						.createSocket(raw, uri.getHost(), port, true);
				SSLParameters parameters = ssl.getSSLParameters();
				parameters.setEndpointIdentificationAlgorithm("HTTPS");
				ssl.setSSLParameters(parameters);
				ssl.startHandshake();
				connected = ssl;
			}
			writeRequest(connected.getOutputStream(), uri, method, requestHeaders, port);
			BufferedInputStream input = new BufferedInputStream(connected.getInputStream());
			String statusLine = readLine(input);
			int status = parseStatus(statusLine);
			Map<String, List<String>> headers = readHeaders(input);
			InputStream body = responseBody(input, method, status, headers);
			return new PinnedHttpConnection(connected, status, headers, body);
		} catch (Throwable error) {
			try {
				raw.close();
			} catch (IOException ignored) {
			}
			if (error instanceof IOException io) throw io;
			throw new IOException("Pinned playback connection failed", error);
		}
	}

	int status() {
		return status;
	}

	String header(String name) {
		List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
		return ((values == null) || values.isEmpty()) ? null : values.get(0);
	}

	Map<String, List<String>> headers() {
		return headers;
	}

	InputStream body() {
		return body;
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}

	private static void writeRequest(OutputStream output, URI uri, String method,
			Map<String, String> headers, int port) throws IOException {
		String path = uri.getRawPath();
		if ((path == null) || path.isEmpty()) path = "/";
		if (uri.getRawQuery() != null) path += '?' + uri.getRawQuery();
		String host = uri.getHost();
		boolean defaultPort = (port == 80) && "http".equalsIgnoreCase(uri.getScheme()) ||
				(port == 443) && "https".equalsIgnoreCase(uri.getScheme());
		StringBuilder request = new StringBuilder(512)
				.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
				.append("Host: ").append(host);
		if (!defaultPort) request.append(':').append(port);
		request.append("\r\nConnection: close\r\nAccept-Encoding: identity\r\n");
		for (Map.Entry<String, String> header : headers.entrySet()) {
			String name = header.getKey();
			String value = header.getValue();
			if (!safeHeader(name, value)) throw new IOException("Invalid playback request header");
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.equals("host") || lower.equals("connection") ||
					lower.equals("content-length") || lower.equals("accept-encoding")) continue;
			request.append(name).append(": ").append(value).append("\r\n");
		}
		request.append("\r\n");
		output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
		output.flush();
	}

	private static boolean safeHeader(String name, String value) {
		return (name != null) && !name.isEmpty() && (value != null) &&
				(name.indexOf('\r') < 0) && (name.indexOf('\n') < 0) &&
				(value.indexOf('\r') < 0) && (value.indexOf('\n') < 0);
	}

	private static int parseStatus(String line) throws IOException {
		if ((line == null) || !line.startsWith("HTTP/")) throw new IOException("Invalid HTTP status");
		int first = line.indexOf(' ');
		int second = (first < 0) ? -1 : line.indexOf(' ', first + 1);
		String value = (second < 0) ? line.substring(first + 1) : line.substring(first + 1, second);
		try {
			return Integer.parseInt(value);
		} catch (RuntimeException error) {
			throw new IOException("Invalid HTTP status", error);
		}
	}

	private static Map<String, List<String>> readHeaders(InputStream input) throws IOException {
		LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
		int total = 0;
		for (String line; !(line = readLine(input)).isEmpty(); ) {
			total += line.length() + 2;
			if (total > MAX_HEADER_BYTES) throw new IOException("Playback headers are too large");
			int colon = line.indexOf(':');
			if (colon <= 0) throw new IOException("Invalid HTTP header");
			String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			String value = line.substring(colon + 1).trim();
			headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
		}
		headers.replaceAll((name, values) -> Collections.unmodifiableList(values));
		return Collections.unmodifiableMap(headers);
	}

	private static InputStream responseBody(InputStream input, String method, int status,
			Map<String, List<String>> headers) throws IOException {
		if ("HEAD".equals(method) || (status == 204) || (status == 304)) {
			return new ByteArrayInputStream(new byte[0]);
		}
		String transfer = first(headers, "transfer-encoding");
		if ((transfer != null) && transfer.toLowerCase(Locale.ROOT).contains("chunked")) {
			return new ChunkedInputStream(input);
		}
		String length = first(headers, "content-length");
		if (length == null) return input;
		try {
			return new LimitedInputStream(input, Long.parseLong(length));
		} catch (NumberFormatException error) {
			throw new IOException("Invalid Content-Length", error);
		}
	}

	private static String first(Map<String, List<String>> headers, String name) {
		List<String> values = headers.get(name);
		return ((values == null) || values.isEmpty()) ? null : values.get(0);
	}

	private static String readLine(InputStream input) throws IOException {
		StringBuilder line = new StringBuilder();
		for (;;) {
			int value = input.read();
			if (value == -1) throw new EOFException("Unexpected end of HTTP headers");
			if (value == '\n') return line.toString();
			if (value != '\r') line.append((char) value);
			if (line.length() > MAX_HEADER_BYTES) throw new IOException("HTTP header line is too large");
		}
	}

	private static final class LimitedInputStream extends FilterInputStream {
		private long remaining;

		private LimitedInputStream(InputStream input, long remaining) {
			super(input);
			this.remaining = Math.max(remaining, 0);
		}

		@Override
		public int read() throws IOException {
			if (remaining == 0) return -1;
			int value = super.read();
			if (value != -1) remaining--;
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (remaining == 0) return -1;
			int read = super.read(buffer, offset, (int) Math.min(length, remaining));
			if (read > 0) remaining -= read;
			return read;
		}
	}

	private static final class ChunkedInputStream extends InputStream {
		private final InputStream input;
		private long remaining;
		private boolean finished;

		private ChunkedInputStream(InputStream input) {
			this.input = input;
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			return (read(one, 0, 1) == -1) ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (finished) return -1;
			if (remaining == 0) nextChunk();
			if (finished) return -1;
			int read = input.read(buffer, offset, (int) Math.min(length, remaining));
			if (read == -1) throw new EOFException("Unexpected end of chunked response");
			remaining -= read;
			if (remaining == 0) consumeCrlf();
			return read;
		}

		private void nextChunk() throws IOException {
			String line = readLine(input);
			int extension = line.indexOf(';');
			if (extension >= 0) line = line.substring(0, extension);
			try {
				remaining = Long.parseLong(line.trim(), 16);
			} catch (NumberFormatException error) {
				throw new IOException("Invalid chunk size", error);
			}
			if (remaining == 0) {
				while (!readLine(input).isEmpty()) {
				}
				finished = true;
			}
		}

		private void consumeCrlf() throws IOException {
			int first = input.read();
			int second = input.read();
			if ((first != '\r') || (second != '\n')) throw new IOException("Invalid chunk delimiter");
		}
	}
}
