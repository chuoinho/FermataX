package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record UnsupportedStreamTarget(Reason reason) implements StreamTarget {
	public UnsupportedStreamTarget {
		Objects.requireNonNull(reason, "reason");
	}

	public enum Reason {
		MISSING_TARGET,
		MULTIPLE_TARGETS,
		INVALID_TARGET
	}
}
