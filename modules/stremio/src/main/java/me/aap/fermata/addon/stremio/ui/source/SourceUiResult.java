package me.aap.fermata.addon.stremio.ui.source;

import java.util.Objects;

/** Completion of one gateway mutation. */
public record SourceUiResult(Status status, SourceUiSnapshot snapshot, SourceUiError error) {
	public SourceUiResult {
		Objects.requireNonNull(status, "status");
		error = (error == null) ? SourceUiError.NONE : error;
		if ((status == Status.FAILED) && (error == SourceUiError.NONE)) {
			throw new IllegalArgumentException("Failed result requires an error");
		}
	}

	public static SourceUiResult changed(SourceUiSnapshot snapshot) {
		return new SourceUiResult(Status.CHANGED, Objects.requireNonNull(snapshot),
				SourceUiError.NONE);
	}

	public static SourceUiResult unchanged(SourceUiSnapshot snapshot) {
		return new SourceUiResult(Status.UNCHANGED, Objects.requireNonNull(snapshot),
				SourceUiError.NONE);
	}

	public static SourceUiResult failed(SourceUiError error) {
		return new SourceUiResult(Status.FAILED, null, error);
	}

	public static SourceUiResult cancelled() {
		return new SourceUiResult(Status.CANCELLED, null, SourceUiError.CANCELLED);
	}

	public enum Status {
		CHANGED, UNCHANGED, FAILED, CANCELLED
	}
}
