package me.aap.fermata.addon.stremio.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.source.StremioSourceInput;
import me.aap.fermata.addon.stremio.source.StremioSourceManager;
import me.aap.fermata.addon.stremio.source.StremioSourceException;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Action;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;

/** Source facade that canonicalizes deep links before persistence and manager validation. */
public final class StremioRuntimeSources {
	private final StremioSourceManager delegate;

	StremioRuntimeSources(StremioSourceManager delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	public CompletableFuture<StremioSourceSnapshot> sources() {
		return delegate.sources();
	}

	public AutoCloseable observe(Consumer<StremioSourceSnapshot> observer) {
		return delegate.observe(observer);
	}

	public CompletableFuture<StremioSourceOutcome> add(StremioSourceInput input) {
		try {
			return delegate.add(normalize(input));
		} catch (StremioSourceException error) {
			return failed(Action.ADD, null, error);
		}
	}

	public CompletableFuture<StremioSourceOutcome> edit(
			String sourceUuid, StremioSourceInput input) {
		try {
			return delegate.edit(sourceUuid, normalize(input));
		} catch (StremioSourceException error) {
			return failed(Action.EDIT, sourceUuid, error);
		}
	}

	public CompletableFuture<StremioSourceOutcome> enable(String sourceUuid) {
		return delegate.enable(sourceUuid);
	}

	public CompletableFuture<StremioSourceOutcome> disable(String sourceUuid) {
		return delegate.disable(sourceUuid);
	}

	public CompletableFuture<StremioSourceOutcome> refresh(String sourceUuid) {
		return delegate.refresh(sourceUuid);
	}

	public CompletableFuture<StremioSourceOutcome> remove(String sourceUuid) {
		return delegate.remove(sourceUuid);
	}

	public CompletableFuture<StremioSourceOutcome> reorder(List<String> sourceUuids) {
		return delegate.reorder(sourceUuids);
	}

	public CompletableFuture<StremioSourceOutcome> initializeCinemeta(
			boolean freshInstall, StremioSourceInput input) {
		try {
			return delegate.initializeCinemeta(freshInstall, normalize(input));
		} catch (StremioSourceException error) {
			return failed(Action.INITIALIZE_DEFAULT, null, error);
		}
	}

	static StremioSourceInput normalize(StremioSourceInput input) {
		Objects.requireNonNull(input, "input");
		return new StremioSourceInput(
				ProductionStremioManifestClient.normalizeManifestUri(input.transportUrl()).toString(),
				input.configurationToken(), input.networkConsent());
	}

	private static CompletableFuture<StremioSourceOutcome> failed(
			Action action, String sourceUuid, StremioSourceException error) {
		return CompletableFuture.completedFuture(new StremioSourceOutcome(action, Status.FAILED,
				sourceUuid, null, error.code()));
	}
}
