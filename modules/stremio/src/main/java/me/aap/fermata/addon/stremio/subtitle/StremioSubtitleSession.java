package me.aap.fermata.addon.stremio.subtitle;

import java.util.Objects;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import me.aap.fermata.diagnostics.DiagnosticOperation;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.media.engine.SubtitleTrackFile;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.vfs.VirtualResource;

/** Owns discovery and sidecar loading for exactly one active Stremio video identity. */
public final class StremioSubtitleSession {
	private final String videoKey;
	private long generation;
	private FutureSupplier<?> discovery;
	private FutureSupplier<?> load;
	private DiagnosticOperation discoveryDiagnostics;
	private DiagnosticOperation loadDiagnostics;
	private String providerClass = "unknown";
	private String languageCode = "unknown";
	private String format = "unknown";
	private int resultCount;
	private long staleCallbacks;

	public StremioSubtitleSession(String videoKey) {
		this.videoKey = requireKey(videoKey);
	}

	public String videoKey() {
		return videoKey;
	}

	public synchronized void activate(String videoKey) {
		if (!this.videoKey.equals(requireKey(videoKey))) {
			throw new IllegalArgumentException("Subtitle session identity cannot change");
		}
		generation++;
		cancel(discovery);
		cancel(load);
		cancelDiagnostics(discoveryDiagnostics, "STALE_GENERATION");
		cancelDiagnostics(loadDiagnostics, "STALE_GENERATION");
		discovery = null;
		load = null;
		discoveryDiagnostics = null;
		loadDiagnostics = null;
	}

	public <T> FutureSupplier<T> discover(Supplier<FutureSupplier<T>> operation) {
		return start(true, operation);
	}

	public <T> FutureSupplier<T> load(Supplier<FutureSupplier<T>> operation) {
		return start(false, operation);
	}

	public synchronized void cancel() {
		generation++;
		cancel(discovery);
		cancel(load);
		cancelDiagnostics(discoveryDiagnostics, "LIFECYCLE");
		cancelDiagnostics(loadDiagnostics, "LIFECYCLE");
		discovery = null;
		load = null;
		discoveryDiagnostics = null;
		loadDiagnostics = null;
	}

	public synchronized long staleCallbackCount() {
		return staleCallbacks;
	}

