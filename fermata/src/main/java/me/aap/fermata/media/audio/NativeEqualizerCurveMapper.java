package me.aap.fermata.media.audio;

/** Maps the portable curve to the hardware Equalizer topology without assuming its band count. */
public final class NativeEqualizerCurveMapper {
	private NativeEqualizerCurveMapper() {
	}

	public static short[] mapBandLevels(int[] canonicalCurveDb, int[] nativeCenterHz,
			short[] nativeBandLevelRange) {
		if (canonicalCurveDb.length != AudioEffectsProfile.CANONICAL_FREQ_HZ.length) {
			throw new IllegalArgumentException("Unexpected canonical Equalizer curve");
		}
		if ((nativeCenterHz.length == 0) || (nativeBandLevelRange == null) ||
				(nativeBandLevelRange.length < 2)) return new short[0];

		int min = Math.min(nativeBandLevelRange[0], nativeBandLevelRange[1]);
		int max = Math.max(nativeBandLevelRange[0], nativeBandLevelRange[1]);
		short[] levels = new short[nativeCenterHz.length];

		for (int band = 0; band < levels.length; band++) {
			int millibels = Math.round(interpolateDb(nativeCenterHz[band],
					AudioEffectsProfile.CANONICAL_FREQ_HZ, canonicalCurveDb) * 100F);
			levels[band] = (short) Math.max(min, Math.min(max, millibels));
		}

		return levels;
	}

	public static float interpolateDb(int targetHz, int[] sourceHz, int[] sourceDb) {
		if ((sourceHz.length == 0) || (sourceHz.length != sourceDb.length)) {
			throw new IllegalArgumentException("Equalizer curve frequencies and gains must match");
		}
		if (targetHz <= sourceHz[0]) return sourceDb[0];
		int last = sourceHz.length - 1;
		if (targetHz >= sourceHz[last]) return sourceDb[last];

		double target = Math.log(targetHz);
		for (int i = 0; i < last; i++) {
			if ((targetHz < sourceHz[i]) || (targetHz > sourceHz[i + 1])) continue;
			double left = Math.log(sourceHz[i]);
			double right = Math.log(sourceHz[i + 1]);
			double ratio = (target - left) / (right - left);
			return (float) (sourceDb[i] + ratio * (sourceDb[i + 1] - sourceDb[i]));
		}

		throw new IllegalArgumentException("Canonical frequencies must be ordered");
	}
}
