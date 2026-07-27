package me.aap.fermata.addon.stremio.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.RequestGeneration;

public class SubtitleAggregatorTest {
	private static final Instant EXPIRY = Instant.parse("2030-01-01T00:00:00Z");

	@Test
	public void mergesEmbeddedAndDelayedProvidersWithPartialFailure() {
		var delayed = new CompletableFuture<List<SubtitleCandidate>>();
		var generation = new RequestGeneration();
		var call = new SubtitleAggregator().aggregate(
				List.of(candidate("embedded", "https://sub.example.invalid/en.srt", "eng",
						SubtitleCandidate.Source.STREAM_EMBEDDED)),
				List.of(provider("delayed", delayed), failingProvider("failed")),
				List.of("vi", "en"), generation.begin());

		assertFalse(call.result().isDone());
		delayed.complete(List.of(candidate("vi", "https://sub.example.invalid/vi.vtt", "vie",
				SubtitleCandidate.Source.PROVIDER)));
		var result = call.result().join();

		assertEquals(2, result.subtitles().size());
		assertEquals("vi", result.subtitles().get(0).language().tag());
		assertEquals("en", result.subtitles().get(1).language().tag());
		assertEquals(1, result.failures().size());
		assertEquals(SubtitleProviderFailure.Code.PROVIDER_FAILED,
				result.failures().get(0).code());
	}

	@Test
	public void staleGenerationCancelsDelayedAggregation() {
		var delayed = new CompletableFuture<List<SubtitleCandidate>>();
		var generation = new RequestGeneration();
		var call = new SubtitleAggregator().aggregate(List.of(),
				List.of(provider("slow", delayed)), List.of("en"), generation.begin());

		generation.begin();
		delayed.complete(List.of(candidate("late", "https://sub.example.invalid/late.srt", "en",
				SubtitleCandidate.Source.PROVIDER)));

		assertThrows(CancellationException.class, call.result()::join);
	}

	@Test
	public void explicitCancellationStopsEveryActiveProvider() {
		var generation = new RequestGeneration();
		var firstCancelled = new AtomicBoolean();
		var secondCancelled = new AtomicBoolean();
		var call = new SubtitleAggregator().aggregate(List.of(), List.of(
				provider("first", new CompletableFuture<>(), firstCancelled),
				provider("second", new CompletableFuture<>(), secondCancelled)),
				List.of("en"), generation.begin());

		call.cancel();

		assertTrue(firstCancelled.get());
		assertTrue(secondCancelled.get());
		assertThrows(CancellationException.class, call.result()::join);
	}

	@Test
	public void removingProviderCancelsOnlyThatProviderAndCompletesOthers() {
		var removedFuture = new CompletableFuture<List<SubtitleCandidate>>();
		var removedCancelled = new AtomicBoolean();
		var generation = new RequestGeneration();
		var removed = provider("removed", removedFuture, removedCancelled);
		var ready = provider("ready", CompletableFuture.completedFuture(List.of(
				candidate("ready", "https://sub.example.invalid/ready.srt", "en",
						SubtitleCandidate.Source.PROVIDER))));
		var call = new SubtitleAggregator(1).aggregate(List.of(), List.of(removed, ready),
				List.of("en"), generation.begin());

		call.removeProvider("removed");
		var result = call.result().join();

		assertTrue(removedCancelled.get());
		assertEquals(1, result.subtitles().size());
		assertEquals(SubtitleProviderFailure.Code.PROVIDER_REMOVED,
				result.failures().get(0).code());
	}

	@Test
	public void deduplicatesByNormalizedLanguageAndCanonicalSecretSafeUrlIdentity() {
		var generation = new RequestGeneration();
		var first = candidate("one",
				"HTTPS://SUB.EXAMPLE.INVALID:443/path/../movie.srt?token=secret", "eng",
				SubtitleCandidate.Source.STREAM_EMBEDDED);
		var second = candidate("two", "https://sub.example.invalid/movie.srt?token=secret", "en",
				SubtitleCandidate.Source.PROVIDER);
		var result = new SubtitleAggregator().aggregate(List.of(first, second), List.of(),
				List.of("en"), generation.begin()).result().join();

		assertEquals(1, result.subtitles().size());
		String text = result.subtitles().get(0).toString() + first +
				new OpaqueHeaderReference("headers_1");
		assertFalse(text.contains("secret"));
		assertFalse(text.contains("SUB.EXAMPLE"));
		assertFalse(text.contains("headers_1"));
	}

