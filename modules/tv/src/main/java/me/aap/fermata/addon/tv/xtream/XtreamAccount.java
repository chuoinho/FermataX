package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class XtreamAccount {
	public static final String[] SCHEMES = {"http", "https"};
	public static final String[] OUTPUTS = {"ts", "m3u8"};
	public static final Pref<Supplier<String>> NAME = Pref.s("XTREAM_NAME");
	public static final Pref<IntSupplier> SCHEME = Pref.i("XTREAM_SCHEME", 0);
	public static final Pref<Supplier<String>> HOST = Pref.s("XTREAM_HOST");
	public static final Pref<IntSupplier> PORT = Pref.i("XTREAM_PORT", 0);
	public static final Pref<Supplier<String>> USERNAME = Pref.s("XTREAM_USERNAME");
	public static final Pref<Supplier<String>> PASSWORD = Pref.s("XTREAM_PASSWORD");
	public static final Pref<IntSupplier> OUTPUT = Pref.i("XTREAM_OUTPUT", 0);
	private final int sourceId;
	private final String name;
	private final int scheme;
	private final String host;
	private final int port;
	private final String username;
	private final String password;
	private final int output;
	private final String userAgent;
	private final int responseTimeout;

	public XtreamAccount(int sourceId, String name, int scheme, String host, int port,
											 String username, String password, int output, String userAgent,
											 int responseTimeout) {
		NormalizedInput input = normalize(scheme, host, port, username, password, output);
		this.sourceId = sourceId;
		this.name = trim(name);
		this.scheme = input.scheme;
		this.host = input.host;
		this.port = input.port;
		this.username = input.username;
		this.password = input.password;
		this.output = input.output;
		this.userAgent = trim(userAgent);
		this.responseTimeout = responseTimeout;
	}

	public static XtreamAccount fromPrefs(PreferenceStore ps, String userAgent, int responseTimeout) {
		return new XtreamAccount(0, ps.getStringPref(NAME), ps.getIntPref(SCHEME),
				ps.getStringPref(HOST), ps.getIntPref(PORT), ps.getStringPref(USERNAME),
				ps.getStringPref(PASSWORD), ps.getIntPref(OUTPUT), userAgent, responseTimeout);
	}

	@Nullable
	public static XtreamAccount load(PreferenceStore ps, int sourceId) {
		String host = ps.getStringPref(hostPref(sourceId));
		XtreamCredentials.Credentials credentials = XtreamCredentials.load(sourceId);
		String username = (credentials == null) ? ps.getStringPref(usernamePref(sourceId)) :
				credentials.username;
		String password = (credentials == null) ? ps.getStringPref(passwordPref(sourceId)) :
				credentials.password;
		if (isNullOrBlank(host) || isNullOrBlank(username) || isNullOrBlank(password)) return null;
		if (credentials == null) migrateLegacyCredentials(ps, sourceId, username, password);
		return new XtreamAccount(sourceId, ps.getStringPref(namePref(sourceId)),
				ps.getIntPref(schemePref(sourceId)), host, ps.getIntPref(portPref(sourceId)),
				username, password, ps.getIntPref(outputPref(sourceId)),
				ps.getStringPref(agentPref(sourceId)), ps.getIntPref(timeoutPref(sourceId)));
	}

	public static void save(PreferenceStore.Edit e, int sourceId, XtreamAccount account) {
		requireCredentialStorage();
		e.setStringPref(namePref(sourceId), account.getRawName());
		e.setIntPref(schemePref(sourceId), account.scheme);
		e.setStringPref(hostPref(sourceId), account.getHost());
		e.setIntPref(portPref(sourceId), account.getPort());
		XtreamCredentials.save(e, sourceId, account.getUsername(), account.getPassword());
		e.setIntPref(outputPref(sourceId), account.output);
		e.setStringPref(agentPref(sourceId), account.getUserAgent());
		e.setIntPref(timeoutPref(sourceId), account.getResponseTimeout());
	}

	public static void remove(PreferenceStore.Edit e, int sourceId) {
		e.removePref(namePref(sourceId));
		e.removePref(schemePref(sourceId));
		e.removePref(hostPref(sourceId));
		e.removePref(portPref(sourceId));
		XtreamCredentials.remove(e, sourceId);
		e.removePref(outputPref(sourceId));
		e.removePref(agentPref(sourceId));
		e.removePref(timeoutPref(sourceId));
	}

	public static void requireCredentialStorage() {
		XtreamCredentials.requireAvailable();
	}

	private static void migrateLegacyCredentials(PreferenceStore ps, int sourceId, String username,
																							 String password) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			XtreamCredentials.save(e, sourceId, username, password);
		} catch (RuntimeException ex) {
			Log.e(ex, "Failed to migrate Xtream credentials for source ", sourceId);
		}
	}

	public static Pref<Supplier<String>> namePref(int sourceId) {
		return Pref.s("XTREAM_NAME#" + sourceId);
	}

	public static Pref<IntSupplier> schemePref(int sourceId) {
		return Pref.i("XTREAM_SCHEME#" + sourceId, 0);
	}

	public static Pref<Supplier<String>> hostPref(int sourceId) {
		return Pref.s("XTREAM_HOST#" + sourceId);
	}

	public static Pref<IntSupplier> portPref(int sourceId) {
		return Pref.i("XTREAM_PORT#" + sourceId, 0);
	}

	public static Pref<Supplier<String>> usernamePref(int sourceId) {
		return Pref.s("XTREAM_USERNAME#" + sourceId);
	}

	public static Pref<Supplier<String>> passwordPref(int sourceId) {
		return Pref.s("XTREAM_PASSWORD#" + sourceId);
	}

	public static Pref<IntSupplier> outputPref(int sourceId) {
		return Pref.i("XTREAM_OUTPUT#" + sourceId, 0);
	}

	public static Pref<Supplier<String>> agentPref(int sourceId) {
		return Pref.s("XTREAM_AGENT#" + sourceId);
	}

	public static Pref<IntSupplier> timeoutPref(int sourceId) {
		return Pref.i("XTREAM_RESP_TIMEOUT#" + sourceId, 30);
	}

	public int getSourceId() {
		return sourceId;
	}

	@NonNull
	public XtreamAccount withSourceId(int sourceId) {
		return new XtreamAccount(sourceId, name, scheme, host, port, username, password, output,
				userAgent, responseTimeout);
	}

	public String getRawName() {
		return name;
	}

	public String getName() {
		if (!isNullOrBlank(name)) return name;
		return isNullOrBlank(host) ? "Xtream Codes" : host;
	}

	public int getSchemeIndex() {
		return scheme;
	}

	public String getScheme() {
		return SCHEMES[scheme];
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public int getOutputIndex() {
		return output;
	}

	public String getOutput() {
		return OUTPUTS[output];
	}

	@Nullable
	public String getUserAgent() {
		return isNullOrBlank(userAgent) ? null : userAgent;
	}

	public int getResponseTimeout() {
		return Math.max(0, responseTimeout);
	}

	public boolean isComplete() {
		return !isNullOrBlank(host) && !isNullOrBlank(username) && !isNullOrBlank(password) &&
				(port >= 0);
	}

	public String buildPlayerApiUrl(@Nullable String action, @Nullable String extraKey,
																	@Nullable String extraValue) {
		if (isNullOrBlank(extraKey) || (extraValue == null)) {
			return buildPlayerApiUrl(action, (Map<String, String>) null);
		}
		return buildPlayerApiUrl(action, Collections.singletonMap(extraKey, extraValue));
	}

	public String buildPlayerApiUrl(@Nullable String action,
																	@Nullable Map<String, String> extraParams) {
		Uri.Builder b = baseBuilder();
		b.appendEncodedPath("player_api.php");
		b.appendQueryParameter("username", username);
		b.appendQueryParameter("password", password);
		if (!isNullOrBlank(action)) b.appendQueryParameter("action", action);
		if (extraParams != null) {
			for (Map.Entry<String, String> e : extraParams.entrySet()) {
				if (!isNullOrBlank(e.getKey()) && (e.getValue() != null)) {
					b.appendQueryParameter(e.getKey(), e.getValue());
				}
			}
		}
		return b.build().toString();
	}

	public String buildLiveStreamUrl(int streamId) {
		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			b.append(getScheme()).append("://").append(authority()).append("/live/")
					.append(encodePath(username)).append('/').append(encodePath(password)).append('/')
					.append(streamId).append('.').append(getOutput());
			return b.toString();
		}
	}

	public String buildTimeshiftStreamUrl(int streamId, long startTime, long duration) {
		long safeDuration = duration;
		if ((safeDuration <= 0) || (safeDuration == Long.MAX_VALUE)) {
			safeDuration = Math.max(60000L, System.currentTimeMillis() - startTime);
		}
		long minutes = Math.max(1L, (safeDuration + 59999L) / 60000L);
		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			b.append(getScheme()).append("://").append(authority()).append("/timeshift/")
					.append(encodePath(username)).append('/').append(encodePath(password)).append('/')
					.append(minutes).append('/').append(formatTimeshiftStart(startTime)).append('/')
					.append(streamId).append('.').append(getOutput());
			return b.toString();
		}
	}

	public String buildMovieStreamUrl(int streamId, @Nullable String extension) {
		String ext = normalizeExtension(extension);

		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			b.append(getScheme()).append("://").append(authority()).append("/movie/")
					.append(encodePath(username)).append('/').append(encodePath(password)).append('/')
					.append(streamId);
			if (!isNullOrBlank(ext)) b.append('.').append(encodePath(ext));
			return b.toString();
		}
	}

	public String buildSeriesStreamUrl(int episodeId, @Nullable String extension) {
		String ext = normalizeExtension(extension);

		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			b.append(getScheme()).append("://").append(authority()).append("/series/")
					.append(encodePath(username)).append('/').append(encodePath(password)).append('/')
					.append(episodeId);
			if (!isNullOrBlank(ext)) b.append('.').append(encodePath(ext));
			return b.toString();
		}
	}

	public String redact(String value) {
		if (value == null) return null;
		return redactSecret(redactSecret(value, password), username);
	}

	private static String redactSecret(String value, String secret) {
		if (isNullOrBlank(secret)) return value;
		String redacted = value.replace(secret, "***");
		String encoded = Uri.encode(secret);
		return isNullOrBlank(encoded) ? redacted : redacted.replace(encoded, "***");
	}

	private Uri.Builder baseBuilder() {
		return new Uri.Builder().scheme(getScheme()).encodedAuthority(authority());
	}

	private String authority() {
		String h = host;
		if ((h.indexOf(':') >= 0) && !h.startsWith("[") && !h.endsWith("]")) h = '[' + h + ']';
		if (port <= 0) return h;
		return h + ':' + port;
	}

	private static int clamp(int idx, String[] values) {
		return (idx < 0) || (idx >= values.length) ? 0 : idx;
	}

	private static int indexOf(String[] values, String value) {
		if (value == null) return -1;
		for (int i = 0; i < values.length; i++) {
			if (values[i].equalsIgnoreCase(value)) return i;
		}
		return -1;
	}

	private static NormalizedInput normalize(int scheme, String host, int port, String username,
																					 String password, int output) {
		int normalizedScheme = clamp(scheme, SCHEMES);
		String normalizedHost = trim(host);
		int normalizedPort = Math.max(0, port);
		String normalizedUsername = trim(username);
		String normalizedPassword = trim(password);
		int normalizedOutput = clamp(output, OUTPUTS);

		if (!isNullOrBlank(normalizedHost)) {
			URI uri = parsePortalUri(normalizedScheme, normalizedHost);

			if (uri != null) {
				String parsedScheme = uri.getScheme();
				int parsedSchemeIdx = indexOf(SCHEMES, parsedScheme);
				if (parsedSchemeIdx != -1) normalizedScheme = parsedSchemeIdx;

				String parsedHost = trim(uri.getHost());
				if (!isNullOrBlank(parsedHost)) normalizedHost = parsedHost;

				int parsedPort = parsePort(uri);
				if (parsedPort > 0) normalizedPort = parsedPort;

				String userInfo = uri.getRawUserInfo();
				if (!isNullOrBlank(userInfo)) {
					int idx = userInfo.indexOf(':');
					if (idx >= 0) {
						if (isNullOrBlank(normalizedUsername)) {
							normalizedUsername = decodeComponent(userInfo.substring(0, idx));
						}
						if (isNullOrBlank(normalizedPassword)) {
							normalizedPassword = decodeComponent(userInfo.substring(idx + 1));
						}
					} else if (isNullOrBlank(normalizedUsername)) {
						normalizedUsername = decodeComponent(userInfo);
					}
				}

				if (isNullOrBlank(normalizedUsername)) {
					normalizedUsername = firstQueryParameter(uri, "username", "user");
				}
				if (isNullOrBlank(normalizedPassword)) {
					normalizedPassword = firstQueryParameter(uri, "password", "pass");
				}

				int parsedOutput = indexOf(OUTPUTS, firstQueryParameter(uri, "output"));
				if (parsedOutput != -1) normalizedOutput = parsedOutput;
			}
		}

		return new NormalizedInput(normalizedScheme, normalizedHost, normalizedPort,
				trim(normalizedUsername), trim(normalizedPassword), normalizedOutput);
	}

	@Nullable
	private static URI parsePortalUri(int scheme, String host) {
		String value = host.trim();
		if (value.isEmpty()) return null;

		if (value.startsWith("//")) {
			value = SCHEMES[scheme] + ':' + value;
		} else if (!hasScheme(value)) {
			value = SCHEMES[scheme] + "://" + value;
		}

		try {
			return new URI(value);
		} catch (URISyntaxException | RuntimeException ex) {
			return null;
		}
	}

	private static boolean hasScheme(String value) {
		int idx = value.indexOf("://");
		if (idx <= 0) return false;

		for (int i = 0; i < idx; i++) {
			char c = value.charAt(i);
			if (!(((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) ||
					((c >= '0') && (c <= '9')) || (c == '+') || (c == '-') || (c == '.'))) {
				return false;
			}
		}

		return true;
	}

	@Nullable
	private static String firstQueryParameter(URI uri, String... names) {
		String query = uri.getRawQuery();
		if (isNullOrBlank(query)) return null;

		for (String name : names) {
			String prefix = name + '=';
			int start = 0;

			while (start <= query.length()) {
				int end = query.indexOf('&', start);
				if (end < 0) end = query.length();
				String part = query.substring(start, end);
				if (part.regionMatches(true, 0, prefix, 0, prefix.length())) {
					String value = decodeComponent(part.substring(prefix.length()));
					if (!isNullOrBlank(value)) return value;
				}
				start = end + 1;
			}
		}
		return null;
	}

	private static int parsePort(URI uri) {
		int p = uri.getPort();
		return (p > 0) && (p <= 65535) ? p : -1;
	}

	private static String trim(String s) {
		return (s == null) ? null : s.trim();
	}

	private static String decodeComponent(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
		} catch (Exception ex) {
			return value;
		}
	}

	private static String encodePath(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (Exception ex) {
			return value;
		}
	}

	private static String normalizeExtension(String extension) {
		String ext = trim(extension);
		if (isNullOrBlank(ext)) return "mp4";
		while (ext.startsWith(".")) ext = ext.substring(1);
		return isNullOrBlank(ext) ? "mp4" : ext;
	}

	private static String formatTimeshiftStart(long time) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US);
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		return df.format(new Date(time));
	}

	private static final class NormalizedInput {
		final int scheme;
		final String host;
		final int port;
		final String username;
		final String password;
		final int output;

		NormalizedInput(int scheme, String host, int port, String username, String password,
										int output) {
			this.scheme = scheme;
			this.host = host;
			this.port = port;
			this.username = username;
			this.password = password;
			this.output = output;
		}
	}

	@NonNull
	@Override
	public String toString() {
		return String.format(Locale.ROOT, "XtreamAccount{%s://%s, user=***}", getScheme(),
				authority());
	}
}
