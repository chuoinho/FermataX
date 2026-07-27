package me.aap.fermata.addon.stremio.net;

import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.INVALID_URL;
import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.UNSUPPORTED_SCHEME;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public final class EndpointNormalizer {
	private EndpointNormalizer() {
	}

	public static NormalizedEndpoint normalize(URI input) throws NetworkPolicyViolation {
		Objects.requireNonNull(input, "input");
		String scheme = input.getScheme();
		if (scheme == null) throw violation(INVALID_URL, "URL must be absolute");
		scheme = scheme.toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw violation(UNSUPPORTED_SCHEME, "Only HTTP and HTTPS URLs are supported");
		}
		if ((input.getRawUserInfo() != null) || (input.getRawFragment() != null)) {
			throw violation(INVALID_URL, "Credentials and fragments are not allowed in provider URLs");
		}

		Authority authority = parseAuthority(input.getRawAuthority());
		String host = authority.host();
		try {
			if (!host.contains(":")) host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
		} catch (IllegalArgumentException ex) {
			throw violation(INVALID_URL, "URL host is invalid");
		}
		host = host.toLowerCase(Locale.ROOT);
		while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		if (host.isEmpty()) throw violation(INVALID_URL, "URL host is required");

		int explicitPort = authority.port();
		int defaultPort = scheme.equals("https") ? 443 : 80;
		int port = (explicitPort == -1) ? defaultPort : explicitPort;
		String rawPath = input.getRawPath();
		if ((rawPath == null) || rawPath.isEmpty()) rawPath = "/";
		if (containsControl(rawPath) || containsControl(input.getRawQuery())) {
			throw violation(INVALID_URL, "URL contains control characters");
		}

		int normalizedPort = (port == defaultPort) ? -1 : port;
		String originHost = host.contains(":") ? '[' + host + ']' : host;
		String origin = scheme + "://" + originHost + ((normalizedPort == -1) ? "" : ":" + port);
		String value = origin + rawPath +
				((input.getRawQuery() == null) ? "" : '?' + input.getRawQuery());
		try {
			return new NormalizedEndpoint(URI.create(value), scheme, host, port, origin);
		} catch (IllegalArgumentException ex) {
			throw violation(INVALID_URL, "URL cannot be normalized");
		}
	}

	private static boolean containsControl(String value) {
		if (value == null) return false;
		for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
		return false;
	}

	private static Authority parseAuthority(String rawAuthority) throws NetworkPolicyViolation {
		if ((rawAuthority == null) || rawAuthority.isBlank() || rawAuthority.contains("@")) {
			throw violation(INVALID_URL, "URL host is required");
		}
		String host;
		String portText = null;
		if (rawAuthority.startsWith("[")) {
			int closing = rawAuthority.indexOf(']');
			if (closing <= 1) throw violation(INVALID_URL, "IPv6 host is invalid");
			host = rawAuthority.substring(1, closing);
			String tail = rawAuthority.substring(closing + 1);
			if (!tail.isEmpty()) {
				if (!tail.startsWith(":")) throw violation(INVALID_URL, "URL authority is invalid");
				portText = tail.substring(1);
			}
		} else {
			int colon = rawAuthority.lastIndexOf(':');
			if (colon >= 0) {
				if (rawAuthority.indexOf(':') != colon) {
					throw violation(INVALID_URL, "IPv6 hosts must use brackets");
				}
				host = rawAuthority.substring(0, colon);
				portText = rawAuthority.substring(colon + 1);
			} else {
				host = rawAuthority;
			}
		}
		if (host.isBlank() || host.contains("%")) throw violation(INVALID_URL, "URL host is invalid");
		int port = -1;
		if (portText != null) {
			try {
				port = Integer.parseInt(portText);
			} catch (NumberFormatException ex) {
				throw violation(INVALID_URL, "URL port is invalid");
			}
			if ((port < 1) || (port > 65535)) {
				throw violation(INVALID_URL, "URL port must be between 1 and 65535");
			}
		}
		return new Authority(host, port);
	}

	private static NetworkPolicyViolation violation(
			NetworkPolicyViolation.Reason reason, String message) {
		return new NetworkPolicyViolation(reason, message);
	}

	private record Authority(String host, int port) {
	}
}
