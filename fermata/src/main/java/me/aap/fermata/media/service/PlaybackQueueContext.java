package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import me.aap.utils.async.FutureSupplier;

/** Keeps presentation order separate from the canonical item that owns playback. */
final class PlaybackQueueContext<T> {
	interface Canonicalizer<T> {
		@NonNull T canonicalize(@NonNull T item);
	}

	private final Canonicalizer<T> canonicalizer;
	@Nullable
	private T presentedItem;

	PlaybackQueueContext(Canonicalizer<T> canonicalizer) {
		this.canonicalizer = canonicalizer;
	}

	@NonNull
	T selectAndCanonicalize(@NonNull T item) {
		T canonical = canonicalizer.canonicalize(item);
		presentedItem = (canonical == item) ? null : item;
		return canonical;
	}

	@NonNull
	T navigationItem(@NonNull T source) {
		T presented = presentedItem;
		return ((presented != null) &&
				(canonicalizer.canonicalize(presented) == canonicalizer.canonicalize(source))) ?
				presented : source;
	}

	void advance(@NonNull T source, @Nullable T next) {
		T presented = presentedItem;
		if ((presented == null) ||
				(canonicalizer.canonicalize(presented) != canonicalizer.canonicalize(source))) {
			presentedItem = null;
			return;
		}
		presentedItem = ((next == null) || (canonicalizer.canonicalize(next) == next)) ? null : next;
	}

	FutureSupplier<T> prepareAdvance(@NonNull T source, @Nullable T candidate,
			@NonNull Function<T, FutureSupplier<T>> prepare) {
		if (candidate == null) {
			advance(source, null);
			return completedNull();
		}
		return prepare.apply(candidate).map(prepared -> {
			if (prepared != null) advance(source, candidate);
			return prepared;
		});
	}

	FutureSupplier<T> prepareAdjacent(@NonNull T source, @NonNull T cursor,
			@NonNull Function<T, FutureSupplier<T>> adjacent,
			@NonNull Function<T, FutureSupplier<T>> prepare,
			@NonNull Function<T, String> identity, int maxAttempts) {
		return prepareAdjacent(source, cursor, adjacent, prepare, identity,
				new HashSet<>(), Math.max(1, maxAttempts));
	}

	private FutureSupplier<T> prepareAdjacent(T source, T cursor,
			Function<T, FutureSupplier<T>> adjacent,
			Function<T, FutureSupplier<T>> prepare,
			Function<T, String> identity, Set<String> visited, int remaining) {
		if (remaining == 0) {
			advance(source, null);
			return completedNull();
		}
		return adjacent.apply(cursor).then(candidate -> {
			if ((candidate == null) || !visited.add(identity.apply(candidate))) {
				advance(source, null);
				return completedNull();
			}
			return prepare.apply(candidate).then(prepared -> {
				if (prepared != null) {
					advance(source, candidate);
					return me.aap.utils.async.Completed.completed(prepared);
				}
				return prepareAdjacent(source, candidate, adjacent, prepare, identity,
						visited, remaining - 1);
			});
		});
	}

	void clear() {
		presentedItem = null;
	}
}
