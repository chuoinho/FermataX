package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

	void select(@NonNull T item) {
		presentedItem = (canonicalizer.canonicalize(item) == item) ? null : item;
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

	void clear() {
		presentedItem = null;
	}
}
