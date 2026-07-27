package me.aap.fermata.addon.stremio.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.http.HttpTransport;
import me.aap.fermata.addon.stremio.net.http.TransportCall;
import me.aap.fermata.addon.stremio.net.http.TransportResponse;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.source.StremioSecretReference;
import me.aap.fermata.addon.stremio.source.StremioSourceInput;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;
import me.aap.fermata.addon.stremio.source.StremioSourceSecretVault;

@RunWith(RobolectricTestRunner.class)
public class StremioProductionWiringTest {
	private static final String ADDON_ID = "org.test.production-wiring";
	private static final String TRANSPORT_SECRET = "member-secret";
	private static final String CONFIGURATION_TOKEN = "configuration-token";
	private static final String MANIFEST = """
			{
			  "id":"org.test.production-wiring",
			  "name":"Production wiring",
			  "description":"Fixture",
			  "version":"1.0.0",
			  "types":["movie"],
			  "resources":["catalog"],
			  "catalogs":[{"type":"movie","id":"top","name":"Top"}]
			}
			""";
	private static final String CATALOG = """
			{"metas":[{"id":"tt1","type":"movie","name":"One"}]}
			""";

	private File directory;
	private StremioRuntime runtime;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("stremio-production-wiring").toFile();
	}

	@After
	public void tearDown() throws Exception {
		if (runtime != null) runtime.closeAsync().get(2, TimeUnit.SECONDS);
		delete(directory);
	}

	@Test
	public void persistedSourceLoadsSameVaultSecretForRuntimeGraphCatalogRequest()
			throws Exception {
		RecordingVault vault = new RecordingVault();
		RecordingTransport transport = new RecordingTransport();
		runtime = StremioRuntimeFactory.openForTest(directory, NetworkConsent.STRICT,
				transport, host -> java.util.List.of(
						InetAddress.getByAddress(new byte[]{8, 8, 8, 8})), vault)
				.get(5, TimeUnit.SECONDS);

		var outcome = runtime.sources().add(new StremioSourceInput(
				"stremio://provider.invalid/" + TRANSPORT_SECRET + "/manifest.json",
				CONFIGURATION_TOKEN)).get(5, TimeUnit.SECONDS);
		assertEquals(Status.CHANGED, outcome.status());

		var persisted = runtime.repository().getSourceState().get(5, TimeUnit.SECONDS);
		assertEquals(1, persisted.sources().size());
		StremioSourceRecord source = persisted.sources().get(0);
		String secretId = StremioSecretReference.resolve(source);
		assertEquals(vault.savedId.get(), secretId);
		assertEquals(CONFIGURATION_TOKEN,
				vault.values.get(secretId).configurationToken());

		var page = runtime.graph().items().catalog(
				new CatalogRoute(source.sourceUuid(), "movie", "top"), null, 0)
				.get(5, TimeUnit.SECONDS);

		assertEquals(secretId, vault.loadedId.get());
		assertEquals(1, vault.loadCount.get());
		assertEquals(1, page.items().size());
		assertEquals("One", page.items().get(0).title());
		assertNotNull(transport.catalogUri.get());
		assertEquals("/" + TRANSPORT_SECRET + "/catalog/movie/top.json",
				transport.catalogUri.get().getRawPath());
		assertTrue(transport.requestCount.get() >= 2);
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

	private static final class RecordingTransport implements HttpTransport {
		private final AtomicInteger requestCount = new AtomicInteger();
		private final AtomicReference<URI> catalogUri = new AtomicReference<>();

		@Override
		public TransportCall execute(
				me.aap.fermata.addon.stremio.net.http.TransportRequest request) {
			requestCount.incrementAndGet();
			URI uri = request.endpoint().endpoint().uri();
			String path = uri.getRawPath();
			byte[] body;
			if (path.endsWith("/manifest.json")) {
				body = MANIFEST.getBytes(StandardCharsets.UTF_8);
			} else if (path.endsWith("/catalog/movie/top.json")) {
				catalogUri.set(uri);
				body = CATALOG.getBytes(StandardCharsets.UTF_8);
			} else {
				throw new AssertionError("Unexpected Stremio request: " + uri);
			}
			return completedTransport(new Response(200, Map.of(), body));
		}
	}

	private static final class RecordingVault implements StremioSourceSecretVault {
		private final Map<String, StremioSourceSecret> values = new ConcurrentHashMap<>();
		private final AtomicReference<String> savedId = new AtomicReference<>();
		private final AtomicReference<String> loadedId = new AtomicReference<>();
		private final AtomicInteger loadCount = new AtomicInteger();

		@Override
		public CompletableFuture<StremioSourceSecret> load(String secretId) {
			loadedId.set(secretId);
			loadCount.incrementAndGet();
			return CompletableFuture.completedFuture(values.get(secretId));
		}

		@Override
		public CompletableFuture<Void> save(String secretId, StremioSourceSecret secret) {
			savedId.set(secretId);
			values.put(secretId, secret);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> remove(String secretId) {
			values.remove(secretId);
			return CompletableFuture.completedFuture(null);
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