	@Test
	public void exposesUnsupportedAndOversizedStatesWithoutDroppingRows() {
		var generation = new RequestGeneration();
		var unsupported = candidate("sub", "https://sub.example.invalid/movie.sub", "en",
				SubtitleCandidate.Source.PROVIDER);
		var oversized = new SubtitleCandidate("big", "https://sub.example.invalid/big.srt", "en",
				"provider", "Provider", SubtitleCandidate.Source.PROVIDER, null,
				SubtitleAggregator.MAX_SUBTITLE_FILE_BYTES + 1, null, EXPIRY);
		var result = new SubtitleAggregator().aggregate(List.of(unsupported, oversized), List.of(),
				List.of("en"), generation.begin()).result().join();

		assertEquals(SubtitleDescriptor.Status.FILE_TOO_LARGE,
				result.subtitles().stream().filter(s -> "big".equals(s.subtitleId()))
						.findFirst().orElseThrow().status());
		assertEquals(SubtitleDescriptor.Status.UNSUPPORTED_FORMAT,
				result.subtitles().stream().filter(s -> "sub".equals(s.subtitleId()))
						.findFirst().orElseThrow().status());
	}

	@Test
	public void extensionlessHttpSubtitleEndpointIsReadyForLazyEngineLoading() {
		var generation = new RequestGeneration();
		var result = new SubtitleAggregator().aggregate(List.of(candidate("extensionless",
				"https://sub.example.invalid/download/file/123", "en",
				SubtitleCandidate.Source.PROVIDER)), List.of(), List.of("en"),
				generation.begin()).result().join();

		assertEquals(1, result.subtitles().size());
		assertEquals(SubtitleFormat.UNKNOWN, result.subtitles().get(0).format());
		assertEquals(SubtitleDescriptor.Status.READY, result.subtitles().get(0).status());
	}

	@Test
	public void rankingIsExactThenBaseThenUnknownThenOtherAndDeterministic() {
		var generation = new RequestGeneration();
		var subtitles = List.of(
				candidate("other", "https://sub.example.invalid/fr.srt", "fr", SubtitleCandidate.Source.PROVIDER),
				candidate("unknown", "https://sub.example.invalid/und.srt", "???", SubtitleCandidate.Source.PROVIDER),
				candidate("base", "https://sub.example.invalid/en.srt", "en", SubtitleCandidate.Source.PROVIDER),
				candidate("exact", "https://sub.example.invalid/us.srt", "en-US", SubtitleCandidate.Source.PROVIDER));
		var result = new SubtitleAggregator().aggregate(subtitles, List.of(), List.of("en-US"),
				generation.begin()).result().join();

		assertEquals(List.of("en-US", "en", "und", "fr"), result.subtitles().stream()
				.map(s -> s.language().tag()).toList());
	}

	@Test
	public void configuredLanguageFiltersTracksBeforeTheyReachThePlayer() {
		var generation = new RequestGeneration();
		var aggregated = new SubtitleAggregator().aggregate(List.of(
				candidate("vi", "https://sub.example.invalid/vi.srt", "vi",
						SubtitleCandidate.Source.PROVIDER),
				candidate("en", "https://sub.example.invalid/en.srt", "en",
						SubtitleCandidate.Source.PROVIDER)), List.of(), List.of("vi"),
				generation.begin()).result().join();

		var filtered = SubtitleLanguageFilter.apply(aggregated, List.of("vi-VN"));

		assertEquals(1, filtered.subtitles().size());
		assertEquals("vi", filtered.subtitles().get(0).language().tag());
	}

	@Test
	public void enforcesFourProviderConcurrencyAndSubtitleCountLimit() {
		var generation = new RequestGeneration();
		var active = new AtomicInteger();
		var peak = new AtomicInteger();
		var futures = new ArrayList<CompletableFuture<List<SubtitleCandidate>>>();
		var providers = new ArrayList<SubtitleProvider>();
		for (int i = 0; i < 6; i++) {
			var future = new CompletableFuture<List<SubtitleCandidate>>();
			futures.add(future);
			providers.add(countingProvider("provider-" + i, future, active, peak));
		}
		var call = new SubtitleAggregator().aggregate(List.of(), providers, List.of("en"),
				generation.begin());
		assertEquals(4, peak.get());
		for (int i = 0; i < futures.size(); i++) {
			futures.get(i).complete(List.of(candidate("id-" + i,
					"https://sub.example.invalid/" + i + ".srt", "en", SubtitleCandidate.Source.PROVIDER)));
		}
		assertEquals(6, call.result().join().subtitles().size());

		var many = new ArrayList<SubtitleCandidate>();
		for (int i = 0; i <= SubtitleAggregator.MAX_SUBTITLES; i++) {
			many.add(candidate("item-" + i, "https://sub.example.invalid/items/" + i + ".srt", "en",
					SubtitleCandidate.Source.STREAM_EMBEDDED));
		}
		var limited = new SubtitleAggregator().aggregate(many, List.of(), List.of("en"),
				generation.begin()).result().join();
		assertEquals(SubtitleAggregator.MAX_SUBTITLES, limited.subtitles().size());
		assertTrue(limited.truncated());
	}

