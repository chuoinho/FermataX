package me.aap.fermata.addon.stremio.source;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;

/** Bounded transport client supplied by the network layer. */
public interface StremioManifestClient {
	CompletableFuture<Response> fetch(Request request);

	final class Request {
		private final StremioSourceSecret secret;
		private final String etag;
		private final String lastModified;
		private final BooleanSupplier active;
		private final RequestGeneration.Token generation;
		private final NetworkConsent consent;

		public Request(StremioSourceSecret secret, String etag, String lastModified,
				BooleanSupplier active) {
			this(secret, etag, lastModified, active, NetworkConsent.STRICT);
		}

		public Request(StremioSourceSecret secret, String etag, String lastModified,
				BooleanSupplier active, NetworkConsent consent) {
			this.secret = Objects.requireNonNull(secret, "secret");
			this.etag = etag;
			this.lastModified = lastModified;
			this.active = Objects.requireNonNull(active, "active");
			this.generation = null;
			this.consent = Objects.requireNonNull(consent, "consent");
		}

		public Request(StremioSourceSecret secret, String etag, String lastModified,
				RequestGeneration.Token generation, NetworkConsent consent) {
			this.secret = Objects.requireNonNull(secret, "secret");
			this.etag = etag;
			this.lastModified = lastModified;
			this.generation = Objects.requireNonNull(generation, "generation");
			this.active = generation::isCurrent;
			this.consent = Objects.requireNonNull(consent, "consent");
		}

		public StremioSourceSecret secret() {
			return secret;
		}

		public String etag() {
			return etag;
		}

		public String lastModified() {
			return lastModified;
		}

		public boolean isActive() {
			return active.getAsBoolean();
		}

		public NetworkConsent consent() {
			return consent;
		}

		public boolean hasInvalidationSignal() {
			return generation != null;
		}

		public AutoCloseable onInvalidated(Runnable observer) {
			return (generation == null) ? () -> {} : generation.onInvalidated(observer);
		}

		@Override
		public String toString() {
			return "StremioManifestClient.Request[redacted]";
		}
	}

	record Response(String manifestJson, String etag, String lastModified, boolean notModified) {
		public Response {
			if (notModified) {
				if (manifestJson != null) {
					throw new IllegalArgumentException("Not-modified response cannot contain a body");
				}
			} else {
				Objects.requireNonNull(manifestJson, "manifestJson");
			}
		}

		public static Response modified(String json, String etag, String lastModified) {
			return new Response(json, etag, lastModified, false);
		}

		public static Response notModified(String etag, String lastModified) {
			return new Response(null, etag, lastModified, true);
		}

		@Override
		public String toString() {
			return "StremioManifestClient.Response[body=" +
					((manifestJson == null) ? 0 : manifestJson.length()) +
					" chars, notModified=" + notModified + ']';
		}
	}
}
