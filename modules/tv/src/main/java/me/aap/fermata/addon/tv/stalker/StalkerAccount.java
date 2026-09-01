package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

/** User-supplied identity and connection options for a Stalker/Ministra portal. */
public final class StalkerAccount {
	public static final String DEFAULT_USER_AGENT =
			"Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 " +
					"(KHTML, like Gecko) MAG254 stbapp ver: 4 rev: 2721 Mobile Safari/533.3";
	public static final Pref<Supplier<String>> NAME = Pref.s("STALKER_NAME");
	public static final Pref<Supplier<String>> PORTAL = Pref.s("STALKER_PORTAL");
	public static final Pref<Supplier<String>> MAC = Pref.s("STALKER_MAC");
	public static final Pref<Supplier<String>> SERIAL = Pref.s("STALKER_SERIAL");
	public static final Pref<Supplier<String>> DEVICE_ID = Pref.s("STALKER_DEVICE_ID");

	private final int sourceId;
	private final String name;
	private final String portal;
	private final String mac;
	private final String serial;
	private final String deviceId;
	private final String userAgent;
	private final int responseTimeout;

	public StalkerAccount(int sourceId, String name, String portal, String mac,
			@Nullable String serial, @Nullable String deviceId, @Nullable String userAgent,
			int responseTimeout) {
		this.sourceId = sourceId;
		this.name = trim(name);
		this.portal = normalizePortal(portal);
		this.mac = normalizeMac(mac);
		this.serial = trim(serial);
		this.deviceId = trim(deviceId);
		this.userAgent = trim(userAgent);
		this.responseTimeout = Math.max(0, responseTimeout);
	}

	public static StalkerAccount fromPrefs(PreferenceStore ps, String userAgent,
			int responseTimeout) {
		return new StalkerAccount(0, ps.getStringPref(NAME), ps.getStringPref(PORTAL),
				ps.getStringPref(MAC), ps.getStringPref(SERIAL), ps.getStringPref(DEVICE_ID),
				userAgent, responseTimeout);
	}

	@Nullable
	public static StalkerAccount load(PreferenceStore ps, int sourceId) {
		String portal = ps.getStringPref(portalPref(sourceId));
		StalkerCredentials.Identity identity = StalkerCredentials.load(sourceId);
		String mac = (identity == null) ? ps.getStringPref(macPref(sourceId)) : identity.mac;
		String serial = (identity == null) ? ps.getStringPref(serialPref(sourceId)) : identity.serial;
		String deviceId = (identity == null) ? ps.getStringPref(deviceIdPref(sourceId)) :
				identity.deviceId;
		if (isNullOrBlank(portal) || isNullOrBlank(mac)) return null;
		if (identity == null) migrateLegacyIdentity(ps, sourceId, mac, serial, deviceId);
		return new StalkerAccount(sourceId, ps.getStringPref(namePref(sourceId)), portal, mac,
				serial, deviceId, ps.getStringPref(agentPref(sourceId)),
				ps.getIntPref(timeoutPref(sourceId)));
	}

	public static void save(PreferenceStore.Edit edit, int sourceId, StalkerAccount account) {
		requireCredentialStorage();
		edit.setStringPref(namePref(sourceId), account.getRawName());
		edit.setStringPref(portalPref(sourceId), account.getPortal());
		StalkerCredentials.save(edit, sourceId, account.getMac(), account.getSerial(),
				account.getDeviceId());
		edit.setStringPref(agentPref(sourceId), account.getRawUserAgent());
		edit.setIntPref(timeoutPref(sourceId), account.getResponseTimeout());
	}

	public static void remove(PreferenceStore.Edit edit, int sourceId) {
		edit.removePref(namePref(sourceId));
		edit.removePref(portalPref(sourceId));
		StalkerCredentials.remove(edit, sourceId);
		edit.removePref(agentPref(sourceId));
		edit.removePref(timeoutPref(sourceId));
	}

	public static void requireCredentialStorage() {
		StalkerCredentials.requireAvailable();
	}

	private static void migrateLegacyIdentity(PreferenceStore ps, int sourceId, String mac,
			String serial, String deviceId) {
		try (PreferenceStore.Edit edit = ps.editPreferenceStore()) {
			StalkerCredentials.save(edit, sourceId, mac, serial, deviceId);
		} catch (RuntimeException ex) {
			Log.e(ex, "Failed to migrate Stalker identity for source ", sourceId);
		}
	}

	public static Pref<Supplier<String>> namePref(int sourceId) {
		return Pref.s("STALKER_NAME#" + sourceId);
	}

	public static Pref<Supplier<String>> portalPref(int sourceId) {
		return Pref.s("STALKER_PORTAL#" + sourceId);
	}

	static Pref<Supplier<String>> macPref(int sourceId) {
		return Pref.s("STALKER_MAC#" + sourceId);
	}

	static Pref<Supplier<String>> serialPref(int sourceId) {
		return Pref.s("STALKER_SERIAL#" + sourceId);
	}

	static Pref<Supplier<String>> deviceIdPref(int sourceId) {
		return Pref.s("STALKER_DEVICE_ID#" + sourceId);
	}

	public static Pref<Supplier<String>> agentPref(int sourceId) {
		return Pref.s("STALKER_AGENT#" + sourceId);
	}

	public static Pref<IntSupplier> timeoutPref(int sourceId) {
		return Pref.i("STALKER_RESP_TIMEOUT#" + sourceId, 30);
	}

