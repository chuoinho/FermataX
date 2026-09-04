package me.aap.fermata.addon.web.yt;

import java.util.Locale;

import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.GainSafetyPolicy;

/** Bounded YouTube WebAudio projection of the one global audio-effects profile. */
final class YoutubeWebAudioProfile {
	static final int VERSION = 1;
	static final int MIN_PREAMP_DB = -60;
	private final boolean masterEnabled;
	private final boolean equalizerEnabled;
	private final int[] bandsDb;
	private final int preampDb;

	private YoutubeWebAudioProfile(boolean masterEnabled, boolean equalizerEnabled,
			int[] bandsDb, int preampDb) {
		this.masterEnabled = masterEnabled;
		this.equalizerEnabled = equalizerEnabled;
		this.bandsDb = bandsDb;
		this.preampDb = preampDb;
	}

	static YoutubeWebAudioProfile from(AudioEffectsProfile profile) {
		if ((profile == null) || (profile.schemaVersion() != AudioEffectsProfile.SCHEMA_VERSION)) {
			return unity();
		}
		int[] source = profile.canonicalCurveDb();
		if (source.length != AudioEffectsProfile.CANONICAL_FREQ_HZ.length) return unity();
		int[] bands = new int[source.length];
		for (int i = 0; i < bands.length; i++) {
			bands[i] = clamp(source[i], AudioEffectsProfile.MIN_CANONICAL_DB,
					AudioEffectsProfile.MAX_CANONICAL_DB);
		}
		int preamp = profile.preampDb();
		if (!GainSafetyPolicy.evaluate(profile, false).isPreampAllowed() || (preamp > 0)) preamp = 0;
		return new YoutubeWebAudioProfile(profile.enabled(), profile.equalizerEnabled(), bands,
				clamp(preamp, MIN_PREAMP_DB, 0));
	}

	static YoutubeWebAudioProfile unity() {
		return new YoutubeWebAudioProfile(false, false, AudioEffectsProfile.flatCurveDb(), 0);
	}

	boolean isProcessingEnabled() {
		return masterEnabled && equalizerEnabled;
	}

	int[] bandsDb() {
		return bandsDb.clone();
	}

	int preampDb() {
		return preampDb;
	}

	double preampGain() {
		return Math.pow(10D, preampDb / 20D);
	}

	String toJavascriptObject() {
		StringBuilder values = new StringBuilder(48);
		for (int i = 0; i < bandsDb.length; i++) {
			if (i != 0) values.append(',');
			values.append(bandsDb[i]);
		}
		return String.format(Locale.ROOT, "{v:%d,m:%b,e:%b,b:[%s],p:%d}", VERSION,
				masterEnabled, equalizerEnabled, values, preampDb);
	}

	static boolean isValidWireProfile(int version, boolean masterEnabled, boolean equalizerEnabled,
			double[] bands, double preampDb) {
		if ((version != VERSION) || (bands == null) ||
				(bands.length != AudioEffectsProfile.CANONICAL_FREQ_HZ.length) ||
				!Double.isFinite(preampDb) || (preampDb < MIN_PREAMP_DB) || (preampDb > 0)) {
			return false;
		}
		for (double band : bands) {
			if (!Double.isFinite(band) || (band < AudioEffectsProfile.MIN_CANONICAL_DB) ||
					(band > AudioEffectsProfile.MAX_CANONICAL_DB)) return false;
		}
		return true;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
