package me.aap.fermata.addon.stremio.ui.config;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Bounded, policy-aware transport used by the configuration WebView. */
@FunctionalInterface
public interface StremioConfigResourceLoader {
	CompletableFuture<Response> load(URI uri, Map<String, String> headers);

	record Response(int status, URI finalUri, Map<String, String> headers, byte[] body) {
		public Response {
			headers = Map.copyOf(headers);
			body = body.clone();
		}

		@Override
		public byte[] body() {
			return body.clone();
		}

		@Override
		public String toString() {
			return "Response[status=" + status + ", redacted, bodyBytes=" + body.length + ']';
		}
	}

	static StremioConfigResourceLoader unavailable() {
		return (uri, headers) -> StremioFutures.failedFuture(
				new IllegalStateException("Configuration transport is unavailable"));
	}
}
