package me.aap.fermata.addon.stremio.integration;

import java.net.URI;
import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.RequestEncoder;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

/** Builds the wire request and its opaque cache identity without performing I/O. */
final class StremioProtocolRequestPlanner {
	private StremioProtocolRequestPlanner() {
	}

	static RequestPlan plan(String transportUrl, StremioRequest request,
			String transportFingerprint, String addonId, long updatedMs,
			boolean allowCleartext, boolean allowLan) {
		Objects.requireNonNull(request, "request");
		URI manifestUri = normalizeManifestUri(transportUrl);
		URI requestUri = RequestEncoder.resolve(manifestUri, request);
		String cacheIdentity = RequestEncoder.encodePath(request) + '\u0000' +
				transportFingerprint + '\u0000' + addonId + '\u0000' + updatedMs + '\u0000' +
				allowCleartext + '\u0000' + allowLan + '\u0000' + requestUri.toASCIIString();
		return new RequestPlan(requestUri, cacheIdentity);
	}

	private static URI normalizeManifestUri(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Manifest URL is missing");
		}
		URI uri;
		try {
			uri = URI.create(value.trim());
			if ("stremio".equalsIgnoreCase(uri.getScheme())) {
				String raw = uri.toASCIIString();
				uri = URI.create("https" + raw.substring(raw.indexOf(':')));
			}
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Invalid manifest URL", error);
		}
		String scheme = uri.getScheme();
		String path = uri.getRawPath();
		if (scheme == null || uri.getHost() == null || uri.getRawUserInfo() != null ||
				(!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) ||
				path == null || !path.endsWith("/manifest.json")) {
			throw new IllegalArgumentException("Invalid manifest URL");
		}
		URI normalized = uri.normalize();
		if (!Objects.equals(uri.getRawPath(), normalized.getRawPath())) {
			throw new IllegalArgumentException("Manifest URL path is not normalized");
		}
		return normalized;
	}

	record RequestPlan(URI uri, String cacheIdentity) {
		RequestPlan {
			Objects.requireNonNull(uri, "uri");
			Objects.requireNonNull(cacheIdentity, "cacheIdentity");
		}
	}
}
