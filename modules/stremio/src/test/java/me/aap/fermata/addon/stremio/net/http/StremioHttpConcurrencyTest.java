package me.aap.fermata.addon.stremio.net.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.stremio.net.NetworkConsent;

public class StremioHttpConcurrencyTest {
	private ScheduledExecutorService scheduler;
	private ExecutorService io;
	private HoldingTransport transport;
	private StremioHttpClient client;

	@Before
	public void setUp() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		io = Executors.newFixedThreadPool(4);
		transport = new HoldingTransport();
		client = client(8, 4);
	}

	@After
	public void tearDown() {
		scheduler.shutdownNow();
		io.shutdownNow();
	}

	@Test
	public void enforcesGlobalAndPerHostCapsWithoutBlockingWorkers() {
		List<HttpCall> calls = new ArrayList<>();
		for (int i = 0; i < 6; i++) calls.add(execute("https://same.example.invalid/" + i));
		for (int i = 0; i < 6; i++) calls.add(execute("https://host" + i + ".example.invalid/item"));

		await(() -> transport.active.get() == 8);
		assertEquals(8, transport.maxActive.get());
		assertEquals(4, transport.maxForHost("same.example.invalid"));
		assertEquals(4, transport.activeForHost("same.example.invalid"));

		transport.completeFirst("host0.example.invalid", response(200));
		await(() -> transport.started.get() == 9);
		assertEquals(8, transport.maxActive.get());

		transport.completeFirst("same.example.invalid", response(200));
		await(() -> transport.started.get() == 10);
		assertEquals(4, transport.maxForHost("same.example.invalid"));

		calls.forEach(HttpCall::cancel);
	}

	@Test
	public void queuedCancellationNeverStartsAndDoesNotLeakPermit() throws Exception {
		client = client(1, 1);
		HttpCall active = execute("https://one.example.invalid/active");
		await(() -> transport.started.get() == 1);
		HttpCall cancelled = execute("https://one.example.invalid/cancelled");

		cancelled.cancel();
		assertEquals(HttpFailure.Code.CANCELLED, failure(cancelled).code());
		transport.completeFirst("one.example.invalid", response(200));
		active.response().get(2, TimeUnit.SECONDS);
		Thread.sleep(30);
		assertEquals(1, transport.started.get());

		HttpCall next = execute("https://one.example.invalid/next");
		await(() -> transport.started.get() == 2);
		transport.completeFirst("one.example.invalid", response(200));
		next.response().get(2, TimeUnit.SECONDS);
	}

	@Test
	public void transportErrorReleasesPermitToQueuedCall() throws Exception {
		client = client(1, 1);
		HttpCall failed = execute("https://one.example.invalid/fail");
		await(() -> transport.started.get() == 1);
		HttpCall queued = execute("https://one.example.invalid/queued");

		transport.failFirst("one.example.invalid", new java.io.IOException("fixture failure"));
		assertEquals(HttpFailure.Code.TRANSPORT, failure(failed).code());
		await(() -> transport.started.get() == 2);
		transport.completeFirst("one.example.invalid", response(200));
		queued.response().get(2, TimeUnit.SECONDS);
	}

	@Test
	public void redirectTransfersHostPermitAndReleasesGlobalPermitAtCompletion() throws Exception {
		client = client(1, 1);
		HttpCall redirected = execute("https://one.example.invalid/start");
		await(() -> transport.started.get() == 1);
		HttpCall queued = execute("https://three.example.invalid/queued");

		transport.completeFirst("one.example.invalid", new Response(302,
				Map.of("Location", "https://two.example.invalid/final"), new byte[0]));
		await(() -> transport.started.get() == 2);
		assertEquals("two.example.invalid", transport.calls.get(1).host);
		assertEquals(1, transport.maxActive.get());
		transport.completeFirst("two.example.invalid", response(200));
		redirected.response().get(2, TimeUnit.SECONDS);

		await(() -> transport.started.get() == 3);
		assertEquals("three.example.invalid", transport.calls.get(2).host);
		transport.completeFirst("three.example.invalid", response(200));
		queued.response().get(2, TimeUnit.SECONDS);
		assertEquals(1, transport.maxActive.get());
	}

	private StremioHttpClient client(int global, int perHost) {
		return new StremioHttpClient(host -> List.of(address("8.8.8.8")), transport,
				scheduler, io, new HttpConcurrencyGate(global, perHost));
	}

	private HttpCall execute(String uri) {
		return client.execute(new HttpRequestSpec(URI.create(uri), Map.of(), 1024,
				NetworkConsent.STRICT, HttpDeadlines.DEFAULT, null));
	}

	private static Response response(int status) {
		return new Response(status, Map.of(), "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static HttpFailure failure(HttpCall call) {
		ExecutionException error = assertThrows(ExecutionException.class,
				() -> call.response().get(2, TimeUnit.SECONDS));
		assertTrue(error.getCause() instanceof HttpFailure);
		return (HttpFailure) error.getCause();
	}

	private static void await(java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= deadline) throw new AssertionError("Condition timed out");
			try {
				Thread.sleep(5);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError(ex);
			}
		}
	}

	private static InetAddress address(String value) {
		try {
			return InetAddress.getByName(value);
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	private static final class HoldingTransport implements HttpTransport {
		private final CopyOnWriteArrayList<PendingCall> calls = new CopyOnWriteArrayList<>();
		private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> activeByHost =
				new java.util.concurrent.ConcurrentHashMap<>();
		private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> maxByHost =
				new java.util.concurrent.ConcurrentHashMap<>();
		private final AtomicInteger active = new AtomicInteger();
		private final AtomicInteger maxActive = new AtomicInteger();
		private final AtomicInteger started = new AtomicInteger();

		@Override
		public TransportCall execute(TransportRequest request) {
			String host = request.endpoint().endpoint().host();
			int total = active.incrementAndGet();
			maxActive.accumulateAndGet(total, Math::max);
			int hostTotal = activeByHost.computeIfAbsent(host, ignored -> new AtomicInteger())
					.incrementAndGet();
			maxByHost.computeIfAbsent(host, ignored -> new AtomicInteger())
					.accumulateAndGet(hostTotal, Math::max);
			PendingCall call = new PendingCall(host, this);
			calls.add(call);
			started.incrementAndGet();
			return call;
		}

		private int activeForHost(String host) {
			AtomicInteger count = activeByHost.get(host);
			return (count == null) ? 0 : count.get();
		}

		private int maxForHost(String host) {
			AtomicInteger count = maxByHost.get(host);
			return (count == null) ? 0 : count.get();
		}

		private void completeFirst(String host, TransportResponse response) {
			pending(host).finish(response, null);
		}

		private void failFirst(String host, Throwable error) {
			pending(host).finish(null, error);
		}

		private PendingCall pending(String host) {
			return calls.stream().filter(call -> call.host.equals(host) && !call.finished.get())
					.findFirst().orElseThrow();
		}

		private void finished(String host) {
			active.decrementAndGet();
			activeByHost.get(host).decrementAndGet();
		}
	}

	private static final class PendingCall implements TransportCall {
		private final String host;
		private final HoldingTransport owner;
		private final CompletableFuture<TransportResponse> response = new CompletableFuture<>();
		private final AtomicBoolean finished = new AtomicBoolean();

		private PendingCall(String host, HoldingTransport owner) {
			this.host = host;
			this.owner = owner;
		}

		@Override
		public CompletableFuture<TransportResponse> response() {
			return response;
		}

		@Override
		public void cancel() {
			finish(null, new java.util.concurrent.CancellationException("cancelled"));
		}

		private void finish(TransportResponse value, Throwable error) {
			if (!finished.compareAndSet(false, true)) return;
			owner.finished(host);
			if (error == null) response.complete(value);
			else response.completeExceptionally(error);
		}
	}

	private record Response(int status, Map<String, String> headers, byte[] bytes)
			implements TransportResponse {
		@Override
		public InputStream body() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public void close() {
		}
	}
}
