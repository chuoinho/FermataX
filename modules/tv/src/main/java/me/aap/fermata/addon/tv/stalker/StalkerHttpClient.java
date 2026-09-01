package me.aap.fermata.addon.tv.stalker;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

final class StalkerHttpClient {
	private static final String JS_REQUEST = "1-xml";
	private final StalkerAccount account;
	private final StalkerJsonParser parser = new StalkerJsonParser();
	private final StalkerErrorMapper errors;
	private final Object sessionLock = new Object();
	private String endpoint;
	private String token;

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

	void resetSession() {
		synchronized (sessionLock) {
			token = null;
			endpoint = null;
		}
	}

	private <T> FutureSupplier<T> task(Task<T> task) {
		return App.get().getExecutor().submitTask(() -> {
			synchronized (sessionLock) {
				try {
					return task.run();
				} catch (Throwable error) {
					throw errors.map(error);
				}
			}
		});
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
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(builder.build().toString()).openConnection();
			connection.setRequestMethod("GET");
			connection.setInstanceFollowRedirects(true);
			connection.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
			connection.setRequestProperty("User-Agent", account.getUserAgent());
			connection.setRequestProperty("X-User-Agent", "Model: MAG254; Link: Ethernet");
			connection.setRequestProperty("Cookie", account.cookie());
			connection.setRequestProperty("Referer", account.getPortalReferer());
			if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
			int timeout = account.getResponseTimeout();
			if (timeout > 0) {
				int millis = (int) Math.min(Integer.MAX_VALUE, timeout * 1000L);
				connection.setConnectTimeout(millis);
				connection.setReadTimeout(millis);
			}
			int status = connection.getResponseCode();
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
			if (connection != null) connection.disconnect();
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
