package me.aap.fermata.addon.tv.stalker;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

final class StalkerHttpClient {
	private static final String JS_REQUEST = "1-xml";
	private static final int MAX_REDIRECTS = 5;
	private static final Set<String> SENSITIVE_HEADERS = Set.of(
			"authorization", "cookie", "origin", "referer");
	private final StalkerAccount account;
	private final StalkerJsonParser parser = new StalkerJsonParser();
	private final StalkerErrorMapper errors;
	private final Object sessionLock = new Object();
	private final Object connectionLock = new Object();
	private String endpoint;
	private String token;
	private HttpURLConnection activeConnection;
	private volatile int lastStatusCode;

	StalkerHttpClient(StalkerAccount account, android.content.Context context) {
		this.account = account;
		errors = new StalkerErrorMapper(account, context);
	}

	FutureSupplier<Void> validate() {
		return task(() -> {
			ensureSession();
			request("itv", "get_genres", Map.of(), parser::parseCategories);
			return null;
		});
	}

	FutureSupplier<Void> authenticate() {
		return task(() -> {
			ensureSession();
			return null;
		});
	}

	FutureSupplier<List<StalkerCategory>> getCategories() {
		return task(() -> {
			ensureSession();
			return request("itv", "get_genres", Map.of(), parser::parseCategories);
		});
	}

	FutureSupplier<List<StalkerChannel>> getChannels() {
		return task(() -> {
			ensureSession();
			return request("itv", "get_all_channels", Map.of(), parser::parseChannels);
		});
	}

	FutureSupplier<StalkerPlaybackLink> createLink(String command) {
		return task(() -> {
			ensureSession();
			Map<String, String> params = new LinkedHashMap<>();
			params.put("cmd", command);
			params.put("series", "");
			params.put("forced_storage", "");
			params.put("disable_ad", "0");
			params.put("download", "0");
			return request("itv", "create_link", params,
					input -> parser.parseLink(input, playbackHeaders()));
		});
	}

	FutureSupplier<StalkerProbeResult> probe(StalkerPlaybackLink link) {
		return task(() -> executeProbe(link));
	}

	int getLastStatusCode() {
		return lastStatusCode;
	}

	void resetSession() {
		synchronized (sessionLock) {
			token = null;
			endpoint = null;
		}
	}

	private <T> FutureSupplier<T> task(Task<T> task) {
		FutureSupplier<T> future = App.get().getExecutor().submitTask(() -> {
			synchronized (sessionLock) {
				try {
					return task.run();
				} catch (Throwable error) {
					throw errors.map(error);
				}
			}
		});
		future.onCancel(this::cancelActiveRequest);
		return future;
	}

	private void ensureSession() throws IOException {
		if ((endpoint != null) && (token != null)) return;
		IOException last = null;
		for (String candidate : account.getEndpointCandidates()) {
			try {
				String newToken = handshake(candidate);
				endpoint = candidate;
				token = newToken;
				loadProfile();
				return;
			} catch (IOException ex) {
				last = ex;
				endpoint = null;
				token = null;
			}
		}
		if (last != null) throw last;
		throw new IOException("Stalker portal URL is invalid");
	}

	private String handshake(String candidate) throws IOException {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("type", "stb");
		params.put("action", "handshake");
		params.put("prehash", "0");
		params.put("token", "");
		params.put("JsHttpRequest", JS_REQUEST);
		return execute(candidate, params, null, parser::parseToken);
	}

	private void loadProfile() throws IOException {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("hd", "1");
		params.put("ver", "ImageDescription: 0.2.18-r23-254; ImageDate: 18 Mar 2021; " +
				"PORTAL version: 5.6.6; API Version: JS API version: 343; STB API version: 146");
		params.put("num_banks", "2");
		params.put("stb_type", "MAG254");
		params.put("client_type", "STB");
		params.put("image_version", "218");
		params.put("video_out", "hdmi");
		params.put("auth_second_step", "1");
		params.put("hw_version", "2.6-IB-00");
		params.put("not_valid_token", "0");
		params.put("api_signature", "262");
		if (account.getSerial() != null) params.put("sn", account.getSerial());
		if (account.getDeviceId() != null) {
			params.put("device_id", account.getDeviceId());
			params.put("device_id2", account.getDeviceId());
		}
		params.put("metrics", profileMetrics());
		request("stb", "get_profile", params, input -> {
			parser.requireProfile(input);
			return null;
		});
	}

