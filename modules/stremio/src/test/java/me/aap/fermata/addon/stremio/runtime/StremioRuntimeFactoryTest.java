package me.aap.fermata.addon.stremio.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.http.HttpTransport;
import me.aap.fermata.addon.stremio.net.http.TransportCall;
import me.aap.fermata.addon.stremio.net.http.TransportRequest;
import me.aap.fermata.addon.stremio.net.http.TransportResponse;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.source.StremioSourceInput;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;
import me.aap.fermata.addon.stremio.source.StremioSourceSecretVault;

@RunWith(RobolectricTestRunner.class)
public class StremioRuntimeFactoryTest {
	private static final String MANIFEST = "{\"id\":\"org.test.runtime\"," +
			"\"name\":\"Runtime\",\"description\":\"Fixture\",\"version\":\"1.0.0\"," +
			"\"types\":[\"movie\"],\"resources\":[\"catalog\"],\"catalogs\":[]}";
	private File directory;
	private StremioRuntime runtime;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("stremio-runtime").toFile();
	}

	@Test
	public void productionJsonCacheUsesBoundedAaFriendlyBudget() {
		assertEquals(12L * 1024L * 1024L, StremioRuntimeFactory.JSON_CACHE_BYTES);
		assertEquals(1L * 1024L * 1024L, StremioRuntimeFactory.JSON_CACHE_ENTRY_BYTES);
	}

	@After
	public void tearDown() throws Exception {
		if (runtime != null) runtime.closeAsync().get(2, TimeUnit.SECONDS);
		delete(directory);
	}

	@Test
	public void factoryWiresSourceManagerRepositoryAndNetworkOffCallerThread() throws Exception {
		String callerThread = Thread.currentThread().getName();
		AtomicReference<String> resolverThread = new AtomicReference<>();
		AtomicReference<String> transportThread = new AtomicReference<>();
		FakeVault vault = new FakeVault();
		HttpTransport transport = request -> {
			transportThread.set(Thread.currentThread().getName());
			return completedTransport(new Response(200, Map.of("etag", "runtime-etag"),
					MANIFEST.getBytes(StandardCharsets.UTF_8)));
		};
		runtime = StremioRuntimeFactory.openForTest(directory, NetworkConsent.STRICT,
				transport, host -> {
					resolverThread.set(Thread.currentThread().getName());
					return java.util.List.of(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}));
				}, vault).get(5, TimeUnit.SECONDS);

		var outcome = runtime.sources().add(new StremioSourceInput(
				"stremio://provider.invalid/manifest.json", null)).get(5, TimeUnit.SECONDS);

		assertEquals(outcome.toString(), Status.CHANGED, outcome.status());
		assertEquals(1, runtime.sources().sources().get(2, TimeUnit.SECONDS).sources().size());
		assertEquals(1, runtime.repository().getSourceState().get(2, TimeUnit.SECONDS).sources().size());
		assertNotEquals(callerThread, resolverThread.get());
		assertNotEquals(callerThread, transportThread.get());
		assertFalse(vault.values.isEmpty());
	}

	@Test
	public void closeIsIdempotentAndStopsSourceAndNetworkWork() throws Exception {
		FakeVault vault = new FakeVault();
		runtime = StremioRuntimeFactory.openForTest(directory, NetworkConsent.STRICT,
				request -> completedTransport(new Response(200, Map.of(),
						MANIFEST.getBytes(StandardCharsets.UTF_8))),
				host -> java.util.List.of(InetAddress.getByAddress(new byte[]{8, 8, 8, 8})),
				vault).get(5, TimeUnit.SECONDS);

		CompletableFuture<Void> first = runtime.closeAsync();
		CompletableFuture<Void> second = runtime.closeAsync();
		first.get(2, TimeUnit.SECONDS);

		assertTrue(runtime.isClosed());
		assertTrue(first == second);
		assertTrue(runtime.sourceManager().sources().isCompletedExceptionally());
	}

	@Test
	public void closeThenOpenCreatesIndependentUsableRuntimeGeneration() throws Exception {
		HttpTransport transport = request -> completedTransport(new Response(200, Map.of(),
				MANIFEST.getBytes(StandardCharsets.UTF_8)));
		var resolver = (me.aap.fermata.addon.stremio.net.AddressResolver) host ->
				java.util.List.of(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}));
		StremioRuntime first = StremioRuntimeFactory.openForTest(directory,
				NetworkConsent.STRICT, transport, resolver, new FakeVault())
				.get(5, TimeUnit.SECONDS);
		first.closeAsync().get(2, TimeUnit.SECONDS);

		StremioRuntime second = StremioRuntimeFactory.openForTest(directory,
				NetworkConsent.STRICT, transport, resolver, new FakeVault())
				.get(5, TimeUnit.SECONDS);
		runtime = second;

		assertTrue(first.isClosed());
		assertTrue(first.graph().isClosed());
		assertFalse(second.isClosed());
		assertFalse(second.graph().isClosed());
		assertTrue(first != second);
	}

	private static TransportCall completedTransport(TransportResponse response) {
		return new TransportCall() {
			@Override
			public CompletableFuture<TransportResponse> response() {
				return CompletableFuture.completedFuture(response);
			}

			@Override
			public void cancel() {
			}
		};
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		file.delete();
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

	private static final class FakeVault implements StremioSourceSecretVault {
		private final Map<String, StremioSourceSecret> values = new ConcurrentHashMap<>();

		@Override
		public CompletableFuture<StremioSourceSecret> load(String sourceUuid) {
			return CompletableFuture.completedFuture(values.get(sourceUuid));
		}

		@Override
		public CompletableFuture<Void> save(String sourceUuid, StremioSourceSecret secret) {
			values.put(sourceUuid, secret);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> remove(String sourceUuid) {
			values.remove(sourceUuid);
			return CompletableFuture.completedFuture(null);
		}
	}
}
