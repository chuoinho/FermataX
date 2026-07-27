package me.aap.fermata.addon.stremio.runtime;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.net.http.HttpTransport;
import me.aap.fermata.addon.stremio.net.http.TransportCall;
import me.aap.fermata.addon.stremio.net.http.TransportRequest;
import me.aap.fermata.addon.stremio.net.http.TransportResponse;

/** Owns every transport call, including cache revalidation calls hidden from consumers. */
final class LifecycleHttpTransport implements HttpTransport, AutoCloseable {
	private final HttpTransport delegate;
	private final Set<OwnedCall> activeCalls = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean closed = new AtomicBoolean();

	LifecycleHttpTransport(HttpTransport delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public TransportCall execute(TransportRequest request) {
		if (closed.get()) return cancelled();
		TransportCall call = delegate.execute(request);
		OwnedCall owned = new OwnedCall(call);
		activeCalls.add(owned);
		owned.start();
		if (closed.get()) {
			owned.cancel();
			return cancelled();
		}
		return owned;
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		for (OwnedCall call : activeCalls) call.cancel();
		activeCalls.clear();
	}

	int activeCallCount() {
		return activeCalls.size();
	}

	private static TransportCall cancelled() {
		CompletableFuture<TransportResponse> response = CompletableFuture.failedFuture(
				new HttpFailure(HttpFailure.Code.CANCELLED, "Stremio runtime is closed"));
		return new TransportCall() {
			@Override
			public CompletableFuture<TransportResponse> response() {
				return response;
			}

			@Override
			public void cancel() {
			}
		};
	}

	private final class OwnedCall implements TransportCall {
		private final TransportCall delegate;
		private final CompletableFuture<TransportResponse> response = new CompletableFuture<>();
		private final AtomicBoolean cancelled = new AtomicBoolean();

		private OwnedCall(TransportCall delegate) {
			this.delegate = delegate;
		}

		private void start() {
			delegate.response().whenComplete((value, error) -> {
				if (cancelled.get()) {
					if (value != null) value.close();
					return;
				}
				if (error != null) {
					activeCalls.remove(this);
					response.completeExceptionally(error);
				} else {
					response.complete(new OwnedResponse(value, this));
				}
			});
		}

		@Override
		public CompletableFuture<TransportResponse> response() {
			return response;
		}

		@Override
		public void cancel() {
			activeCalls.remove(this);
			if (!cancelled.compareAndSet(false, true)) return;
			response.completeExceptionally(new HttpFailure(HttpFailure.Code.CANCELLED,
					"Stremio runtime is closed"));
			delegate.cancel();
		}
	}

	private final class OwnedResponse implements TransportResponse {
		private final TransportResponse delegate;
		private final OwnedCall owner;
		private final AtomicBoolean closed = new AtomicBoolean();

		private OwnedResponse(TransportResponse delegate, OwnedCall owner) {
			this.delegate = delegate;
			this.owner = owner;
		}

		@Override
		public int status() {
			return delegate.status();
		}

		@Override
		public java.util.Map<String, String> headers() {
			return delegate.headers();
		}

		@Override
		public java.io.InputStream body() throws java.io.IOException {
			return delegate.body();
		}

		@Override
		public void close() {
			if (!closed.compareAndSet(false, true)) return;
			activeCalls.remove(owner);
			delegate.close();
		}
	}
}
