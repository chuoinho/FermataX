package me.aap.fermata.addon.stremio.ui.source;

import java.util.Objects;

/** Safe exceptional completion used when a gateway operation cannot return a result. */
public final class SourceUiFailure extends RuntimeException {
	private final SourceUiError error;

	public SourceUiFailure(SourceUiError error) {
		super(Objects.requireNonNull(error, "error").name());
		this.error = error;
	}

	public SourceUiError error() {
		return error;
	}

	@Override
	public String toString() {
		return "SourceUiFailure[error=" + error + ']';
	}
}
