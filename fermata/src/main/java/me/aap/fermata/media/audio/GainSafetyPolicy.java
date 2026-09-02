package me.aap.fermata.media.audio;

/**
 * Keeps gain-producing effects from stacking without a configured, active limiter. EQ-2 does
 * not yet ship tuned limiter settings, so the production backend reports no limiter capability.
 */
public final class GainSafetyPolicy {
	private GainSafetyPolicy() {
	}

	public static Decision evaluate(AudioEffectsProfile profile, boolean limiterAvailable) {
		if (limiterAvailable) return Decision.SAFE;
		boolean positivePreamp = profile.preampDb() > 0;
		boolean loudness = profile.loudnessEnabled() && (profile.loudnessGain() > 0);

		if (positivePreamp) {
			return new Decision(false, !loudness, Reason.POSITIVE_PREAMP_REQUIRES_LIMITER);
		}
		if (loudness && profile.equalizerEnabled() &&
				(maximumBandGain(profile.canonicalCurveDb()) > 0)) {
			return new Decision(true, false, Reason.LOUDNESS_EQ_STACK_REQUIRES_LIMITER);
		}
		return Decision.SAFE;
	}

	private static int maximumBandGain(int[] curveDb) {
		int maximum = 0;
		for (int gain : curveDb) maximum = Math.max(maximum, gain);
		return maximum;
	}

	public enum Reason {
		SAFE,
		POSITIVE_PREAMP_REQUIRES_LIMITER,
		LOUDNESS_EQ_STACK_REQUIRES_LIMITER
	}

	public static final class Decision {
		static final Decision SAFE = new Decision(true, true, Reason.SAFE);
		private final boolean preampAllowed;
		private final boolean loudnessAllowed;
		private final Reason reason;

		Decision(boolean preampAllowed, boolean loudnessAllowed, Reason reason) {
			this.preampAllowed = preampAllowed;
			this.loudnessAllowed = loudnessAllowed;
			this.reason = reason;
		}

		public boolean isPreampAllowed() {
			return preampAllowed;
		}

		public boolean isLoudnessAllowed() {
			return loudnessAllowed;
		}

		public Reason getReason() {
			return reason;
		}
	}
}
