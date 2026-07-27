package me.aap.fermata.addon.stremio.torrent;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;

import me.aap.fermata.media.net.RemotePlaybackProgress;

/** Loopback-only HTTP Range bridge for one bounded set of torrent files. */
final class TorrentHttpServer implements AutoCloseable {
	private static final String TAG = "StremioTorrentHttp";
	private static final InetAddress IPV4_LOOPBACK = ipv4Loopback();
	private static final int MAX_ENTRIES = 8;
	private static final int MAX_HEADER_CHARS = 16 * 1024;

	private final String token = randomToken();
	private final Map<String, TorrentStreamLease> entries =
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<String, TorrentStreamLease> eldest) {
					if (size() <= MAX_ENTRIES) return false;
					eldest.getValue().cancel();
					return true;
				}
			};
	private final ExecutorService clients = Executors.newCachedThreadPool(threads());
	private final ScheduledExecutorService lifecycle =
			Executors.newSingleThreadScheduledExecutor(threads());
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicBoolean closed = new AtomicBoolean();
	private ServerSocket server;
	private Thread acceptThread;

	synchronized String register(String key, File file, long size, TorrentHandle handle,
			TorrentInfo info, int fileIndex, TorrentAlertRouter alerts,
			Consumer<RemotePlaybackProgress> progress) throws IOException {
		start();
		TorrentStreamLease previous = entries.remove(key);
		if (previous != null) previous.cancel();
		TorrentStreamLease entry = new TorrentStreamLease(file, size, handle, info, fileIndex,
				lifecycle, closed, alerts);
		entry.observe(progress);
		entries.put(key, entry);
		return url(key);
	}

	synchronized void observe(String key, Consumer<RemotePlaybackProgress> progress) {
		TorrentStreamLease entry = entries.get(key);
		if (entry != null) entry.observe(progress);
	}

	synchronized void cancel(String key) {
		TorrentStreamLease entry = entries.remove(key);
		if (entry != null) entry.cancel();
	}

	private String url(String key) {
		return "http://127.0.0.1:" + server.getLocalPort() + "/torrent/" + token + '/' + key;
	}

	synchronized void start() throws IOException {
		if (closed.get()) throw new IOException("Torrent HTTP server is closed");
		if (running.get()) return;
		// The playback URL is deliberately IPv4. InetAddress.getLoopbackAddress() may
		// return ::1 on Android, which makes 127.0.0.1 fail with Connection refused.
		server = new ServerSocket(0, 16, IPV4_LOOPBACK);
		server.setReuseAddress(false);
		running.set(true);
		acceptThread = new Thread(this::acceptLoop, "FermataX-Stremio-Torrent-Accept");
		acceptThread.setDaemon(true);
		acceptThread.start();
	}

	synchronized URI endpoint() throws IOException {
		start();
		return URI.create("http://127.0.0.1:" + server.getLocalPort());
	}

	static InetAddress bindAddress() {
		return IPV4_LOOPBACK;
	}

	private static InetAddress ipv4Loopback() {
		try {
			return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
		} catch (UnknownHostException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private void acceptLoop() {
		while (running.get()) {
			try {
				Socket socket = server.accept();
				socket.setSoTimeout(15_000);
				clients.execute(() -> handle(socket));
			} catch (IOException error) {
				if (running.get()) Log.w(TAG, "Accept failed", error);
			}
		}
	}

	private void handle(Socket socket) {
		try (socket;
			 BufferedReader reader = new BufferedReader(new InputStreamReader(
					 new BufferedInputStream(socket.getInputStream()), StandardCharsets.US_ASCII))) {
			String requestLine = reader.readLine();
			if (requestLine == null) return;
			String[] request = requestLine.split(" ", 3);
			if (request.length != 3 || (!"GET".equals(request[0]) && !"HEAD".equals(request[0]))) {
				writeError(socket, 405, "Method Not Allowed");
				return;
			}
			String range = null;
			int headerChars = requestLine.length();
			for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
				headerChars += line.length();
				if (headerChars > MAX_HEADER_CHARS) {
					writeError(socket, 431, "Request Header Fields Too Large");
					return;
				}
				int colon = line.indexOf(':');
				if (colon > 0 && "range".equalsIgnoreCase(line.substring(0, colon).trim())) {
					range = line.substring(colon + 1).trim();
				}
			}
			String prefix = "/torrent/" + token + '/';
			String path = request[1];
			int query = path.indexOf('?');
			if (query >= 0) path = path.substring(0, query);
			if (!path.startsWith(prefix)) {
				writeError(socket, 404, "Not Found");
				return;
			}
			TorrentStreamLease entry;
			synchronized (this) {
				entry = entries.get(path.substring(prefix.length()));
			}
			if (entry == null) {
				writeError(socket, 404, "Stream Expired");
				return;
			}
			ByteRange bytes;
			try {
				bytes = ByteRange.parse(range, entry.size());
			} catch (IllegalArgumentException invalid) {
				writeRangeError(socket, entry.size());
				return;
			}
			if ("GET".equals(request[0])) {
				try (TorrentStreamLease.ReadSession session = entry.openSession()) {
					try {
						// Do not let the player enter Playing after one 64 KiB piece. VLC probes and
						// then consumes loopback HTTP much faster than a torrent can refill it; a
						// bounded lead window keeps the first response continuous while still letting
						// Range requests drive piece priority.
						session.await(bytes.start, entry.initialBufferLength(bytes));
					} catch (SocketTimeoutException unavailable) {
						int status = entry.unavailableStatus();
						writeError(socket, status,
								(status == 503) ? "P2P Peers Unavailable" : "P2P Data Unavailable");
						return;
					} catch (IOException unavailable) {
						Log.w(TAG, "P2P bridge unavailable: trackers=" + entry.trackerCount());
						writeError(socket, 502, "P2P Engine Unavailable");
						return;
					}
					writeHeaders(socket.getOutputStream(), entry, bytes);
					stream(session, entry, socket.getOutputStream(), bytes);
				} catch (SocketTimeoutException unavailable) {
					Log.w(TAG, "P2P stream stalled after response started");
				} catch (SocketException abandoned) {
					// Players routinely close successful probe/range requests once they have
					// enough container data. This is not a transport failure.
				} catch (IOException unavailable) {
					Log.w(TAG, "P2P stream ended after response started: " +
							failureCode(unavailable));
				}
				return;
			}
			writeHeaders(socket.getOutputStream(), entry, bytes);
		} catch (SocketTimeoutException ignored) {
			// A stalled or abandoned player connection owns no runtime state.
		} catch (SocketException ignored) {
			// Players routinely close probe/range connections before the response is complete.
		} catch (Exception error) {
			if (running.get()) Log.w(TAG, "Client failed", error);
		}
	}

	private static void writeHeaders(OutputStream output, TorrentStreamLease entry, ByteRange range)
			throws IOException {
		String status = range.partial ? "206 Partial Content" : "200 OK";
		StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
				.append("Content-Type: ").append(mime(entry.file().getName())).append("\r\n")
				.append("Accept-Ranges: bytes\r\n")
				.append("Content-Length: ").append(range.length()).append("\r\n");
		if (range.partial) headers.append("Content-Range: bytes ").append(range.start)
				.append('-').append(range.end).append('/').append(entry.size()).append("\r\n");
		headers.append("Cache-Control: no-store\r\nConnection: close\r\n\r\n");
		output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
		output.flush();
	}

	private static void stream(TorrentStreamLease.ReadSession session, TorrentStreamLease entry,
			OutputStream output, ByteRange range) throws IOException, InterruptedException {
		byte[] buffer = new byte[64 * 1024];
		long offset = range.start;
		long remaining = range.length();
		long firstOffset = offset;
		int firstAmount = (int) Math.min(buffer.length, remaining);
		try (RandomAccessFile file = openAfterAwait(entry.file(),
				() -> session.await(firstOffset, firstAmount))) {
			while (remaining > 0) {
				int amount = (int) Math.min(buffer.length, remaining);
				session.await(offset, amount);
				file.seek(offset);
				int read = file.read(buffer, 0, amount);
				if (read <= 0) throw new IOException("Torrent piece is unavailable");
				output.write(buffer, 0, read);
				offset += read;
				remaining -= read;
			}
		}
		output.flush();
	}

	static RandomAccessFile openAfterAwait(File file, InterruptibleAwait await)
			throws IOException, InterruptedException {
		await.run();
		return new RandomAccessFile(file, "r");
	}

	private static String mime(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
		if (lower.endsWith(".mkv")) return "video/x-matroska";
		if (lower.endsWith(".webm")) return "video/webm";
		if (lower.endsWith(".avi")) return "video/x-msvideo";
		if (lower.endsWith(".ts") || lower.endsWith(".m2ts")) return "video/mp2t";
		return "application/octet-stream";
	}

	private static String failureCode(Throwable error) {
		String type = error.getClass().getSimpleName();
		String message = error.getMessage();
		return ((message == null) || message.isBlank()) ? type : type + ": " + message;
	}

	private static void writeError(Socket socket, int code, String message) throws IOException {
		byte[] body = message.getBytes(StandardCharsets.UTF_8);
		OutputStream output = socket.getOutputStream();
		output.write(("HTTP/1.1 " + code + ' ' + message + "\r\nContent-Type: text/plain\r\n" +
				"Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
				.getBytes(StandardCharsets.US_ASCII));
		output.write(body);
		output.flush();
	}

	private static void writeRangeError(Socket socket, long size) throws IOException {
		OutputStream output = socket.getOutputStream();
		output.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + size +
				"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
				.getBytes(StandardCharsets.US_ASCII));
		output.flush();
	}

	@Override
	public synchronized void close() {
		if (!closed.compareAndSet(false, true)) return;
		running.set(false);
		for (TorrentStreamLease entry : entries.values()) entry.cancel();
		entries.clear();
		if (server != null) {
			try {
				server.close();
			} catch (IOException ignored) {
			}
		}
		clients.shutdownNow();
		lifecycle.shutdownNow();
		if (acceptThread != null) acceptThread.interrupt();
	}

	private static String randomToken() {
		byte[] bytes = new byte[16];
		new SecureRandom().nextBytes(bytes);
		StringBuilder value = new StringBuilder(32);
		for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02x", b & 0xff));
		return value.toString();
	}

	private static ThreadFactory threads() {
		return task -> {
			Thread thread = new Thread(task, "FermataX-Stremio-Torrent-HTTP");
			thread.setDaemon(true);
			return thread;
		};
	}

	@FunctionalInterface
	interface InterruptibleAwait {
		void run() throws IOException, InterruptedException;
	}

	static record ByteRange(long start, long end, boolean partial) {
		static ByteRange parse(String value, long size) {
			if (size <= 0) throw new IllegalArgumentException("Empty file");
			if (value == null || value.isBlank()) return new ByteRange(0, size - 1, false);
			if (!value.startsWith("bytes=") || value.indexOf(',') >= 0) {
				throw new IllegalArgumentException("Unsupported range");
			}
			String range = value.substring(6).trim();
			int dash = range.indexOf('-');
			if (dash < 0) throw new IllegalArgumentException("Invalid range");
			String first = range.substring(0, dash).trim();
			String last = range.substring(dash + 1).trim();
			long start;
			long end;
			if (first.isEmpty()) {
				long suffix = Long.parseLong(last);
				if (suffix <= 0) throw new IllegalArgumentException("Invalid suffix");
				start = Math.max(0, size - suffix);
				end = size - 1;
			} else {
				start = Long.parseLong(first);
				end = last.isEmpty() ? size - 1 : Math.min(Long.parseLong(last), size - 1);
			}
			if (start < 0 || start >= size || end < start) {
				throw new IllegalArgumentException("Unsatisfiable range");
			}
			return new ByteRange(start, end, true);
		}

		long length() { return end - start + 1; }
	}

	static int priorityWindowBytes(long downloadRate) {
		return TorrentStreamPolicy.priorityWindowBytes(downloadRate);
	}

	static int initialBufferLength(long rangeLength, long downloadRate) {
		return TorrentStreamPolicy.initialBufferLength(rangeLength, downloadRate);
	}

	static int bufferScore(int peers, long downloadRate, long downloaded, long target) {
		return TorrentStreamPolicy.bufferScore(peers, downloadRate, downloaded, target);
	}
}
