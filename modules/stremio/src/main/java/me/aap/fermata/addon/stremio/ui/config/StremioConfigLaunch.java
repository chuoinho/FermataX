package me.aap.fermata.addon.stremio.ui.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.security.StremioUrlRedactor;
import me.aap.fermata.addon.stremio.net.NetworkConsent;

/** Opaque launch material for one provider configuration session. */
public final class StremioConfigLaunch {
	private static final String MANIFEST_SUFFIX = "/manifest.json";
	private static final String CONFIG_TOKEN_HEADER = "X-Stremio-Configuration-Token";
	private final String initialUrl;
	private final NetworkConsent consent;
	private final Map<String, String> initialHeaders;
	private final StremioConfigResourceLoader resourceLoader;

	public StremioConfigLaunch(StremioSourceSecret secret, boolean allowCleartext) {
		this(secret, new NetworkConsent(allowCleartext, false),
				StremioConfigResourceLoader.unavailable());
	}

	public StremioConfigLaunch(StremioSourceSecret secret, NetworkConsent consent,
			StremioConfigResourceLoader resourceLoader) {
		Objects.requireNonNull(secret, "secret");
		initialUrl = configurationUrl(secret.transportUrl());
		this.consent = Objects.requireNonNull(consent, "consent");
		this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
		String token = secret.configurationToken();
		if (token == null) {
			initialHeaders = Map.of();
		} else {
			if ((token.indexOf('\r') >= 0) || (token.indexOf('\n') >= 0)) {
				throw new IllegalArgumentException("Invalid configuration token");
			}
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put(CONFIG_TOKEN_HEADER, token);
			initialHeaders = Map.copyOf(headers);
		}
	}

	public StremioConfigWebController open(StremioConfigWebView view,
			StremioConfigCallback callback) {
		return StremioConfigWebController.production(view, initialUrl, consent,
				initialHeaders, resourceLoader, callback);
	}

	String initialUrlForTest() {
		return initialUrl;
	}

	Map<String, String> initialHeadersForTest() {
		return initialHeaders;
	}

	static String configurationUrl(String transportUrl) {
		URI source;
		try {
			source = URI.create(Objects.requireNonNull(transportUrl, "transportUrl").trim());
			if ("stremio".equalsIgnoreCase(source.getScheme())) {
				String raw = source.toASCIIString();
				source = URI.create("https" + raw.substring(raw.indexOf(':')));
			}
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Invalid Stremio transport URL", error);
		}
		String scheme = lower(source.getScheme());
		String path = source.getRawPath();
		if ((source.getHost() == null) || (source.getRawUserInfo() != null) ||
				(!"https".equals(scheme) && !"http".equals(scheme)) ||
				(path == null) || !path.toLowerCase(Locale.ROOT).endsWith(MANIFEST_SUFFIX)) {
			throw new IllegalArgumentException("Invalid Stremio manifest URL");
		}
		String configPath = path.substring(0, path.length() - MANIFEST_SUFFIX.length()) +
				"/configure";
		try {
			return new URI(scheme, null, source.getHost(), source.getPort(), configPath,
					null, null).toASCIIString();
		} catch (Exception error) {
			throw new IllegalArgumentException("Invalid Stremio configuration URL", error);
		}
	}

	@Override
	public String toString() {
		return "StremioConfigLaunch[url=" + StremioUrlRedactor.forMessage(initialUrl) +
				", tokenPresent=" + !initialHeaders.isEmpty() + ']';
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}
}