	private <T> FutureSupplier<T> start(boolean discoveryStage,
			Supplier<FutureSupplier<T>> operation) {
		Objects.requireNonNull(operation, "operation");
		final long owner;
		final DiagnosticOperation diagnostics;
		synchronized (this) {
			owner = generation;
			FutureSupplier<?> previous = discoveryStage ? discovery : load;
			cancel(previous);
			if (discoveryStage) {
				cancelDiagnostics(discoveryDiagnostics, "SUPERSEDED");
				discoveryDiagnostics = null;
			} else {
				cancelDiagnostics(loadDiagnostics, "SUPERSEDED");
				loadDiagnostics = null;
			}
			diagnostics = beginDiagnostics(discoveryStage, owner);
		}
		FutureSupplier<T> upstream;
		try {
			upstream = Objects.requireNonNull(operation.get(), "subtitle operation");
		} catch (Throwable error) {
			if (isCancellation(error)) cancelDiagnostics(diagnostics, "CANCELLED");
			else failDiagnostics(diagnostics, error, discoveryStage, owner, 0);
			return me.aap.utils.async.Completed.failed(error);
		}
		Promise<T> result = new Promise<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				upstream.cancel(mayInterruptIfRunning);
				return super.cancel(mayInterruptIfRunning);
			}
		};
		synchronized (this) {
			if (owner != generation) {
				upstream.cancel();
				cancelDiagnostics(diagnostics, "STALE_GENERATION");
				result.completeExceptionally(new CancellationException(
						"Subtitle session was replaced"));
				return result;
			}
			if (discoveryStage) {
				discovery = result;
				discoveryDiagnostics = diagnostics;
			} else {
				load = result;
				loadDiagnostics = diagnostics;
			}
		}
		upstream.onCompletion((value, error) -> {
			synchronized (StremioSubtitleSession.this) {
				FutureSupplier<?> current = discoveryStage ? discovery : load;
				if ((owner != generation) || (current != result)) {
					staleCallbacks++;
					cancelDiagnostics(diagnostics, "STALE_GENERATION");
					return;
				}
				if (discoveryStage) {
					discovery = null;
					discoveryDiagnostics = null;
				} else {
					load = null;
					loadDiagnostics = null;
				}
			}
			if (error == null) {
				if (discoveryStage) updateMetadata(value);
				completeDiagnostics(diagnostics, discoveryStage, owner, value);
				result.complete(value);
			} else {
				if (isCancellation(error)) cancelDiagnostics(diagnostics, "CANCELLED");
				else failDiagnostics(diagnostics, error, discoveryStage, owner, 0);
				result.completeExceptionally(error);
			}
		});
		return result;
	}

	private DiagnosticOperation beginDiagnostics(boolean discoveryStage, long owner) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("phase", discoveryStage ? "discovery" : "load");
		attributes.put("generation", owner);
		return AndroidDiagnosticsRuntime.get().begin("stremio_subtitle",
				discoveryStage ? "subtitle_discovery" : "subtitle_load", attributes);
	}

	private void completeDiagnostics(DiagnosticOperation diagnostics, boolean discoveryStage,
			long owner, Object value) {
		if (diagnostics == null) return;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("status", "completed");
		attributes.put("phase", discoveryStage ? "discovery" : "load");
		attributes.put("generation", owner);
		attributes.put("provider_class", providerClass);
		attributes.put("language_code", languageCode);
		attributes.put("format", format);
		attributes.put("count", resultCount);
		if (value instanceof byte[]) attributes.put("byte_count", ((byte[]) value).length);
		try {
			diagnostics.complete(attributes);
		} catch (Throwable ignored) {
		}
	}

	private void failDiagnostics(DiagnosticOperation diagnostics, Throwable error,
			boolean discoveryStage, long owner, int count) {
		if (diagnostics == null) return;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("status", "failed");
		attributes.put("phase", discoveryStage ? "discovery" : "load");
		attributes.put("generation", owner);
		attributes.put("provider_class", providerClass);
		attributes.put("language_code", languageCode);
		attributes.put("format", format);
		attributes.put("count", count);
		try {
			diagnostics.fail(error, attributes);
		} catch (Throwable ignored) {
		}
	}

	private static void cancelDiagnostics(DiagnosticOperation diagnostics, String reason) {
		if (diagnostics == null) return;
		try {
			diagnostics.cancel(Map.of("status", "cancelled", "reason", reason));
		} catch (Throwable ignored) {
		}
	}

	private static boolean isCancellation(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof CancellationException) return true;
			if (current.getCause() == current) break;
		}
		return false;
	}

	private void updateMetadata(Object value) {
		if (!(value instanceof Collection<?> collection)) {
			resultCount = 0;
			return;
		}
		resultCount = collection.size();
		String provider = null;
		String language = null;
		String detectedFormat = null;
		for (Object item : collection) {
			if (!(item instanceof VirtualResource resource)) continue;
			if (provider == null) provider = resource.getClass().getName();
			if (item instanceof SubtitleTrackFile track) {
				language = mergeValue(language, track.getSubtitleLanguage());
				detectedFormat = mergeValue(detectedFormat, formatOf(resource.getName()));
			}
		}
		providerClass = (provider == null) ? "unknown" : provider;
		languageCode = (language == null) ? "unknown" : language;
		format = (detectedFormat == null) ? "unknown" : detectedFormat;
	}

	private static String mergeValue(String previous, String value) {
		if ((value == null) || value.isBlank()) return previous;
		if (previous == null) return value;
		return previous.equals(value) ? previous : "multiple";
	}

	private static String formatOf(String name) {
		if (name == null) return null;
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".vtt")) return "webvtt";
		if (lower.endsWith(".srt")) return "subrip";
		return null;
	}

	private static void cancel(FutureSupplier<?> operation) {
		if ((operation != null) && !operation.isDone()) operation.cancel();
	}

	private static String requireKey(String value) {
		Objects.requireNonNull(value, "videoKey");
		if (value.isBlank()) throw new IllegalArgumentException("videoKey cannot be blank");
		return value;
	}
}
