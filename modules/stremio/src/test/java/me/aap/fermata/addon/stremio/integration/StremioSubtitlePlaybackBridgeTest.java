package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.cache.CacheKey;
import me.aap.fermata.addon.stremio.net.cache.CachePolicy;
import me.aap.fermata.addon.stremio.net.cache.CachedCall;
import me.aap.fermata.addon.stremio.net.cache.CachedResponse;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregator;
import me.aap.fermata.addon.stremio.subtitle.SubtitleCandidate;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.addon.stremio.subtitle.SubtitleFormat;
import me.aap.fermata.addon.stremio.subtitle.SubtitleLanguageNormalizer;

public class StremioSubtitlePlaybackBridgeTest {
	private static final String SOURCE = "11111111-1111-4111-8111-111111111111";
	private static final byte[] BODY = ("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n").getBytes(
			java.nio.charset.StandardCharsets.UTF_8);

	@Test
	public void subtitleRankingUsesCurrentApplicationLocale() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("vi-VN"));
			assertEquals(List.of("vi-VN", "vi", "en"),
					StremioSubtitlePlaybackBridge.preferredLanguages());
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	public void loadsThroughBoundedCachedRuntimeWithoutPersistingRawUrlInKey() throws Exception {
		FakeRuntime runtime = new FakeRuntime();
		SubtitleDescriptor descriptor = descriptor(SubtitleFormat.WEBVTT,
				Instant.now().plusSeconds(60));

		byte[] loaded = StremioSubtitlePlaybackBridge.load(runtime, descriptor).get();

		assertArrayEquals(BODY, loaded);
		assertEquals(SubtitleAggregator.MAX_SUBTITLE_FILE_BYTES, runtime.maxBodyBytes);
		assertEquals("subtitle-file", runtime.key.resource());
		assertTrue(runtime.headers.get("accept").contains("text/vtt"));
		assertTrue(runtime.key.toString().indexOf("sub.example") < 0);
		assertEquals(1, runtime.fetches);
		assertEquals(new NetworkConsent(true, true), runtime.consent);
	}

	@Test
	public void rejectsExpiredAndUnsupportedSidecarsBeforeNetwork() {
		FakeRuntime runtime = new FakeRuntime();
		assertThrows(Exception.class, () -> StremioSubtitlePlaybackBridge.load(runtime,
				descriptor(SubtitleFormat.WEBVTT, Instant.now().minusSeconds(1))).get());
		assertThrows(Exception.class, () -> StremioSubtitlePlaybackBridge.load(runtime,
				descriptor(SubtitleFormat.MICRODVD, Instant.now().plusSeconds(60))).get());
		assertEquals(0, runtime.fetches);
	}

	@Test
	public void loadsExtensionlessTextSubtitleDownloads() throws Exception {
		FakeRuntime runtime = new FakeRuntime();
		SubtitleDescriptor descriptor = new SubtitleDescriptor("opaque-subtitle", "en",
				URI.create("https://subs.example.invalid/download/file/123"), "eng",
				SubtitleLanguageNormalizer.normalize("eng"), SOURCE, "Fixture",
				SubtitleCandidate.Source.PROVIDER, SubtitleFormat.UNKNOWN,
				SubtitleDescriptor.Status.READY, BODY.length, null,
				Instant.now().plusSeconds(60));

		assertArrayEquals(BODY, StremioSubtitlePlaybackBridge.load(runtime, descriptor).get());
		assertEquals(1, runtime.fetches);
	}

	@Test
	public void normalizesCompressedAndBomEncodedSubtitlePayloads() throws Exception {
		byte[] text = "WEBVTT\n\n00:01.000 --> 00:02.000\nHello\n"
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
			gzip.write(text);
		}
		assertArrayEquals(text,
				StremioSubtitlePlaybackBridge.normalizePayload(compressed.toByteArray()));

		byte[] utf16 = "WEBVTT\n".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
		byte[] encoded = new byte[utf16.length + 2];
		encoded[0] = (byte) 0xff;
		encoded[1] = (byte) 0xfe;
		System.arraycopy(utf16, 0, encoded, 2, utf16.length);
		assertArrayEquals("WEBVTT\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),
				StremioSubtitlePlaybackBridge.normalizePayload(encoded));
	}

	@Test
	public void editOrDisableRevokesPendingSubtitleAndPartitionsCache() throws Exception {
		FakeRuntime runtime = new FakeRuntime();
		StremioSourceLease firstLease = StremioSourceLease.bound(
				runtime.snapshot.revision(), runtime.snapshot.source(SOURCE),
				() -> runtime.snapshot);
		runtime.pending = true;
		var pending = StremioSubtitlePlaybackBridge.load(runtime,
				descriptor(SubtitleFormat.WEBVTT, Instant.now().plusSeconds(60), firstLease));
		while (runtime.fetches == 0) Thread.yield();
		CacheKey firstKey = runtime.key;

		runtime.publish(snapshot(1, source("edited-fingerprint", true,
				new NetworkConsent(true, false), 2L)));

		assertThrows(Exception.class, pending::get);
		assertTrue(runtime.cancelled);
		assertFalse(runtime.validity.getAsBoolean());

		runtime.pending = false;
		runtime.cancelled = false;
		StremioSourceLease secondLease = StremioSourceLease.bound(
				runtime.snapshot.revision(), runtime.snapshot.source(SOURCE),
				() -> runtime.snapshot);
		StremioSubtitlePlaybackBridge.load(runtime,
				descriptor(SubtitleFormat.WEBVTT, Instant.now().plusSeconds(60), secondLease)).get();

		assertNotEquals(firstKey, runtime.key);
		assertEquals(new NetworkConsent(true, false), runtime.consent);

		StremioSourceLease disabledLease = secondLease;
		int fetchesBeforeDisable = runtime.fetches;
		runtime.publish(snapshot(2, source("edited-fingerprint", false,
				new NetworkConsent(true, false), 2L)));
		assertThrows(Exception.class, () -> StremioSubtitlePlaybackBridge.load(runtime,
				descriptor(SubtitleFormat.WEBVTT, Instant.now().plusSeconds(60),
						disabledLease)).get());
		assertEquals(fetchesBeforeDisable, runtime.fetches);
	}

	private static SubtitleDescriptor descriptor(SubtitleFormat format, Instant expiresAt) {
		return new SubtitleDescriptor("opaque-subtitle", "en",
				URI.create("https://sub.example.invalid/private/video.vtt"), "eng",
				SubtitleLanguageNormalizer.normalize("eng"), SOURCE, "Fixture",
				SubtitleCandidate.Source.PROVIDER, format, SubtitleDescriptor.Status.READY,
				BODY.length, null, expiresAt);
	}

	private static SubtitleDescriptor descriptor(SubtitleFormat format, Instant expiresAt,
			StremioSourceLease lease) {
		return new SubtitleDescriptor("opaque-subtitle", "en",
				URI.create("https://sub.example.invalid/private/video.vtt"), "eng",
				SubtitleLanguageNormalizer.normalize("eng"), SOURCE, "Fixture",
				SubtitleCandidate.Source.PROVIDER, format, SubtitleDescriptor.Status.READY,
				BODY.length, null, lease, expiresAt);
	}

	private static StremioSourceSnapshot snapshot(long revision, StremioSourceRecord source) {
		return new StremioSourceSnapshot(revision, List.of(source), true);
	}

	private static StremioSourceRecord source(String fingerprint, boolean enabled,
			NetworkConsent consent, long updatedMs) {
		return new StremioSourceRecord(SOURCE, fingerprint, "fixture", "Fixture", "1.0",
				"https://provider.example.invalid/manifest.json", "secure:" + SOURCE, enabled, 0,
				"{\"id\":\"fixture\"}", null, null, 0, 0, null, 0, updatedMs,
				consent.allowCleartext(), consent.allowLan());
	}

	private static final class FakeRuntime implements StremioRuntimeAccess {
		private final CopyOnWriteArrayList<Consumer<StremioSourceSnapshot>> observers =
				new CopyOnWriteArrayList<>();
		private CacheKey key;
		private Map<String, String> headers;
		private long maxBodyBytes;
		private int fetches;
		private NetworkConsent consent;
		private volatile StremioSourceSnapshot snapshot = snapshot(0,
				source("fingerprint", true, new NetworkConsent(true, true), 0L));
		private volatile boolean pending;
		private volatile boolean cancelled;
		private volatile BooleanSupplier validity = () -> true;
		private volatile CompletableFuture<CachedResponse> response;

		@Override
		public CompletableFuture<StremioSourceSnapshot> sources() {
			return CompletableFuture.completedFuture(snapshot);
		}

		@Override
		public AutoCloseable observeSources(Consumer<StremioSourceSnapshot> observer) {
			observers.add(observer);
			return () -> observers.remove(observer);
		}

		private void publish(StremioSourceSnapshot value) {
			snapshot = value;
			for (Consumer<StremioSourceSnapshot> observer : observers) observer.accept(value);
		}

		@Override
		public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
				long maxBodyBytes, CachePolicy policy, NetworkConsent consent) {
			return fetch(key, uri, headers, maxBodyBytes, policy, consent, () -> true);
		}

		@Override
		public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
				long maxBodyBytes, CachePolicy policy, NetworkConsent consent,
				BooleanSupplier validity) {
			this.key = key;
			this.headers = headers;
			this.maxBodyBytes = maxBodyBytes;
			this.consent = consent;
			this.validity = validity;
			fetches++;
			response = pending ? new CompletableFuture<>() : CompletableFuture.completedFuture(
					new CachedResponse(BODY, CachedResponse.Origin.NETWORK));
			return new CachedCall() {
				@Override
				public CompletableFuture<CachedResponse> response() {
					return response;
				}

				@Override
				public void cancel() {
					cancelled = true;
					response.completeExceptionally(new java.util.concurrent.CancellationException());
				}
			};
		}
	}
}