	private String profileMetrics() {
		return "{\"mac\":\"" + json(account.getMac()) + "\",\"sn\":\"" +
				json(account.getSerial()) + "\",\"model\":\"MAG254\",\"type\":\"STB\"," +
				"\"uid\":\"" + json(account.getDeviceId()) + "\"}";
	}

	private static String json(@Nullable String value) {
		if (value == null) return "";
		StringBuilder escaped = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c == '\\') || (c == '"')) escaped.append('\\');
			if (c >= 0x20) escaped.append(c);
		}
		return escaped.toString();
	}

	private <T> T request(String type, String action, Map<String, String> extra,
			ResponseParser<T> responseParser) throws IOException {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("type", type);
		params.put("action", action);
		params.putAll(extra);
		params.put("JsHttpRequest", JS_REQUEST);
		try {
			return execute(endpoint, params, token, responseParser);
		} catch (StalkerErrorMapper.HttpStatusException status) {
			if ((status.status != HttpURLConnection.HTTP_UNAUTHORIZED) &&
					(status.status != HttpURLConnection.HTTP_FORBIDDEN)) throw status;
			token = null;
			String currentEndpoint = endpoint;
			String newToken = handshake(currentEndpoint);
			endpoint = currentEndpoint;
			token = newToken;
			if (!("stb".equals(type) && "get_profile".equals(action))) loadProfile();
			return execute(endpoint, params, token, responseParser);
		} catch (IOException failure) {
			String message = failure.getMessage();
			if ((message == null) || !message.toLowerCase(Locale.ROOT).contains("token")) {
				throw failure;
			}
			String currentEndpoint = endpoint;
			token = handshake(currentEndpoint);
			if (!("stb".equals(type) && "get_profile".equals(action))) loadProfile();
			return execute(currentEndpoint, params, token, responseParser);
		}
	}

	private <T> T execute(String endpoint, Map<String, String> params, @Nullable String token,
			ResponseParser<T> responseParser) throws IOException {
		Uri.Builder builder = Uri.parse(endpoint).buildUpon();
		for (Map.Entry<String, String> param : params.entrySet()) {
			builder.appendQueryParameter(param.getKey(), param.getValue());
		}
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
		headers.put("Accept-Encoding", "gzip, deflate");
		headers.put("User-Agent", account.getUserAgent());
		headers.put("X-User-Agent", "Model: MAG254; Link: Ethernet");
		headers.put("Cookie", account.cookie());
		headers.put("Referer", account.getPortalReferer());
		if (token != null) headers.put("Authorization", "Bearer " + token);

		HttpURLConnection connection = open(URI.create(builder.build().toString()), headers);
		try {
			int status = lastStatusCode;
			if (status != HttpURLConnection.HTTP_OK) {
				close(connection.getErrorStream());
				throw new StalkerErrorMapper.HttpStatusException(status,
						connection.getResponseMessage());
			}
			InputStream payload = connection.getInputStream();
			if (payload == null) throw new IOException("Stalker portal returned an empty response");
			try (InputStream input = decode(payload, connection.getContentEncoding())) {
				return responseParser.parse(input);
			}
		} finally {
			disconnect(connection);
		}
	}

	private StalkerProbeResult executeProbe(StalkerPlaybackLink link) throws IOException {
		Map<String, String> headers = new LinkedHashMap<>(link.headers());
		headers.putIfAbsent("User-Agent", account.getUserAgent());
		headers.put("Accept", "*/*");
		headers.put("Range", "bytes=0-0");
		HttpURLConnection connection = open(link.uri(), headers);
		try {
			int status = lastStatusCode;
			if ((status != HttpURLConnection.HTTP_OK) &&
					(status != HttpURLConnection.HTTP_PARTIAL)) {
				close(connection.getErrorStream());
				throw new StalkerErrorMapper.HttpStatusException(status,
						connection.getResponseMessage());
			}
			InputStream payload = connection.getInputStream();
			if (payload == null) throw new IOException("Stalker stream returned an empty response");
			try (InputStream input = payload) {
				input.read();
			}
			return new StalkerProbeResult(status);
		} finally {
			disconnect(connection);
		}
	}

	private HttpURLConnection open(URI initial, Map<String, String> headers)
			throws IOException {
		URI current = initial;
		boolean includeSensitive = true;
		for (int redirects = 0; ; redirects++) {
			validateHttpUri(current);
			HttpURLConnection connection = (HttpURLConnection) new URL(current.toString())
					.openConnection();
			track(connection);
			try {
				connection.setRequestMethod("GET");
				connection.setInstanceFollowRedirects(false);
				for (Map.Entry<String, String> header : headers.entrySet()) {
					if (includeSensitive || !isSensitive(header.getKey())) {
						connection.setRequestProperty(header.getKey(), header.getValue());
					}
				}
				applyTimeout(connection);
				int status = connection.getResponseCode();
				lastStatusCode = status;
				if (!isRedirect(status)) return connection;
				if (redirects >= MAX_REDIRECTS) {
					throw new IOException("Stalker request exceeded the redirect limit");
				}
				String location = connection.getHeaderField("Location");
				if ((location == null) || location.isBlank()) {
					throw new IOException("Stalker redirect did not include a destination");
				}
				URI next;
				try {
					next = current.resolve(location);
				} catch (IllegalArgumentException ex) {
					throw new IOException("Stalker redirect destination is invalid", ex);
				}
				validateHttpUri(next);
				includeSensitive &= sameOrigin(current, next);
				close(connection.getErrorStream());
				close(connection.getInputStream());
				disconnect(connection);
				current = next;
			} catch (IOException | RuntimeException ex) {
				disconnect(connection);
				throw ex;
			}
		}
	}

	private void applyTimeout(HttpURLConnection connection) {
		int timeout = account.getResponseTimeout();
		if (timeout <= 0) return;
		int millis = (int) Math.min(Integer.MAX_VALUE, timeout * 1000L);
		connection.setConnectTimeout(millis);
		connection.setReadTimeout(millis);
	}

	private void track(HttpURLConnection connection) {
		synchronized (connectionLock) {
			activeConnection = connection;
		}
	}

	private void disconnect(HttpURLConnection connection) {
		synchronized (connectionLock) {
			if (activeConnection == connection) activeConnection = null;
		}
		connection.disconnect();
	}

	private void cancelActiveRequest() {
		HttpURLConnection connection;
		synchronized (connectionLock) {
			connection = activeConnection;
			activeConnection = null;
		}
		if (connection != null) connection.disconnect();
	}

	private static boolean isRedirect(int status) {
		return (status == HttpURLConnection.HTTP_MOVED_PERM) ||
				(status == HttpURLConnection.HTTP_MOVED_TEMP) ||
				(status == HttpURLConnection.HTTP_SEE_OTHER) || (status == 307) || (status == 308);
	}

	private static boolean isSensitive(String name) {
		return SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT));
	}

	private static boolean sameOrigin(URI first, URI second) {
		return first.getScheme().equalsIgnoreCase(second.getScheme()) &&
				first.getHost().equalsIgnoreCase(second.getHost()) &&
				(effectivePort(first) == effectivePort(second));
	}

	private static int effectivePort(URI uri) {
		if (uri.getPort() >= 0) return uri.getPort();
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static void validateHttpUri(URI uri) throws IOException {
		String scheme = uri.getScheme();
		if ((uri.getHost() == null) || (uri.getRawUserInfo() != null) ||
				(!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
			throw new IOException("Stalker request destination is invalid");
		}
	}

	private Map<String, String> playbackHeaders() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", account.getUserAgent());
		headers.put("Cookie", account.cookie());
		headers.put("Referer", account.getPortalReferer());
		return headers;
	}

	private static InputStream decode(InputStream input, @Nullable String encoding)
			throws IOException {
		if (encoding == null) return input;
		String normalized = encoding.toLowerCase(Locale.ROOT);
		if (normalized.contains("gzip")) return new GZIPInputStream(input);
		if (normalized.contains("deflate")) return new InflaterInputStream(input);
		throw new IOException("Unsupported Stalker content encoding: " + encoding);
	}

	private static void close(@Nullable InputStream input) throws IOException {
		if (input != null) input.close();
	}

	@FunctionalInterface
	private interface Task<T> {
		T run() throws Exception;
	}

	@FunctionalInterface
	private interface ResponseParser<T> {
		T parse(InputStream input) throws IOException;
	}
}
