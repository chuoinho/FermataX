package me.aap.fermata.media.net;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable description of a remote playback request.
 *
 * <p>The profile stores only an opaque header reference. Header values are resolved just before a
 * request and are exposed only after validation for the destination origin.</p>
 */
public final class PlaybackRequestProfile {
	public static final int MAX_HEADER_COUNT = 16;
	public static final int MAX_HEADER_NAME_BYTES = 64;
	public static final int MAX_HEADER_VALUE_BYTES = 8 * 1024;
	public static final long NO_EXPIRY = Long.MAX_VALUE;

	private static final Set<String> ALLOWED_HEADERS = Set.of(
			"user-agent", "referer", "origin", "cookie", "authorization", "accept",
			"accept-language");
	private static final Set<String> FORBIDDEN_HEADERS = Set.of(
			"connection", "content-length", "host", "keep-alive", "proxy-authenticate",
			"proxy-authorization", "proxy-connection", "range", "te", "trailer",
			"transfer-encoding", "upgrade");
	private static final Map<String, String> CANONICAL_HEADER_NAMES = Map.of(
			"user-agent", "User-Agent",
			"referer", "Referer",
			"origin", "Origin",
			"cookie", "Cookie",
			"authorization", "Authorization",
			"accept", "Accept",
			"accept-language", "Accept-Language");

	private final URI targetUri;
	private final HeaderReference headerReference;
	private final Set<Origin> allowedOrigins;
	private final long expiresAtEpochMillis;
	private final RedirectPolicy redirectPolicy;
	private final String diagnosticIdentity;
	private final Set<EngineCapability> requiredEngineCapabilities;

	private PlaybackRequestProfile(Builder builder) {
		targetUri = validateHttpUri(builder.targetUri, "target URI");
		Origin targetOrigin = Origin.from(targetUri);
		headerReference = builder.headerReference;
		expiresAtEpochMillis = builder.expiresAtEpochMillis;
		redirectPolicy = Objects.requireNonNull(builder.redirectPolicy, "redirectPolicy");
		diagnosticIdentity = createDiagnosticIdentity(builder.diagnosticSource);

		if (expiresAtEpochMillis <= 0) {
			throw new IllegalArgumentException("Expiry must be a positive epoch timestamp");
		}

		LinkedHashSet<Origin> origins = new LinkedHashSet<>();
		origins.add(targetOrigin);
		origins.addAll(builder.allowedOrigins);
		if ((redirectPolicy != RedirectPolicy.ALLOW_LISTED_ORIGINS) && (origins.size() > 1)) {
			throw new IllegalArgumentException(
					"Cross-origin allowlist requires ALLOW_LISTED_ORIGINS policy");
		}
		allowedOrigins = Collections.unmodifiableSet(origins);

		EnumSet<EngineCapability> capabilities = builder.requiredEngineCapabilities.clone();
		capabilities.add(EngineCapability.REDIRECT_ORIGIN_POLICY);
		if (redirectPolicy == RedirectPolicy.VALIDATED_ENDPOINTS) {
			capabilities.add(EngineCapability.ENDPOINT_VALIDATION);
		}
		if (headerReference != null) capabilities.add(EngineCapability.REQUEST_HEADERS);
		if (origins.size() > 1) capabilities.add(EngineCapability.CROSS_ORIGIN_REQUESTS);
		requiredEngineCapabilities = Collections.unmodifiableSet(capabilities);
	}

	public static Builder builder(URI targetUri, String diagnosticSource) {
		return new Builder(targetUri, diagnosticSource);
	}

	public URI getTargetUri() {
		return targetUri;
	}

	public HeaderReference getHeaderReference() {
		return headerReference;
	}

	public Set<Origin> getAllowedOrigins() {
		return allowedOrigins;
	}

	public long getExpiresAtEpochMillis() {
		return expiresAtEpochMillis;
	}

	public RedirectPolicy getRedirectPolicy() {
		return redirectPolicy;
	}

	public String getDiagnosticIdentity() {
		return diagnosticIdentity;
	}

	public Set<EngineCapability> getRequiredEngineCapabilities() {
		return requiredEngineCapabilities;
	}

	public boolean isExpired(long nowEpochMillis) {
		return nowEpochMillis >= expiresAtEpochMillis;
	}

