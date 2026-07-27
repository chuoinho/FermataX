package me.aap.fermata.addon.stremio.ui.config;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** Restricts provider configuration browsing to one explicitly approved origin. */
public final class StremioConfigUrlPolicy {
	private final String initialUrl;
	private final Origin providerOrigin;
	private final boolean allowCleartext;

	public StremioConfigUrlPolicy(String initialUrl, boolean allowCleartext) {
		this.initialUrl = Objects.requireNonNull(initialUrl, "initialUrl");
		this.allowCleartext = allowCleartext;
		URI initial = parse(initialUrl);
		providerOrigin = Origin.fromHttp(initial, allowCleartext);
	}

	public String initialUrl() {
		return initialUrl;
	}

	public boolean isInitialUrl(String candidate) {
		try {
			return parse(initialUrl).equals(parse(candidate));
		} catch (IllegalArgumentException error) {
			return false;
		}
	}

	public String providerOriginUrl() {
		URI uri = parse(initialUrl);
		return uri.getScheme() + "://" + uri.getRawAuthority();
	}

	public Decision evaluate(String candidate) {
		if ((candidate == null) || candidate.isBlank()) return Decision.BLOCKED;
		URI uri;
		try {
			uri = parse(candidate);
		} catch (IllegalArgumentException ex) {
			return Decision.BLOCKED;
		}

		String scheme = lower(uri.getScheme());
		if ("stremio".equals(scheme)) {
			return isManifest(uri) && providerOrigin.matchesStremio(uri) ?
					Decision.COMPLETE : Decision.BLOCKED;
		}
		if (!"https".equals(scheme) && !(allowCleartext && "http".equals(scheme))) {
			return Decision.BLOCKED;
		}
		if (!providerOrigin.matchesHttp(uri)) return Decision.BLOCKED;
		return isManifest(uri) ? Decision.COMPLETE : Decision.NAVIGATE;
	}

	public boolean isAllowedResource(String candidate) {
		return isAllowedResource(candidate, true);
	}

	/** Main-frame navigation stays origin-bound; validated HTTPS CDN subresources are allowed. */
	public boolean isAllowedResource(String candidate, boolean mainFrame) {
		final URI uri;
		try {
			uri = parse(candidate);
		} catch (IllegalArgumentException ex) {
			return false;
		}
		String scheme = lower(uri.getScheme());
		if (!"https".equals(scheme) && !(allowCleartext && "http".equals(scheme))) return false;
		if ((uri.getHost() == null) || (uri.getRawUserInfo() != null)) return false;
		return !mainFrame || (evaluate(candidate) != Decision.BLOCKED);
	}

	boolean isProviderOrigin(String candidate) {
		try {
			return providerOrigin.matchesHttp(parse(candidate));
		} catch (IllegalArgumentException error) {
			return false;
		}
	}

	private static URI parse(String value) {
		try {
			URI uri = new URI(value.trim());
			if ((uri.getScheme() == null) || (uri.getRawUserInfo() != null) ||
					containsControl(value)) throw new IllegalArgumentException("Unsafe URL");
			return uri;
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException("Invalid URL", ex);
		}
	}

	private static boolean containsControl(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c <= 0x1f) || (c == 0x7f)) return true;
		}
		return false;
	}

	private static boolean isManifest(URI uri) {
		String path = uri.getPath();
		return (path != null) && path.toLowerCase(Locale.ROOT).endsWith("/manifest.json");
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}

	public enum Decision {
		NAVIGATE,
		COMPLETE,
		BLOCKED
	}

	private record Origin(String scheme, String host, int port) {
		private static Origin fromHttp(URI uri, boolean allowCleartext) {
			String scheme = lower(uri.getScheme());
			if (!"https".equals(scheme) && !(allowCleartext && "http".equals(scheme))) {
				throw new IllegalArgumentException("Provider configuration requires HTTPS");
			}
			String host = normalizeHost(uri.getHost());
			if (host == null) throw new IllegalArgumentException("Provider host is missing");
			return new Origin(scheme, host, effectivePort(scheme, uri.getPort()));
		}

		private boolean matchesHttp(URI uri) {
			String candidateScheme = lower(uri.getScheme());
			return scheme.equals(candidateScheme) && host.equals(normalizeHost(uri.getHost())) &&
					port == effectivePort(candidateScheme, uri.getPort());
		}

		private boolean matchesStremio(URI uri) {
			int candidatePort = uri.getPort();
			if (candidatePort == -1) candidatePort = defaultPort(scheme);
			return host.equals(normalizeHost(uri.getHost())) && port == candidatePort;
		}

		private static String normalizeHost(String host) {
			if ((host == null) || host.isBlank()) return null;
			String normalized = host;
			if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
			if (normalized.indexOf(':') >= 0) return normalized.toLowerCase(Locale.ROOT);
			try {
				return IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
			} catch (IllegalArgumentException ex) {
				return null;
			}
		}

		private static int effectivePort(String scheme, int port) {
			return (port == -1) ? defaultPort(scheme) : port;
		}

		private static int defaultPort(String scheme) {
			return "http".equals(scheme) ? 80 : 443;
		}
	}
}
