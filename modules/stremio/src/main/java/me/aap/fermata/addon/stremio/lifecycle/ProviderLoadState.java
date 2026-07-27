package me.aap.fermata.addon.stremio.lifecycle;

import java.util.Objects;

import me.aap.fermata.addon.stremio.failure.StremioFailure;

/** Finite provider-resource state; no loading state may exist without an owning operation. */
public sealed interface ProviderLoadState<T> {
	String providerKey();

	long operationId();

	record Loading<T>(String providerKey, long operationId) implements ProviderLoadState<T> {
		public Loading {
			providerKey = requireKey(providerKey);
		}
	}

	record Ready<T>(String providerKey, long operationId, T value)
			implements ProviderLoadState<T> {
		public Ready {
			providerKey = requireKey(providerKey);
			Objects.requireNonNull(value, "value");
		}
	}

	record Empty<T>(String providerKey, long operationId) implements ProviderLoadState<T> {
		public Empty {
			providerKey = requireKey(providerKey);
		}
	}

	record Failed<T>(String providerKey, long operationId, StremioFailure failure)
			implements ProviderLoadState<T> {
		public Failed {
			providerKey = requireKey(providerKey);
			Objects.requireNonNull(failure, "failure");
		}
	}

	record Cancelled<T>(String providerKey, long operationId) implements ProviderLoadState<T> {
		public Cancelled {
			providerKey = requireKey(providerKey);
		}
	}

	private static String requireKey(String key) {
		Objects.requireNonNull(key, "providerKey");
		if (key.isBlank()) throw new IllegalArgumentException("providerKey is blank");
		return key;
	}
}
