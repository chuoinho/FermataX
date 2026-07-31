package me.aap.fermata.addon.stremio.subtitle;

import static me.aap.fermata.addon.stremio.subtitle.SubtitleProviderFailure.Code.INVALID_SUBTITLE;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;
import me.aap.fermata.addon.stremio.net.RequestGeneration;

public final class SubtitleAggregator {
	public static final int MAX_CONCURRENT_PROVIDERS = 4;
	public static final int MAX_PROVIDERS = 64;
	public static final int MAX_SUBTITLES = 500;
	public static final int MAX_URL_BYTES = 16 * 1024;
	public static final long MAX_SUBTITLE_FILE_BYTES = 10L * 1024L * 1024L;

	private final int maxConcurrentProviders;

	public SubtitleAggregator() {
		this(MAX_CONCURRENT_PROVIDERS);
	}

	SubtitleAggregator(int maxConcurrentProviders) {
		if ((maxConcurrentProviders < 1) ||
				(maxConcurrentProviders > MAX_CONCURRENT_PROVIDERS)) {
			throw new IllegalArgumentException("Invalid subtitle provider concurrency");
		}
		this.maxConcurrentProviders = maxConcurrentProviders;
	}

	public AggregationCall aggregate(List<SubtitleCandidate> embedded,
			List<? extends SubtitleProvider> providers, List<String> preferredLanguages,
			RequestGeneration.Token generation) {
		return new AggregationCall(embedded, providers, preferredLanguages, generation);
	}

	public final class AggregationCall implements StremioCall<SubtitleAggregationResult> {
		private final CompletableFuture<SubtitleAggregationResult> result = new CompletableFuture<>();
		private final List<SubtitleProvider> providers;
		private final List<SubtitleLanguage> preferred;
		private final RequestGeneration.Token generation;
		private final List<SubtitleDescriptor> descriptors = new ArrayList<>();
		private final List<SubtitleProviderFailure> failures = new ArrayList<>();
		private final Map<String, ActiveProvider> active = new HashMap<>();
		private final Set<String> removed = new HashSet<>();
		private final Set<Consumer<SubtitleAggregationResult>> observers = new HashSet<>();
		private int nextProvider;
		private boolean terminal;
		private boolean truncated;

		private AggregationCall(List<SubtitleCandidate> embedded,
				List<? extends SubtitleProvider> providers, List<String> preferredLanguages,
				RequestGeneration.Token generation) {
			Objects.requireNonNull(embedded, "embedded");
			Objects.requireNonNull(providers, "providers");
			Objects.requireNonNull(preferredLanguages, "preferredLanguages");
			this.generation = Objects.requireNonNull(generation, "generation");
			this.preferred = preferredLanguages.stream()
					.map(SubtitleLanguageNormalizer::normalize).distinct().toList();
			this.providers = copyProviders(providers);
			if (providers.size() > MAX_PROVIDERS) {
				truncated = true;
				failures.add(new SubtitleProviderFailure("aggregate",
						SubtitleProviderFailure.Code.LIMIT_REACHED));
			}
			addCandidates("embedded", embedded);
			pump();
		}

		public CompletableFuture<SubtitleAggregationResult> result() {
			return result;
		}

		public AutoCloseable observe(Consumer<SubtitleAggregationResult> observer) {
			Objects.requireNonNull(observer, "observer");
			SubtitleAggregationResult snapshot;
			synchronized (this) {
				snapshot = buildLocked(terminal);
				if (!terminal) observers.add(observer);
			}
			observer.accept(snapshot);
			return () -> {
				synchronized (AggregationCall.this) {
					observers.remove(observer);
				}
			};
		}

		@Override
		public CompletableFuture<SubtitleAggregationResult> completion() {
			return result;
		}

		@Override
		public boolean isActive() {
			return !terminal && !result.isDone();
		}

		public void cancel() {
			List<SubtitleProvider.SubtitleProviderCall> calls;
			synchronized (this) {
				if (terminal) return;
				terminal = true;
				calls = active.values().stream().map(ActiveProvider::call)
						.filter(Objects::nonNull).toList();
				active.clear();
				observers.clear();
			}
			calls.forEach(SubtitleProvider.SubtitleProviderCall::cancel);
			result.completeExceptionally(new CancellationException("Subtitle aggregation cancelled"));
		}

		public void removeProvider(String providerKey) {
			Objects.requireNonNull(providerKey, "providerKey");
			SubtitleProvider.SubtitleProviderCall call = null;
			synchronized (this) {
				if (terminal || !removed.add(providerKey)) return;
				ActiveProvider provider = active.remove(providerKey);
				if (provider != null) {
					call = provider.call();
					failures.add(new SubtitleProviderFailure(providerKey,
							SubtitleProviderFailure.Code.PROVIDER_REMOVED));
				}
			}
			if (call != null) call.cancel();
			publishPartial();
			pump();
		}

