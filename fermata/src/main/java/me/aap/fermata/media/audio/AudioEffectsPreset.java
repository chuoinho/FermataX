package me.aap.fermata.media.audio;

import java.util.Arrays;

/** Small immutable set of Fermata tonal suggestions on the canonical 31 Hz-16 kHz curve. */
public enum AudioEffectsPreset {
	FLAT(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
	POP(new int[]{2, 2, 2, 1, 0, -1, -1, 0, 1, 1}),
	ROCK(new int[]{2, 2, 1, -1, -1, 0, 1, 2, 2, 2}),
	CLASSICAL(new int[]{1, 1, 0, 0, 0, 1, 2, 2, 1, 1}),
	DANCE(new int[]{3, 2, 1, 0, 0, -1, -1, 1, 2, 2}),
	CUSTOM(null);

	private final int[] curveDb;

	AudioEffectsPreset(int[] curveDb) {
		this.curveDb = curveDb == null ? null : curveDb.clone();
	}

	public boolean hasCurve() {
		return curveDb != null;
	}

	public int[] curveDb() {
		return curveDb == null ? null : curveDb.clone();
	}

	public int maximumBoostDb() {
		if (curveDb == null) return 0;
		int maximum = 0;
		for (int gain : curveDb) maximum = Math.max(maximum, gain);
		return maximum;
	}

	public static AudioEffectsPreset match(int[] curveDb) {
		for (AudioEffectsPreset preset : values()) {
			if (preset.hasCurve() && Arrays.equals(preset.curveDb, curveDb)) return preset;
		}
		return CUSTOM;
	}
}
