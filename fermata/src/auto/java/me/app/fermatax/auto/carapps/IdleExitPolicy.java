package me.app.fermatax.auto.carapps;

/** When the root server may leave: only after a quiet period with no client attached. */
public final class IdleExitPolicy {

	public static final long IDLE_LIMIT_MS = 600_000L;

	private IdleExitPolicy() {
	}

	public static boolean shouldExit(long nowMs, long lastClientDetachMs, boolean clientAttached) {
		return !clientAttached && nowMs - lastClientDetachMs >= IDLE_LIMIT_MS;
	}
}