	@Test
	public void invalidInputBecomesFailureAndDoesNotAbortValidSubtitle() {
		var generation = new RequestGeneration();
		var invalid = candidate("bad", "file:///private/token.srt", "en",
				SubtitleCandidate.Source.PROVIDER);
		var valid = candidate("good", "https://sub.example.invalid/good.srt", "en",
				SubtitleCandidate.Source.PROVIDER);
		var result = new SubtitleAggregator().aggregate(List.of(invalid, valid), List.of(),
				List.of("en"), generation.begin()).result().join();
		assertEquals(1, result.subtitles().size());
		assertEquals(SubtitleProviderFailure.Code.INVALID_SUBTITLE, result.failures().get(0).code());
	}

	@Test
	public void observerReceivesFinitePartialResultsBeforeCompletion() throws Exception {
		var first = new CompletableFuture<List<SubtitleCandidate>>();
		var second = new CompletableFuture<List<SubtitleCandidate>>();
		var call = new SubtitleAggregator().aggregate(List.of(),
				List.of(provider("first", first), provider("second", second)),
				List.of("en"), new RequestGeneration().begin());
		var snapshots = new ArrayList<SubtitleAggregationResult>();
		AutoCloseable observation = call.observe(snapshots::add);
		assertFalse(snapshots.get(0).complete());

		first.complete(List.of(candidate("one", "https://sub.example.invalid/one.srt", "en",
				SubtitleCandidate.Source.PROVIDER)));
		assertEquals(1, snapshots.get(snapshots.size() - 1).subtitles().size());
		assertFalse(snapshots.get(snapshots.size() - 1).complete());

		second.complete(List.of(candidate("two", "https://sub.example.invalid/two.srt", "en",
				SubtitleCandidate.Source.PROVIDER)));
		assertTrue(snapshots.get(snapshots.size() - 1).complete());
		assertEquals(2, call.result().join().subtitles().size());
		observation.close();
	}

	private static SubtitleCandidate candidate(String id, String url, String language,
			SubtitleCandidate.Source source) {
		return new SubtitleCandidate(id, url, language, "provider", "Provider", source,
				null, -1, new OpaqueHeaderReference("headers_1"), EXPIRY);
	}

	private static SubtitleProvider failingProvider(String key) {
		var failure = new CompletableFuture<List<SubtitleCandidate>>();
		failure.completeExceptionally(new IllegalStateException("secret provider URL"));
		return provider(key, failure);
	}

	private static SubtitleProvider provider(String key,
			CompletableFuture<List<SubtitleCandidate>> future) {
		return provider(key, future, new AtomicBoolean());
	}

	private static SubtitleProvider provider(String key,
			CompletableFuture<List<SubtitleCandidate>> future, AtomicBoolean cancelled) {
		return new SubtitleProvider() {
			@Override
			public String providerKey() {
				return key;
			}

			@Override
			public SubtitleProviderCall load(RequestGeneration.Token generation) {
				return new SubtitleProviderCall() {
					@Override
					public CompletableFuture<List<SubtitleCandidate>> response() {
						return future;
					}

					@Override
					public void cancel() {
						cancelled.set(true);
					}
				};
			}
		};
	}

	private static SubtitleProvider countingProvider(String key,
			CompletableFuture<List<SubtitleCandidate>> future, AtomicInteger active,
			AtomicInteger peak) {
		return new SubtitleProvider() {
			@Override
			public String providerKey() {
				return key;
			}

			@Override
			public SubtitleProviderCall load(RequestGeneration.Token generation) {
				int count = active.incrementAndGet();
				peak.accumulateAndGet(count, Math::max);
				future.whenComplete((value, error) -> active.decrementAndGet());
				return new SubtitleProviderCall() {
					@Override
					public CompletableFuture<List<SubtitleCandidate>> response() {
						return future;
					}

					@Override
					public void cancel() {
					}
				};
			}
		};
	}
}
