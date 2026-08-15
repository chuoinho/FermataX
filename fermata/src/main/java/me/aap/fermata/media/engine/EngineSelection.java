package me.aap.fermata.media.engine;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Result of selecting a media-engine candidate without mutating the prior engine. */
public record EngineSelection(@Nullable MediaEngine candidate, @NonNull Ownership ownership,
		@NonNull Retirement retirement) {
	public enum Ownership {
		NO_CANDIDATE,
		PREEXISTING,
		BORROWED,
		OWNED_NEW
	}

	/**
	 * What the legacy selection path would have done with the supplied current engine.
	 *
	 * <p>The receiver must honor {@link #RETIRE_AFTER_RESOLUTION} only after it has
	 * authoritatively accepted the candidate or claimed the no-candidate failure. A stale or
	 * rejected selection must retain the prior engine.</p>
	 */
	public enum Retirement {
		RETAIN,
		RETIRE_AFTER_RESOLUTION
	}

	/** Keeps source compatibility for callers that do not need prior-engine retirement intent. */
	public EngineSelection(@Nullable MediaEngine candidate, @NonNull Ownership ownership) {
		this(candidate, ownership, Retirement.RETAIN);
	}

	public EngineSelection {
		Objects.requireNonNull(ownership);
		Objects.requireNonNull(retirement);
		if ((candidate == null) != (ownership == Ownership.NO_CANDIDATE)) {
			throw new IllegalArgumentException(
					"NO_CANDIDATE ownership must match a null engine candidate");
		}
	}
}