	public boolean isRedirectAllowed(URI currentUri, URI destinationUri) {
		Origin current = Origin.from(validateHttpUri(currentUri, "current URI"));
		Origin destination = Origin.from(validateHttpUri(destinationUri, "redirect URI"));
		Origin target = Origin.from(targetUri);

		return switch (redirectPolicy) {
			case DENY -> false;
			case SAME_ORIGIN -> current.equals(target) && destination.equals(target);
			case ALLOW_LISTED_ORIGINS -> allowedOrigins.contains(current)
					&& allowedOrigins.contains(destination);
			case VALIDATED_ENDPOINTS -> true;
		};
	}

	boolean isRequestOriginAllowed(URI requestUri) {
		Origin origin = Origin.from(validateHttpUri(requestUri, "request URI"));
		return (redirectPolicy == RedirectPolicy.VALIDATED_ENDPOINTS) ||
				allowedOrigins.contains(origin);
	}

	/**
	 * Resolves and validates headers for one request destination.
	 * Credentials and origin-bearing headers are always removed outside the target origin.
	 */
	public ResolvedHeaders resolveHeaders(URI requestUri, long nowEpochMillis,
			PlaybackHeaderResolver resolver) throws PlaybackRequestValidationException {
		if (isExpired(nowEpochMillis)) {
			throw new PlaybackRequestValidationException(
					"Playback request profile has expired: " + diagnosticIdentity);
		}

		Origin requestOrigin;
		try {
			requestOrigin = Origin.from(validateHttpUri(requestUri, "request URI"));
		} catch (IllegalArgumentException ex) {
			throw new PlaybackRequestValidationException("Invalid playback request origin", ex);
		}
		if (!isRequestOriginAllowed(requestUri)) {
			throw new PlaybackRequestValidationException(
					"Request origin is not allowed: " + diagnosticIdentity);
		}

		if (headerReference == null) {
			return new ResolvedHeaders(Collections.emptyMap(), requiredEngineCapabilities);
		}
		if (resolver == null) {
			throw new PlaybackRequestValidationException("Header resolver is required");
		}

		Map<String, String> resolved = resolver.resolve(headerReference);
		if (resolved == null) {
			throw new PlaybackRequestValidationException("Header resolver returned no result");
		}
		LinkedHashMap<String, String> headers = validateHeaders(resolved);
		boolean crossOrigin = !Origin.from(targetUri).equals(requestOrigin);
		if (crossOrigin) {
			headers.remove("Authorization");
			headers.remove("Cookie");
			headers.remove("Referer");
			headers.remove("Origin");
		}

		EnumSet<EngineCapability> capabilities = requiredEngineCapabilities.isEmpty()
				? EnumSet.noneOf(EngineCapability.class)
				: EnumSet.copyOf(requiredEngineCapabilities);
		if (headers.containsKey("Authorization")) {
			capabilities.add(EngineCapability.AUTHORIZATION_HEADER);
		}
		if (headers.containsKey("Cookie")) capabilities.add(EngineCapability.COOKIE_HEADER);
		if (headers.containsKey("Referer")) capabilities.add(EngineCapability.REFERER_HEADER);
		if (headers.containsKey("Origin")) capabilities.add(EngineCapability.ORIGIN_HEADER);
		if (headers.containsKey("User-Agent")) {
			capabilities.add(EngineCapability.USER_AGENT_HEADER);
		}
		return new ResolvedHeaders(headers, capabilities);
	}

	@Override
	public String toString() {
		return "PlaybackRequestProfile{" + diagnosticIdentity + ", redirect=" + redirectPolicy
				+ ", expires=" + expiresAtEpochMillis + '}';
	}

