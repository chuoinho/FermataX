package me.aap.fermata.addon.stremio.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.AddressKind;
import me.aap.fermata.addon.stremio.net.NormalizedEndpoint;
import me.aap.fermata.addon.stremio.net.ValidatedEndpoint;
import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.net.http.TransportCall;
import me.aap.fermata.addon.stremio.net.http.TransportRequest;
import me.aap.fermata.addon.stremio.net.http.TransportResponse;

public class LifecycleHttpTransportTest {
	@Test
	public void closeCancelsEveryHiddenTransportCallAndRejectsNewOnes() throws Exception {
		AtomicBoolean firstCancelled = new AtomicBoolean();
		AtomicBoolean secondCancelled = new AtomicBoolean();
		CompletableFuture<TransportResponse> first = new CompletableFuture<>();
		CompletableFuture<TransportResponse> second = new CompletableFuture<>();
		var responses = new java.util.ArrayDeque<CompletableFuture<TransportResponse>>();
		responses.add(first);
		responses.add(second);
		var cancellations = new java.util.ArrayDeque<AtomicBoolean>();
		cancellations.add(firstCancelled);
		cancellations.add(secondCancelled);
		var transport = new LifecycleHttpTransport(request -> {
			CompletableFuture<TransportResponse> response = responses.remove();
			AtomicBoolean cancelled = cancellations.remove();
			return call(response, cancelled);
		});

		transport.execute(request());
		transport.execute(request());
		assertEquals(2, transport.activeCallCount());

		transport.close();

		assertTrue(firstCancelled.get());
		assertTrue(secondCancelled.get());
		assertEquals(0, transport.activeCallCount());
		ExecutionException failure = assertThrows(ExecutionException.class,
				() -> transport.execute(request()).response().get());
		assertEquals(HttpFailure.Code.CANCELLED, ((HttpFailure) failure.getCause()).code());
	}

	@Test
	public void deliveredResponseRemainsOwnedUntilBodyIsClosed() throws Exception {
		AtomicBoolean delegateClosed = new AtomicBoolean();
		TransportResponse response = new TransportResponse() {
			@Override
			public int status() {
				return 200;
			}

			@Override
			public Map<String, String> headers() {
				return Map.of();
			}

			@Override
			public java.io.InputStream body() {
				return new java.io.ByteArrayInputStream(new byte[0]);
			}

			@Override
			public void close() {
				delegateClosed.set(true);
			}
		};
		var transport = new LifecycleHttpTransport(request -> call(
				CompletableFuture.completedFuture(response), new AtomicBoolean()));

		TransportResponse owned = transport.execute(request()).response().get();
		assertEquals(1, transport.activeCallCount());

		owned.close();

		assertEquals(0, transport.activeCallCount());
		assertTrue(delegateClosed.get());
		transport.close();
	}

	private static TransportCall call(
			CompletableFuture<TransportResponse> response, AtomicBoolean cancelled) {
		return new TransportCall() {
			@Override
			public CompletableFuture<TransportResponse> response() {
				return response;
			}

			@Override
			public void cancel() {
				cancelled.set(true);
			}
		};
	}

	private static TransportRequest request() throws Exception {
		URI uri = URI.create("https://provider.invalid/manifest.json");
		NormalizedEndpoint endpoint = new NormalizedEndpoint(uri, "https",
				"provider.invalid", 443, "https://provider.invalid");
		InetAddress address = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
		return new TransportRequest(new ValidatedEndpoint(endpoint, address,
				AddressKind.PUBLIC, java.util.List.of(address)), Map.of(),
				HttpDeadlines.DEFAULT, 1024);
	}
}
