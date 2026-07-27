package me.aap.fermata.addon.stremio.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.addon.stremio.failure.StremioFailure;
import me.aap.fermata.addon.stremio.failure.StremioRecovery;
import me.aap.fermata.addon.stremio.lifecycle.ProviderLoadState;

public record StreamAggregationResult(
		List<ProviderGroup> providerGroups, List<PlaybackDescriptor> orderedDescriptors) {
	public StreamAggregationResult(List<ProviderGroup> providerGroups) {
		this(providerGroups, flatten(providerGroups));
	}

	public StreamAggregationResult {
		providerGroups = List.copyOf(Objects.requireNonNull(providerGroups, "providerGroups"));
		orderedDescriptors = List.copyOf(
				Objects.requireNonNull(orderedDescriptors, "orderedDescriptors"));
	}

	public List<PlaybackDescriptor> descriptors() {
		return orderedDescriptors;
	}

	public boolean hasPartialFailures() {
		boolean success = false;
		boolean failure = false;
		for (ProviderGroup group : providerGroups) {
			if (group.status() == ProviderStatus.SUCCESS) success = true;
			else failure = true;
		}
		return success && failure;
	}

	public boolean hasPendingProviders() {
		for (ProviderGroup group : providerGroups) {
			if (group.status() == ProviderStatus.PENDING) return true;
		}
		return false;
	}

	public record ProviderGroup(
			StreamProvider provider, ProviderStatus status, List<PlaybackDescriptor> descriptors,
			long operationId) {
		public ProviderGroup(StreamProvider provider, ProviderStatus status,
				List<PlaybackDescriptor> descriptors) {
			this(provider, status, descriptors, -1L);
		}

		public ProviderGroup {
			Objects.requireNonNull(provider, "provider");
			Objects.requireNonNull(status, "status");
			descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
			if ((status != ProviderStatus.SUCCESS) && !descriptors.isEmpty()) {
				throw new IllegalArgumentException("failed provider cannot expose descriptors");
			}
		}

		public ProviderLoadState<List<PlaybackDescriptor>> loadState() {
			return switch (status) {
				case PENDING -> new ProviderLoadState.Loading<>(provider.sourceUuid(), operationId);
				case SUCCESS -> descriptors.isEmpty() ?
						new ProviderLoadState.Empty<>(provider.sourceUuid(), operationId) :
						new ProviderLoadState.Ready<>(provider.sourceUuid(), operationId, descriptors);
				case FAILED -> new ProviderLoadState.Failed<>(provider.sourceUuid(), operationId,
						new StremioFailure(StremioFailure.Code.INTERNAL,
								StremioFailure.Phase.STREAM, provider.sourceUuid(), false,
								StremioRecovery.CANCEL, null));
				case TIMED_OUT -> new ProviderLoadState.Failed<>(provider.sourceUuid(), operationId,
						new StremioFailure(StremioFailure.Code.BODY_TIMEOUT,
								StremioFailure.Phase.STREAM, provider.sourceUuid(), true,
								StremioRecovery.RETRY, null));
			};
		}
	}

	public enum ProviderStatus {
		SUCCESS,
		FAILED,
		TIMED_OUT,
		PENDING
	}

	private static List<PlaybackDescriptor> flatten(List<ProviderGroup> groups) {
		Objects.requireNonNull(groups, "providerGroups");
		ArrayList<PlaybackDescriptor> descriptors = new ArrayList<>();
		for (ProviderGroup group : groups) descriptors.addAll(group.descriptors());
		return List.copyOf(descriptors);
	}
}
