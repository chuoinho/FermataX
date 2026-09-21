package me.aap.fermata.auto;

import java.util.Objects;

/** Typed in-process request. The controller replaces its token before dispatch. */
public record OpenOnCarRequest(OpenOnCarKind kind, int addonId, Object payload,
		OpenOnCarToken token) {
	public OpenOnCarRequest {
		Objects.requireNonNull(kind);
		Objects.requireNonNull(token);
	}

	OpenOnCarRequest stamp(OpenOnCarToken token) {
		return new OpenOnCarRequest(kind, addonId, payload, token);
	}
}
