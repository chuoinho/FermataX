package me.aap.fermata.media.engine;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Result of selecting a media-engine candidate, including its rejection-time ownership. */
public record EngineSelection(@Nullable MediaEngine candidate, @NonNull Ownership ownership) {
	public enum Ownership {
		NO_CANDIDATE,
		PREEXISTING,
		BORROWED,
		OWNED_NEW
	}

	public EngineSelection {
		Objects.requireNonNull(ownership);
		if ((candidate == null) != (ownership == Ownership.NO_CANDIDATE)) {
			throw new IllegalArgumentException(
					"NO_CANDIDATE ownership must match a null engine candidate");
		}
	}
}
