package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

public sealed interface BrowseLoadState<T> permits BrowseLoadState.Loading,
		BrowseLoadState.Content, BrowseLoadState.Empty, BrowseLoadState.Failure {
	record Loading<T>(T previous, boolean stale) implements BrowseLoadState<T> {
	}

	record Content<T>(T value, boolean stale, List<ProviderFailure> failures)
			implements BrowseLoadState<T> {
		public Content {
			value = Objects.requireNonNull(value, "value");
			failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
		}

		public boolean partial() {
			return !failures.isEmpty();
		}

		public boolean canRetry() {
			return failures.stream().anyMatch(ProviderFailure::retryable);
		}
	}

	record Empty<T>(boolean stale, List<ProviderFailure> failures) implements BrowseLoadState<T> {
		public Empty {
			failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
		}

		public boolean canRetry() {
			return failures.stream().anyMatch(ProviderFailure::retryable);
		}
	}

	record Failure<T>(List<ProviderFailure> failures) implements BrowseLoadState<T> {
		public Failure {
			failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
			if (failures.isEmpty()) throw new IllegalArgumentException("failures cannot be empty");
		}

		public boolean canRetry() {
			return failures.stream().anyMatch(ProviderFailure::retryable);
		}
	}
}
