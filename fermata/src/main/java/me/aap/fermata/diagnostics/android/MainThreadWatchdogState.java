package me.aap.fermata.diagnostics.android;

final class MainThreadWatchdogState {
	private final int requiredMisses;
	private final long reportIntervalMillis;
	private long nextProbeId;
	private long outstandingProbeId;
	private long outstandingProbeSentAt;
	private long lastAcknowledgedProbeId;
	private long stallStartedAt = -1L;
	private long lastReportAt = Long.MIN_VALUE;
	private int consecutiveMisses;

	MainThreadWatchdogState(int requiredMisses, long reportIntervalMillis) {
		if (requiredMisses < 2) throw new IllegalArgumentException("requiredMisses must be >= 2");
		if (reportIntervalMillis <= 0L) {
			throw new IllegalArgumentException("reportIntervalMillis must be positive");
		}
		this.requiredMisses = requiredMisses;
		this.reportIntervalMillis = reportIntervalMillis;
	}

	synchronized Tick onTick(long now, boolean eligible) {
		if (!eligible) {
			resetProbeState();
			return Tick.INACTIVE;
		}

		if ((outstandingProbeId != 0L) &&
				(lastAcknowledgedProbeId < outstandingProbeId)) {
			consecutiveMisses++;
			if (stallStartedAt < 0L) stallStartedAt = outstandingProbeSentAt;
		} else {
			consecutiveMisses = 0;
			stallStartedAt = -1L;
		}

		long probeId = ++nextProbeId;
		outstandingProbeId = probeId;
		outstandingProbeSentAt = now;
		boolean report = (consecutiveMisses >= requiredMisses) && canReport(now);
		if (report) lastReportAt = now;
		long stalledFor = (stallStartedAt < 0L) ? 0L : Math.max(0L, now - stallStartedAt);
		return new Tick(probeId, consecutiveMisses, stalledFor, report);
	}

	synchronized void acknowledge(long probeId) {
		if (probeId > lastAcknowledgedProbeId) lastAcknowledgedProbeId = probeId;
	}

	synchronized void deactivate() {
		resetProbeState();
	}

	private boolean canReport(long now) {
		return (lastReportAt == Long.MIN_VALUE) || (now < lastReportAt) ||
				((now - lastReportAt) >= reportIntervalMillis);
	}

	private void resetProbeState() {
		outstandingProbeId = 0L;
		outstandingProbeSentAt = 0L;
		lastAcknowledgedProbeId = nextProbeId;
		stallStartedAt = -1L;
		consecutiveMisses = 0;
	}

	static final class Tick {
		static final Tick INACTIVE = new Tick(0L, 0, 0L, false);

		private final long probeId;
		private final int missedProbes;
		private final long stalledForMillis;
		private final boolean reportStall;

		Tick(long probeId, int missedProbes, long stalledForMillis, boolean reportStall) {
			this.probeId = probeId;
			this.missedProbes = missedProbes;
			this.stalledForMillis = stalledForMillis;
			this.reportStall = reportStall;
		}

		long getProbeId() {
			return probeId;
		}

		int getMissedProbes() {
			return missedProbes;
		}

		long getStalledForMillis() {
			return stalledForMillis;
		}

		boolean shouldReportStall() {
			return reportStall;
		}
	}
}