	public int getSourceId() {
		return sourceId;
	}

	@NonNull
	public StalkerAccount withSourceId(int sourceId) {
		return new StalkerAccount(sourceId, name, portal, mac, serial, deviceId, userAgent,
				responseTimeout);
	}

	@Nullable
	public String getRawName() {
		return name;
	}

	public String getName() {
		if (!isNullOrBlank(name)) return name;
		try {
			String host = new URI(portal).getHost();
			if (!isNullOrBlank(host)) return host;
		} catch (URISyntaxException ignore) {
		}
		return "Stalker Portal";
	}

	public String getPortal() {
		return portal;
	}

	public URI getPortalUri() {
		return URI.create(portal);
	}

	public String getMac() {
		return mac;
	}

	@Nullable
	public String getSerial() {
		return isNullOrBlank(serial) ? null : serial;
	}

	@Nullable
	public String getDeviceId() {
		return isNullOrBlank(deviceId) ? null : deviceId;
	}

	@Nullable
	public String getRawUserAgent() {
		return userAgent;
	}

	public String getUserAgent() {
		return isNullOrBlank(userAgent) ? DEFAULT_USER_AGENT : userAgent;
	}

	public int getResponseTimeout() {
		return responseTimeout;
	}

	public boolean isComplete() {
		if (isNullOrBlank(portal) || !isValidMac(mac)) return false;
		try {
			URI uri = new URI(portal);
			return isHttp(uri.getScheme()) && !isNullOrBlank(uri.getHost()) &&
					(uri.getRawUserInfo() == null);
		} catch (URISyntaxException ex) {
			return false;
		}
	}

	public List<String> getEndpointCandidates() {
		if (isNullOrBlank(portal)) return List.of();
		URI uri = URI.create(portal);
		String path = uri.getPath();
		if (path == null) path = "";
		String lower = path.toLowerCase(Locale.ROOT);
		Set<String> endpoints = new LinkedHashSet<>();

		if (lower.endsWith("/server/load.php") || lower.endsWith("/portal.php")) {
			endpoints.add(uri.toString());
		} else {
			String base = uri.toString();
			if (!base.endsWith("/")) base += '/';
			endpoints.add(base + "server/load.php");
			endpoints.add(base + "portal.php");
			if (path.isEmpty() || "/".equals(path)) {
				endpoints.add(base + "stalker_portal/server/load.php");
			}
		}
		return new ArrayList<>(endpoints);
	}

	public String getPortalReferer() {
		URI uri = URI.create(portal);
		String path = uri.getPath();
		if (path == null) path = "/";
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.endsWith("/server/load.php")) {
			path = path.substring(0, path.length() - "server/load.php".length()) + "c/";
		} else if (lower.endsWith("/portal.php")) {
			path = path.substring(0, path.length() - "portal.php".length()) + "c/";
		} else {
			if (!path.endsWith("/")) path += '/';
			path += "c/";
		}
		try {
			return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), path, null, null)
					.toString();
		} catch (URISyntaxException ex) {
			return portal;
		}
	}

	public String cookie() {
		return "mac=" + mac + "; stb_lang=en; timezone=UTC";
	}

	public String redact(String value) {
		if (value == null) return null;
		String redacted = value.replace(mac, "**:**:**:**:**:**");
		String encoded = Uri.encode(mac);
		if (!isNullOrBlank(encoded)) redacted = redacted.replace(encoded, "***");
		if (!isNullOrBlank(serial)) redacted = redacted.replace(serial, "***");
		if (!isNullOrBlank(deviceId)) redacted = redacted.replace(deviceId, "***");
		return redacted.replaceAll("(?i)(https?://[^\\s?]+)\\?[^\\s]+", "$1?***");
	}

	private static String normalizePortal(String value) {
		String portal = trim(value);
		if (isNullOrBlank(portal)) return portal;
		if (portal.startsWith("//")) portal = "http:" + portal;
		else if (!portal.contains("://")) portal = "http://" + portal;

		try {
			URI uri = new URI(portal);
			String path = uri.getPath();
			if (path == null) path = "";
			while ((path.length() > 1) && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			if (path.toLowerCase(Locale.ROOT).endsWith("/c")) {
				path = path.substring(0, path.length() - 2);
				if (path.isEmpty()) path = "/";
			}
			return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null, uri.getHost(),
					uri.getPort(), path, null, null).toString();
		} catch (URISyntaxException | RuntimeException ex) {
			return portal;
		}
	}

	private static String normalizeMac(String value) {
		String mac = trim(value);
		if (isNullOrBlank(mac)) return mac;
		String hex = mac.replace(":", "").replace("-", "").replace(".", "");
		if (!hex.matches("(?i)[0-9a-f]{12}")) return mac.toUpperCase(Locale.ROOT);
		StringBuilder normalized = new StringBuilder(17);
		for (int i = 0; i < hex.length(); i += 2) {
			if (i != 0) normalized.append(':');
			normalized.append(hex, i, i + 2);
		}
		return normalized.toString().toUpperCase(Locale.ROOT);
	}

	private static boolean isValidMac(String mac) {
		return (mac != null) && mac.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}");
	}

	private static boolean isHttp(String scheme) {
		return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
	}

	private static String trim(String value) {
		return (value == null) ? null : value.trim();
	}

	@NonNull
	@Override
	public String toString() {
		return "StalkerAccount{" + portal + ", mac=**:**:**:**:**:**}";
	}
}