		private void pump() {
			while (true) {
				if (!generation.isCurrent()) {
					cancel();
					return;
				}
				SubtitleProvider provider;
				SubtitleAggregationResult completed = null;
				synchronized (this) {
					if (terminal) return;
					provider = nextProvider();
					if (provider == null) {
						if (active.isEmpty() && (nextProvider >= providers.size())) {
							completed = finishLocked();
						}
					}
				}
				if (completed != null) {
					publish(completed, true);
					result.complete(completed);
					return;
				}
				if (provider == null) return;
				start(provider);
			}
		}

		private SubtitleProvider nextProvider() {
			if (active.size() >= maxConcurrentProviders) return null;
			while (nextProvider < providers.size()) {
				SubtitleProvider provider = providers.get(nextProvider++);
				if (removed.contains(provider.providerKey())) {
					failures.add(new SubtitleProviderFailure(provider.providerKey(),
							SubtitleProviderFailure.Code.PROVIDER_REMOVED));
					continue;
				}
				return provider;
			}
			return null;
		}

		private void start(SubtitleProvider provider) {
			synchronized (this) {
				if (terminal || removed.contains(provider.providerKey())) return;
				active.put(provider.providerKey(), new ActiveProvider(null));
			}
			try {
				SubtitleProvider.SubtitleProviderCall call = provider.load(generation);
				Objects.requireNonNull(call, "provider call");
				CompletableFuture<List<SubtitleCandidate>> response =
						Objects.requireNonNull(call.response(), "provider response");
				synchronized (this) {
					if (terminal || removed.contains(provider.providerKey())) {
						call.cancel();
						return;
					}
					active.put(provider.providerKey(), new ActiveProvider(call));
				}
				response.whenComplete((subtitles, error) ->
						completeProvider(provider.providerKey(), subtitles, error));
			} catch (Throwable error) {
				completeProvider(provider.providerKey(), null, error);
			}
		}

		private void completeProvider(String providerKey, List<SubtitleCandidate> subtitles,
				Throwable error) {
			boolean stale;
			synchronized (this) {
				if (terminal || (active.remove(providerKey) == null)) return;
				stale = !generation.isCurrent();
				if (stale) {
					// Cancellation runs outside the monitor so provider code cannot re-enter it.
				} else if (error != null) {
					failures.add(new SubtitleProviderFailure(providerKey,
							SubtitleProviderFailure.Code.PROVIDER_FAILED));
				} else if (subtitles == null) {
					failures.add(new SubtitleProviderFailure(providerKey,
							SubtitleProviderFailure.Code.PROVIDER_FAILED));
				} else {
					addCandidates(providerKey, subtitles);
				}
			}
			if (stale) {
				cancel();
				return;
			}
			publishPartial();
			pump();
		}

		private void addCandidates(String providerKey, List<SubtitleCandidate> candidates) {
			for (SubtitleCandidate candidate : candidates) {
				if (descriptors.size() >= MAX_SUBTITLES) {
					truncated = true;
					failures.add(new SubtitleProviderFailure(providerKey,
							SubtitleProviderFailure.Code.LIMIT_REACHED));
					return;
				}
				try {
					descriptors.add(normalize(candidate));
				} catch (RuntimeException ex) {
					failures.add(new SubtitleProviderFailure(providerKey, INVALID_SUBTITLE));
				}
			}
		}

		private SubtitleAggregationResult finishLocked() {
			if (terminal) return null;
			terminal = true;
			var unique = new LinkedHashMap<String, SubtitleDescriptor>();
			descriptors.stream().sorted(ranking(preferred)).forEach(descriptor ->
					unique.putIfAbsent(descriptor.language().tag() + '\u001f' +
							descriptor.identity(), descriptor));
			return new SubtitleAggregationResult(
					List.copyOf(unique.values()), List.copyOf(failures), truncated, true);
		}

		private SubtitleAggregationResult buildLocked(boolean complete) {
			var unique = new LinkedHashMap<String, SubtitleDescriptor>();
			descriptors.stream().sorted(ranking(preferred)).forEach(descriptor ->
					unique.putIfAbsent(descriptor.language().tag() + '\u001f' +
							descriptor.identity(), descriptor));
			return new SubtitleAggregationResult(List.copyOf(unique.values()),
					List.copyOf(failures), truncated, complete);
		}

		private void publishPartial() {
			SubtitleAggregationResult snapshot;
			synchronized (this) {
				if (terminal || observers.isEmpty()) return;
				snapshot = buildLocked(false);
			}
			publish(snapshot, false);
		}

		private void publish(SubtitleAggregationResult snapshot, boolean terminalSnapshot) {
			List<Consumer<SubtitleAggregationResult>> listeners;
			synchronized (this) {
				listeners = List.copyOf(observers);
				if (terminalSnapshot) observers.clear();
			}
			for (Consumer<SubtitleAggregationResult> observer : listeners) {
				try {
					observer.accept(snapshot);
				} catch (RuntimeException ignored) {
				}
			}
		}
	}

