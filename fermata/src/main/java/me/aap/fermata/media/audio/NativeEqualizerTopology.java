package me.aap.fermata.media.audio;

import java.util.Arrays;

/** Immutable, validated description of the Equalizer topology attached to one native session. */
public final class NativeEqualizerTopology {
	private final int[] centerMillihertz;
	private final int minimumLevelMillibels;
	private final int maximumLevelMillibels;

	private NativeEqualizerTopology(int[] centerMillihertz, int minimumLevelMillibels,
			int maximumLevelMillibels) {
		this.centerMillihertz = centerMillihertz;
		this.minimumLevelMillibels = minimumLevelMillibels;
		this.maximumLevelMillibels = maximumLevelMillibels;
	}

	public static NativeEqualizerTopology create(int[] centerMillihertz, short[] bandLevelRange) {
		if ((centerMillihertz == null) || (centerMillihertz.length == 0) ||
				(bandLevelRange == null) || (bandLevelRange.length < 2) ||
				(bandLevelRange[0] > bandLevelRange[1])) {
			throw new IllegalArgumentException("Invalid native Equalizer topology");
		}

		int[] centers = centerMillihertz.clone();
		for (int band = 0; band < centers.length; band++) {
			if ((centers[band] <= 0) || ((band > 0) && (centers[band - 1] >= centers[band]))) {
				throw new IllegalArgumentException("Native Equalizer center frequencies must increase");
			}
		}

		return new NativeEqualizerTopology(centers, bandLevelRange[0], bandLevelRange[1]);
	}

	public int bandCount() {
		return centerMillihertz.length;
	}

	public int[] centerMillihertz() {
		return centerMillihertz.clone();
	}

	public int minimumLevelMillibels() {
		return minimumLevelMillibels;
	}

	public int maximumLevelMillibels() {
		return maximumLevelMillibels;
	}

	public boolean containsLevels(int[] levelsMillibels) {
		if ((levelsMillibels == null) || (levelsMillibels.length != centerMillihertz.length)) {
			return false;
		}
		for (int level : levelsMillibels) {
			if ((level < minimumLevelMillibels) || (level > maximumLevelMillibels)) return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "NativeEqualizerTopology{" + Arrays.toString(centerMillihertz) + '}';
	}
}
