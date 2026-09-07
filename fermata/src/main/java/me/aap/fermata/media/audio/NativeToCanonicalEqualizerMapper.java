package me.aap.fermata.media.audio;

/** Converts legacy native Equalizer levels into the portable canonical EQ curve. */
public final class NativeToCanonicalEqualizerMapper {
	private NativeToCanonicalEqualizerMapper() {
	}

	public static int[] map(int[] nativeLevelsMillibels, NativeEqualizerTopology topology) {
		if (!topology.containsLevels(nativeLevelsMillibels)) {
			throw new IllegalArgumentException("Legacy Equalizer levels do not match native topology");
		}

		int[] canonical = new int[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
		int[] nativeMillihertz = topology.centerMillihertz();

		for (int band = 0; band < canonical.length; band++) {
			float db = interpolateDb(AudioEffectsProfile.CANONICAL_FREQ_HZ[band] * 1_000,
					nativeMillihertz, nativeLevelsMillibels);
			canonical[band] = clampCanonicalDb(roundCanonicalDb(db));
		}

		return canonical;
	}

	static float interpolateDb(int targetMillihertz, int[] sourceMillihertz,
			int[] sourceLevelsMillibels) {
		if ((sourceMillihertz.length == 0) ||
				(sourceMillihertz.length != sourceLevelsMillibels.length)) {
			throw new IllegalArgumentException("Equalizer topology and levels must match");
		}
		if (targetMillihertz <= sourceMillihertz[0]) return sourceLevelsMillibels[0] / 100F;
		int last = sourceMillihertz.length - 1;
		if (targetMillihertz >= sourceMillihertz[last]) return sourceLevelsMillibels[last] / 100F;

		double target = Math.log(targetMillihertz);
		for (int i = 0; i < last; i++) {
			if ((targetMillihertz < sourceMillihertz[i]) ||
					(targetMillihertz > sourceMillihertz[i + 1])) continue;
			double left = Math.log(sourceMillihertz[i]);
			double right = Math.log(sourceMillihertz[i + 1]);
			double ratio = (target - left) / (right - left);
			return (float) ((sourceLevelsMillibels[i] +
					ratio * (sourceLevelsMillibels[i + 1] - sourceLevelsMillibels[i])) / 100D);
		}

		throw new IllegalArgumentException("Native Equalizer center frequencies must be ordered");
	}

	static int roundCanonicalDb(float db) {
		return (db < 0F) ? -Math.round(-db) : Math.round(db);
	}

	private static int clampCanonicalDb(int db) {
		return Math.max(AudioEffectsProfile.MIN_CANONICAL_DB,
				Math.min(AudioEffectsProfile.MAX_CANONICAL_DB, db));
	}
}