	private static LinkedHashMap<String, String> validateHeaders(Map<String, String> source)
			throws PlaybackRequestValidationException {
		if (source.size() > MAX_HEADER_COUNT) {
			throw new PlaybackRequestValidationException(
					"Too many playback request headers: " + source.size());
		}

		LinkedHashMap<String, String> validated = new LinkedHashMap<>();
		Set<String> seen = new LinkedHashSet<>();
		for (Map.Entry<String, String> entry : source.entrySet()) {
			String name = entry.getKey();
			String value = entry.getValue();
			if ((name == null) || name.isEmpty()) {
				throw new PlaybackRequestValidationException("Header name is empty");
			}
			if (name.getBytes(StandardCharsets.UTF_8).length > MAX_HEADER_NAME_BYTES) {
				throw new PlaybackRequestValidationException("Header name exceeds 64 bytes");
			}
			if (!isHttpToken(name)) {
				throw new PlaybackRequestValidationException("Header name is invalid");
			}

			String normalizedName = name.toLowerCase(Locale.ROOT);
			if (!seen.add(normalizedName)) {
				throw new PlaybackRequestValidationException(
						"Duplicate playback request header: " + normalizedName);
			}
			if (FORBIDDEN_HEADERS.contains(normalizedName)) {
				throw new PlaybackRequestValidationException(
						"Forbidden playback request header: " + normalizedName);
			}
			if (!ALLOWED_HEADERS.contains(normalizedName)) {
				throw new PlaybackRequestValidationException(
						"Unsupported playback request header: " + normalizedName);
			}
			if (value == null) {
				throw new PlaybackRequestValidationException(
						"Header value is missing: " + normalizedName);
			}
			if (value.getBytes(StandardCharsets.UTF_8).length > MAX_HEADER_VALUE_BYTES) {
				throw new PlaybackRequestValidationException(
						"Header value exceeds 8 KiB: " + normalizedName);
			}

			validated.put(CANONICAL_HEADER_NAMES.get(normalizedName), stripControls(value));
		}
		return validated;
	}

