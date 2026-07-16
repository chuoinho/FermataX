package me.aap.fermata.addon.podcast.data;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
public class PodcastArtworkCacheTest {
	private static final byte[] PNG = new byte[] {
			(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0
	};
	private File directory;

	@Before
	public void createDirectory() throws Exception {
		directory = Files.createTempDirectory("podcast-artwork-test").toFile();
	}

	@After
	public void removeDirectory() throws Exception {
		try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(directory.toPath())) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
	}

	@Test
	public void privateArtworkUsesAuthorizationAndReturnsSecretFreeLocalUri() throws Exception {
		FakeClient client = new FakeClient(PNG, "image/png");
		PodcastArtworkCache cache = new PodcastArtworkCache(directory, client);
		PodcastPlaybackSource source = new PodcastPlaybackSource(
				"https://example.test/private.png", "Basic very-secret-value");

		String first = cache.resolve(source).getOrThrow();
		String second = cache.resolve(source).getOrThrow();

		assertEquals(first, second);
		assertEquals(1, client.requests);
		assertEquals("Basic very-secret-value", client.lastRequest.getAuthorization());
		assertFalse(first.contains("very-secret-value"));
		assertTrue(first.startsWith("file:"));
		File[] images = directory.listFiles((dir, name) -> name.endsWith(".img"));
		assertEquals(1, images == null ? 0 : images.length);
		assertArrayEquals(PNG, Files.readAllBytes(images[0].toPath()));
	}

	@Test
	public void publicArtworkKeepsExistingBitmapCachePath() throws Exception {
		FakeClient client = new FakeClient(PNG, "image/png");
		PodcastArtworkCache cache = new PodcastArtworkCache(directory, client);
		String url = "https://example.test/public.png";

		assertEquals(url, cache.resolve(new PodcastPlaybackSource(url, null)).getOrThrow());
		assertEquals(0, client.requests);
	}

	private static final class FakeClient extends PodcastHttpClient {
		private final byte[] body;
		private final String contentType;
		int requests;
		DocumentRequest lastRequest;

		FakeClient(byte[] body, String contentType) {
			this.body = body;
			this.contentType = contentType;
		}

		@Override
		public <T> FutureSupplier<DocumentResponse<T>> getDocument(DocumentRequest request,
				DocumentParser<T> parser) {
			requests++;
			lastRequest = request;
			try {
				T result = parser.parse(new ByteArrayInputStream(body), contentType,
						request.getUrl());
				return completed(new DocumentResponse<>(200, request.getUrl(), contentType,
						null, null, result));
			} catch (Throwable error) {
				return failed(error);
			}
		}
	}
}
