package me.aap.fermata.diagnostics;

/** Injectable clock used to make event ordering and retention deterministic in tests. */
public interface DiagnosticClock {
	DiagnosticClock SYSTEM = new DiagnosticClock() {
		private final long originNanos = System.nanoTime();

		@Override
		public long wallTimeMillis() {
			return System.currentTimeMillis();
		}

		@Override
		public long elapsedRealtimeMillis() {
			return (System.nanoTime() - originNanos) / 1_000_000L;
		}
	};

	long wallTimeMillis();

	long elapsedRealtimeMillis();
}
