package me.aap.fermata.ui.policy;

import androidx.annotation.Nullable;

/** Owns Activity presentation attachment without owning or mutating service playback. */
public final class RuntimeSessionCoordinator {
	private long generation;
	@Nullable
	private Token current;

	public synchronized Token attach(Object host, RuntimeHostMode mode) {
		return current = new Token(++generation, host, mode);
	}

	public synchronized boolean detach(@Nullable Token token) {
		if (!isCurrent(token)) return false;
		current = null;
		generation++;
		return true;
	}

	public synchronized boolean isCurrent(@Nullable Token token) {
		return (token != null) && token.equals(current);
	}

	@Nullable
	public synchronized Token getCurrent() {
		return current;
	}

	public record Token(long generation, Object host, RuntimeHostMode mode) {
	}
}