	private static List<SubtitleProvider> copyProviders(List<? extends SubtitleProvider> providers) {
		var copy = new ArrayList<SubtitleProvider>(Math.min(providers.size(), MAX_PROVIDERS));
		var keys = new HashSet<String>();
		for (SubtitleProvider provider : providers) {
			Objects.requireNonNull(provider, "provider");
			String key = Objects.requireNonNull(provider.providerKey(), "providerKey");
			if (key.isBlank() || !keys.add(key)) {
				throw new IllegalArgumentException("Duplicate or blank subtitle provider key");
			}
			if (copy.size() == MAX_PROVIDERS) break;
			copy.add(provider);
		}
		return List.copyOf(copy);
	}

	private static SubtitleDescriptor normalize(SubtitleCandidate candidate) {
		Objects.requireNonNull(candidate, "candidate");
		if (candidate.url().getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES) {
			throw new IllegalArgumentException("Subtitle URL exceeds limit");
		}
		URI uri = URI.create(candidate.url()).normalize();
		String scheme = uri.getScheme();
		if (!uri.isAbsolute() || uri.isOpaque() || (uri.getHost() == null) ||
				(!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("Invalid subtitle URL");
		}
		SubtitleLanguage language = SubtitleLanguageNormalizer.normalize(candidate.languageLabel());
		SubtitleFormat format = SubtitleFormat.classify(uri, candidate.formatHint());
		SubtitleDescriptor.Status status;
		if ((candidate.declaredSizeBytes() >= 0) &&
				(candidate.declaredSizeBytes() > MAX_SUBTITLE_FILE_BYTES)) {
			status = SubtitleDescriptor.Status.FILE_TOO_LARGE;
		} else {
			status = (format.isSupported() || format.isEngineReadable(uri)) ?
					SubtitleDescriptor.Status.READY :
					SubtitleDescriptor.Status.UNSUPPORTED_FORMAT;
		}
		String identity = digest(canonicalUrl(uri));
		return new SubtitleDescriptor(identity, candidate.subtitleId(), uri,
				candidate.languageLabel(), language, candidate.providerKey(),
				candidate.providerLabel(), candidate.source(), format, status,
				candidate.declaredSizeBytes(), candidate.requestHeaders(),
				candidate.sourceLease(), candidate.expiresAt());
	}

	private static String canonicalUrl(URI uri) {
		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		int port = uri.getPort();
		if (("https".equals(scheme) && (port == 443)) ||
				("http".equals(scheme) && (port == 80))) port = -1;
		try {
			return new URI(scheme, uri.getUserInfo(), host, port, uri.getPath(),
					uri.getQuery(), null).toASCIIString();
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid subtitle URL", ex);
		}
	}

	private static String digest(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			var out = new StringBuilder(32);
			for (int i = 0; i < 16; i++) out.append(String.format(Locale.ROOT, "%02x", bytes[i]));
			return out.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static Comparator<SubtitleDescriptor> ranking(List<SubtitleLanguage> preferred) {
		return Comparator.comparingInt((SubtitleDescriptor descriptor) -> rank(descriptor, preferred))
				.thenComparingInt(descriptor -> preferenceIndex(descriptor.language(), preferred))
				.thenComparingInt(descriptor -> descriptor.status() ==
						SubtitleDescriptor.Status.READY ? 0 : 1)
				.thenComparingInt(descriptor -> descriptor.source() ==
						SubtitleCandidate.Source.STREAM_EMBEDDED ? 0 : 1)
				.thenComparing(descriptor -> descriptor.language().tag())
				.thenComparing(SubtitleDescriptor::providerKey)
				.thenComparing(SubtitleDescriptor::identity)
				.thenComparing(SubtitleDescriptor::subtitleId);
	}

	private static int rank(SubtitleDescriptor descriptor, List<SubtitleLanguage> preferred) {
		SubtitleLanguage language = descriptor.language();
		for (SubtitleLanguage preference : preferred) {
			if (!preference.isUnknown() && preference.tag().equals(language.tag())) return 0;
		}
		for (SubtitleLanguage preference : preferred) {
			if (!preference.isUnknown() &&
					preference.baseLanguage().equals(language.baseLanguage())) return 1;
		}
		return language.isUnknown() ? 2 : 3;
	}

	private static int preferenceIndex(SubtitleLanguage language, List<SubtitleLanguage> preferred) {
		for (int i = 0; i < preferred.size(); i++) {
			SubtitleLanguage preference = preferred.get(i);
			if (preference.tag().equals(language.tag()) ||
					preference.baseLanguage().equals(language.baseLanguage())) return i;
		}
		return Integer.MAX_VALUE;
	}

	private record ActiveProvider(SubtitleProvider.SubtitleProviderCall call) {
	}
}
