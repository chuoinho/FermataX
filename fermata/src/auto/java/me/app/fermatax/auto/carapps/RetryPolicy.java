package me.app.fermatax.auto.carapps;

/**
 * Connection retry policy with exponential backoff for the CarDisplayServer process.
 */
public final class RetryPolicy {

	private RetryPolicy() {
	}

	public static long delayMs(int attempt) {
		if (attempt == 0) return 1000L;
		if (attempt == 1) return 2000L;
		if (attempt == 2) return 4000L;
		return -1L;
	}
}
