package me.aap.fermata.addon.web.audio;

import java.util.Locale;

import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.GainSafetyPolicy;

/**
 * Universal WebAudio profile projection of the global audio-effects profile
 * shared across YouTube, Stremio, and Generic Web Browser addons.
 */
public final class WebAudioProfile {
	public static final int VERSION = 1;
	public static final int MIN_PREAMP_DB = -60;
	private final boolean masterEnabled;
	private final boolean equalizerEnabled;
	private final int[] bandsDb;
	private final int preampDb;

	public WebAudioProfile(boolean masterEnabled, boolean equalizerEnabled, int[] bandsDb, int preampDb) {
		this.masterEnabled = masterEnabled;
		this.equalizerEnabled = equalizerEnabled;
		this.bandsDb = bandsDb;
		this.preampDb = preampDb;
	}

	public static WebAudioProfile from(AudioEffectsProfile profile) {
		if ((profile == null) || (profile.schemaVersion() != AudioEffectsProfile.SCHEMA_VERSION)) {
			return unity();
		}
		int[] source = profile.canonicalCurveDb();
		if (source.length != AudioEffectsProfile.CANONICAL_FREQ_HZ.length) return unity();
		int[] bands = new int[source.length];
		for (int i = 0; i < bands.length; i++) {
			bands[i] = clamp(source[i], AudioEffectsProfile.MIN_CANONICAL_DB, AudioEffectsProfile.MAX_CANONICAL_DB);
		}
		int preamp = profile.preampDb();
		if (!GainSafetyPolicy.evaluate(profile, false).isPreampAllowed() || (preamp > 0)) preamp = 0;
		preamp = clamp(preamp, MIN_PREAMP_DB, 0);
		return new WebAudioProfile(profile.enabled(), profile.equalizerEnabled(), bands, preamp);
	}

	public static WebAudioProfile unity() {
		return new WebAudioProfile(false, false, AudioEffectsProfile.flatCurveDb(), 0);
	}

	public boolean masterEnabled() {
		return masterEnabled;
	}

	public boolean equalizerEnabled() {
		return equalizerEnabled;
	}

	public int[] bandsDb() {
		return bandsDb.clone();
	}

	public int preampDb() {
		return preampDb;
	}

	public String toJavascriptObject() {
		StringBuilder values = new StringBuilder(48);
		for (int i = 0; i < bandsDb.length; i++) {
			if (i != 0) values.append(',');
			values.append(bandsDb[i]);
		}
		return String.format(Locale.ROOT, "{v:%d,m:%b,e:%b,b:[%s],p:%d}", VERSION,
				masterEnabled, equalizerEnabled, values, preampDb);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