	private static String stripControls(String value) {
		StringBuilder clean = null;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isISOControl(c)) {
				if (clean == null) clean = new StringBuilder(value.length()).append(value, 0, i);
			} else if (clean != null) {
				clean.append(c);
			}
		}
		return (clean == null) ? value : clean.toString();
	}

	private static boolean isHttpToken(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c <= 0x20) || (c >= 0x7f) || "()<>@,;:\\\"/[]?={}\t".contains(
					String.valueOf(c))) return false;
		}
		return true;
	}

	private static URI validateHttpUri(URI uri, String label) {
		Objects.requireNonNull(uri, label);
		String scheme = uri.getScheme();
		if ((scheme == null) || (!scheme.equalsIgnoreCase("http")
				&& !scheme.equalsIgnoreCase("https")) || (uri.getHost() == null)) {
			throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URI");
		}
		if (uri.getRawUserInfo() != null) {
			throw new IllegalArgumentException(label + " must not contain user info");
		}
		int port = uri.getPort();
		if ((port == 0) || (port > 65535)) throw new IllegalArgumentException(label + " has bad port");
		return uri;
	}

	private static String createDiagnosticIdentity(String source) {
		if ((source == null) || source.isBlank()) {
			throw new IllegalArgumentException("Diagnostic source must not be empty");
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(source.getBytes(StandardCharsets.UTF_8));
			StringBuilder id = new StringBuilder("playback:");
			for (int i = 0; i < 12; i++) id.append(String.format(Locale.ROOT, "%02x", digest[i]));
			return id.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("SHA-256 is unavailable", ex);
		}
	}

	public enum RedirectPolicy {
		DENY,
		SAME_ORIGIN,
		ALLOW_LISTED_ORIGINS,
		/** Every hop must pass the request's endpoint validator before connection. */
		VALIDATED_ENDPOINTS
	}

	public enum EngineCapability {
		REDIRECT_ORIGIN_POLICY,
		ENDPOINT_VALIDATION,
		REQUEST_HEADERS,
		AUTHORIZATION_HEADER,
		COOKIE_HEADER,
		REFERER_HEADER,
		ORIGIN_HEADER,
		USER_AGENT_HEADER,
		CROSS_ORIGIN_REQUESTS,
		SINGLE_ATTEMPT_LOADING,
		/** Marks a loopback bridge backed by a peer-to-peer transport. */
		P2P_STREAMING
	}

	/** Opaque lookup key whose string representation never exposes the underlying identifier. */
	public static final class HeaderReference {
		private final String opaqueId;

		private HeaderReference(String opaqueId) {
			if ((opaqueId == null) || opaqueId.isBlank()) {
				throw new IllegalArgumentException("Header reference must not be empty");
			}
			for (int i = 0; i < opaqueId.length(); i++) {
				if (Character.isISOControl(opaqueId.charAt(i))) {
					throw new IllegalArgumentException("Header reference contains control characters");
				}
			}
			this.opaqueId = opaqueId;
		}

		public static HeaderReference of(String opaqueId) {
			return new HeaderReference(opaqueId);
		}

		public String getOpaqueId() {
			return opaqueId;
		}

		@Override
		public boolean equals(Object obj) {
			return (this == obj) || ((obj instanceof HeaderReference other)
					&& opaqueId.equals(other.opaqueId));
		}

		@Override
		public int hashCode() {
			return opaqueId.hashCode();
		}

		@Override
		public String toString() {
			return "HeaderReference{redacted}";
		}
	}

	/** Normalized HTTP origin with default ports folded into the scheme. */
	public static final class Origin {
		private final String scheme;
		private final String host;
		private final int port;

		private Origin(String scheme, String host, int port) {
			this.scheme = scheme;
			this.host = host;
			this.port = port;
		}

		public static Origin from(URI uri) {
			validateHttpUri(uri, "origin URI");
			String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
			int port = uri.getPort();
			if (port == -1) port = scheme.equals("https") ? 443 : 80;
			return new Origin(scheme, host, port);
		}

		public String getScheme() {
			return scheme;
		}

		public String getHost() {
			return host;
		}

		public int getPort() {
			return port;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Origin other)) return false;
			return (port == other.port) && scheme.equals(other.scheme) && host.equals(other.host);
		}

		@Override
		public int hashCode() {
			return Objects.hash(scheme, host, port);
		}

		@Override
		public String toString() {
			return scheme + "://" + host + ':' + port;
		}
	}

	public static final class ResolvedHeaders {
		private final Map<String, String> headers;
		private final Set<EngineCapability> requiredEngineCapabilities;

		private ResolvedHeaders(Map<String, String> headers,
				Set<EngineCapability> requiredEngineCapabilities) {
			this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
			EnumSet<EngineCapability> capabilities = requiredEngineCapabilities.isEmpty()
					? EnumSet.noneOf(EngineCapability.class)
					: EnumSet.copyOf(requiredEngineCapabilities);
			this.requiredEngineCapabilities = Collections.unmodifiableSet(capabilities);
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public Set<EngineCapability> getRequiredEngineCapabilities() {
			return requiredEngineCapabilities;
		}

		public boolean isSupportedBy(Set<EngineCapability> engineCapabilities) {
			return engineCapabilities.containsAll(requiredEngineCapabilities);
		}
	}

	public static final class Builder {
		private final URI targetUri;
		private final String diagnosticSource;
		private final LinkedHashSet<Origin> allowedOrigins = new LinkedHashSet<>();
		private final EnumSet<EngineCapability> requiredEngineCapabilities =
				EnumSet.noneOf(EngineCapability.class);
		private HeaderReference headerReference;
		private long expiresAtEpochMillis = NO_EXPIRY;
		private RedirectPolicy redirectPolicy = RedirectPolicy.SAME_ORIGIN;

		private Builder(URI targetUri, String diagnosticSource) {
			this.targetUri = Objects.requireNonNull(targetUri, "targetUri");
			this.diagnosticSource = diagnosticSource;
		}

		public Builder headerReference(HeaderReference headerReference) {
			this.headerReference = headerReference;
			return this;
		}

		public Builder allowOrigin(URI uri) {
			allowedOrigins.add(Origin.from(uri));
			return this;
		}

		public Builder expiresAt(long expiresAtEpochMillis) {
			this.expiresAtEpochMillis = expiresAtEpochMillis;
			return this;
		}

		public Builder redirectPolicy(RedirectPolicy redirectPolicy) {
			this.redirectPolicy = Objects.requireNonNull(redirectPolicy, "redirectPolicy");
			return this;
		}

		public Builder requireCapability(EngineCapability capability) {
			requiredEngineCapabilities.add(Objects.requireNonNull(capability, "capability"));
			return this;
		}

		public PlaybackRequestProfile build() {
			return new PlaybackRequestProfile(this);
		}
	}
}
