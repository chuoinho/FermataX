package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Runs loaded providers independently so one failure or hang cannot suppress another provider. */
public final class SmartTopProviderCoordinator {
	public static final long PROVIDER_TIMEOUT_MILLIS = 500L;
	public static final int MAX_PROVIDER_CANDIDATES = 32;

	interface Backend {
		List<SmartTopProviderLease> leases();

		FutureSupplier<List<SmartTopCandidate>> load(SmartTopProviderLease lease);

		boolean owns(SmartTopProviderLease lease);

		boolean accepts(SmartTopProviderLease lease, SmartTopCandidate candidate);

		FutureSupplier<PlayableItem> resolve(DefaultMediaLib lib, SmartTopProviderLease lease,
				SmartTopCandidate candidate);
	}

	private final Backend backend;

	public SmartTopProviderCoordinator(AddonManager manager) {
		this(new Backend() {
			@Override
			public List<SmartTopProviderLease> leases() {
				return manager.snapshotSmartTopProviderLeases();
			}

			@Override
			public FutureSupplier<List<SmartTopCandidate>> load(SmartTopProviderLease lease) {
				return manager.loadSmartTopCandidates(lease);
			}

			@Override
			public boolean owns(SmartTopProviderLease lease) {
				return manager.ownsSmartTopProviderLease(lease);
			}

			@Override
			public boolean accepts(SmartTopProviderLease lease, SmartTopCandidate candidate) {
				return manager.acceptsSmartTopCandidate(lease, candidate);
			}

			@Override
			public FutureSupplier<PlayableItem> resolve(DefaultMediaLib lib,
					SmartTopProviderLease lease, SmartTopCandidate candidate) {
				return manager.resolveSmartTopCandidate(lib, lease, candidate);
			}
		});
	}

	SmartTopProviderCoordinator(Backend backend) {
		this.backend = backend;
	}

	public FutureSupplier<List<SmartTopProviderResult>> loadCandidates() {
		List<SmartTopProviderLease> leases = backend.leases();
		if (leases.isEmpty()) return completed(List.of());

		Promise<List<SmartTopProviderResult>> result = new Promise<>();
		AtomicInteger remaining = new AtomicInteger(leases.size());
		List<SmartTopProviderResult> collected = new ArrayList<>();
		for (SmartTopProviderLease lease : leases) {
			backend.load(lease)
					.timeout(PROVIDER_TIMEOUT_MILLIS, List::of)
					.onCompletion((candidates, failure) -> {
						if ((failure == null) && (candidates != null) &&
								(candidates.size() <= MAX_PROVIDER_CANDIDATES) &&
								backend.owns(lease)) {
							synchronized (collected) {
								for (SmartTopCandidate candidate : candidates) {
									if (backend.accepts(lease, candidate)) {
										collected.add(new SmartTopProviderResult(lease, candidate));
									}
								}
							}
						}
						if (remaining.decrementAndGet() == 0) {
							List<SmartTopProviderResult> snapshot;
							synchronized (collected) {
								snapshot = new ArrayList<>(collected);
							}
							snapshot.sort(Comparator
									.comparingInt((SmartTopProviderResult r) ->
											r.candidate().kind() == SmartTopCandidate.Kind.RESUME ? 0 : 1)
									.thenComparing(Comparator.comparingLong(
											(SmartTopProviderResult r) ->
													r.candidate().lastInteractionMillis()).reversed())
									.thenComparing(r -> r.candidate().addonClass())
									.thenComparing(r -> r.candidate().opaqueId()));
							result.complete(List.copyOf(snapshot));
						}
					});
		}
		return result;
	}

	public FutureSupplier<PlayableItem> resolve(DefaultMediaLib lib,
			SmartTopProviderResult result) {
		return backend.resolve(lib, result.lease(), result.candidate());
	}
}
