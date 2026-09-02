package me.aap.fermata.media.audio;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.EnumSet;

import me.aap.utils.log.Log;

/** Applies the unified profile to one real native audio session. */
final class NativeSessionAudioEffectsBackend
		implements AudioEffectsBackend, NativeEqualizerTopologyProvider {
	private static final int EFFECT_PRIORITY = 0;
	private final EnumSet<AudioEffectCapability> capabilities =
			EnumSet.noneOf(AudioEffectCapability.class);
	@Nullable
	private Equalizer equalizer;
	@Nullable
	private final NativeEqualizerTopology equalizerTopology;
	@Nullable
	private BassBoost bassBoost;
	@Nullable
	private LoudnessEnhancer loudnessEnhancer;
	@Nullable
	private Virtualizer virtualizer;
	@Nullable
	private AudioEffect dynamicsProcessing;
	private boolean released;

	@Nullable
	static NativeSessionAudioEffectsBackend create(int audioSessionId) {
		return isValidSessionId(audioSessionId) ? new NativeSessionAudioEffectsBackend(audioSessionId) : null;
	}

	static boolean isValidSessionId(int audioSessionId) {
		return audioSessionId > 0;
	}

	private NativeSessionAudioEffectsBackend(int audioSessionId) {
		equalizer = create(() -> new Equalizer(EFFECT_PRIORITY, audioSessionId));
		if (equalizer != null) capabilities.add(AudioEffectCapability.EQUALIZER);
		equalizerTopology = readTopology(equalizer);

		bassBoost = create(() -> new BassBoost(EFFECT_PRIORITY, audioSessionId));
		if ((bassBoost != null) && strengthSupported(bassBoost)) capabilities.add(AudioEffectCapability.BASS_BOOST);
		else releaseBassBoost();

		loudnessEnhancer = create(() -> new LoudnessEnhancer(audioSessionId));
		if (loudnessEnhancer != null) capabilities.add(AudioEffectCapability.LOUDNESS);

		if (SDK_INT < VANILLA_ICE_CREAM) {
			virtualizer = create(() -> new Virtualizer(EFFECT_PRIORITY, audioSessionId));
			if ((virtualizer != null) && strengthSupported(virtualizer)) {
				capabilities.add(AudioEffectCapability.VIRTUALIZER);
			} else {
				releaseVirtualizer();
			}
		}

		if (SDK_INT >= P) {
			dynamicsProcessing = create(() -> Api28.createDynamicsProcessing(audioSessionId));
			if (dynamicsProcessing != null) capabilities.add(AudioEffectCapability.PREAMP);
		}
	}

	@Override
	@Nullable
	public NativeEqualizerTopology getEqualizerTopology() {
		return equalizerTopology;
	}

	@Override
	public EnumSet<AudioEffectCapability> getCapabilities() {
		return capabilities.clone();
	}

	@Override
	public void apply(AudioEffectsProfile profile) {
		if (released || !profile.enabled()) {
			bypass();
			return;
		}

		GainSafetyPolicy.Decision gainSafety = GainSafetyPolicy.evaluate(profile,
				capabilities.contains(AudioEffectCapability.LIMITER));
		applyEqualizer(profile);
		applyBassBoost(profile);
		applyLoudness(profile, gainSafety);
		applyVirtualizer(profile);
		applyPreamp(profile, gainSafety);
	}

	@Override
	public void bypass() {
		disable(AudioEffectCapability.EQUALIZER, equalizer, this::releaseEqualizer);
		disable(AudioEffectCapability.BASS_BOOST, bassBoost, this::releaseBassBoost);
		disable(AudioEffectCapability.LOUDNESS, loudnessEnhancer, this::releaseLoudnessEnhancer);
		disable(AudioEffectCapability.VIRTUALIZER, virtualizer, this::releaseVirtualizer);
		disable(AudioEffectCapability.PREAMP, dynamicsProcessing, this::releaseDynamicsProcessing);
	}

	@Override
	public void release() {
		if (released) return;
		released = true;
		releaseEqualizer();
		releaseBassBoost();
		releaseLoudnessEnhancer();
		releaseVirtualizer();
		releaseDynamicsProcessing();
		capabilities.clear();
	}

	private void applyEqualizer(AudioEffectsProfile profile) {
		Equalizer effect = equalizer;
		if ((effect == null) || !profile.equalizerEnabled()) {
			disable(AudioEffectCapability.EQUALIZER, effect, this::releaseEqualizer);
			return;
		}

		try {
			short bandCount = effect.getNumberOfBands();
			if (bandCount <= 0) throw new IllegalStateException("Equalizer has no bands");
			int[] centerHz = new int[bandCount];
			for (short band = 0; band < bandCount; band++) {
				centerHz[band] = Math.max(1, effect.getCenterFreq(band) / 1_000);
			}
			short[] levels = NativeEqualizerCurveMapper.mapBandLevels(profile.canonicalCurveDb(),
					centerHz, effect.getBandLevelRange());
			for (short band = 0; band < bandCount; band++) effect.setBandLevel(band, levels[band]);
			effect.setEnabled(true);
		} catch (RuntimeException error) {
			fail(AudioEffectCapability.EQUALIZER, error, this::releaseEqualizer);
		}
	}

	@Nullable
	private static NativeEqualizerTopology readTopology(@Nullable Equalizer equalizer) {
		if (equalizer == null) return null;
		try {
			short bandCount = equalizer.getNumberOfBands();
			if (bandCount <= 0) return null;
			int[] centerMillihertz = new int[bandCount];
			for (short band = 0; band < bandCount; band++) {
				centerMillihertz[band] = equalizer.getCenterFreq(band);
			}
			return NativeEqualizerTopology.create(centerMillihertz, equalizer.getBandLevelRange());
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private void applyBassBoost(AudioEffectsProfile profile) {
		BassBoost effect = bassBoost;
		if ((effect == null) || !profile.bassBoostEnabled()) {
			disable(AudioEffectCapability.BASS_BOOST, effect, this::releaseBassBoost);
			return;
		}

		try {
			effect.setStrength((short) clampStrength(profile.bassBoostStrength()));
			effect.setEnabled(true);
		} catch (RuntimeException error) {
			fail(AudioEffectCapability.BASS_BOOST, error, this::releaseBassBoost);
		}
	}

	private void applyLoudness(AudioEffectsProfile profile, GainSafetyPolicy.Decision gainSafety) {
		LoudnessEnhancer effect = loudnessEnhancer;
		if ((effect == null) || !profile.loudnessEnabled() || !gainSafety.isLoudnessAllowed()) {
			disable(AudioEffectCapability.LOUDNESS, effect, this::releaseLoudnessEnhancer);
			return;
		}

		try {
			effect.setTargetGain(clampStrength(profile.loudnessGain()) * 10);
			effect.setEnabled(true);
		} catch (RuntimeException error) {
			fail(AudioEffectCapability.LOUDNESS, error, this::releaseLoudnessEnhancer);
		}
	}

	private void applyVirtualizer(AudioEffectsProfile profile) {
		Virtualizer effect = virtualizer;
		if ((effect == null) || !profile.virtualizerEnabled()) {
			disable(AudioEffectCapability.VIRTUALIZER, effect, this::releaseVirtualizer);
			return;
		}

		try {
			effect.setStrength((short) clampStrength(profile.virtualizerStrength()));
			if (!effect.forceVirtualizationMode(profile.virtualizerMode())) {
				throw new IllegalStateException("Virtualization mode is unsupported");
			}
			effect.setEnabled(true);
		} catch (RuntimeException error) {
			fail(AudioEffectCapability.VIRTUALIZER, error, this::releaseVirtualizer);
		}
	}

	private void applyPreamp(AudioEffectsProfile profile, GainSafetyPolicy.Decision gainSafety) {
		AudioEffect effect = dynamicsProcessing;
		if ((effect == null) || (profile.preampDb() == 0) || !gainSafety.isPreampAllowed()) {
			disable(AudioEffectCapability.PREAMP, effect, this::releaseDynamicsProcessing);
			return;
		}

		try {
			Api28.setInputGain(effect, profile.preampDb());
			effect.setEnabled(true);
		} catch (RuntimeException error) {
			fail(AudioEffectCapability.PREAMP, error, this::releaseDynamicsProcessing);
		}
	}

	private void disable(AudioEffectCapability capability, @Nullable AudioEffect effect, Runnable release) {
		if (effect == null) return;
		try {
			effect.setEnabled(false);
		} catch (RuntimeException error) {
			capabilities.remove(capability);
			Log.w(error, "Failed to bypass native audio effect: ", capability);
			release.run();
		}
	}

	private void fail(AudioEffectCapability capability, RuntimeException error, Runnable release) {
		capabilities.remove(capability);
		Log.w(error, "Failed to configure native audio effect: ", capability);
		release.run();
	}

	private void releaseEqualizer() {
		Equalizer effect = equalizer;
		equalizer = null;
		release(effect);
	}

	private void releaseBassBoost() {
		BassBoost effect = bassBoost;
		bassBoost = null;
		release(effect);
	}

	private void releaseLoudnessEnhancer() {
		LoudnessEnhancer effect = loudnessEnhancer;
		loudnessEnhancer = null;
		release(effect);
	}

	private void releaseVirtualizer() {
		Virtualizer effect = virtualizer;
		virtualizer = null;
		release(effect);
	}

	private void releaseDynamicsProcessing() {
		AudioEffect effect = dynamicsProcessing;
		dynamicsProcessing = null;
		release(effect);
	}

	private static boolean strengthSupported(BassBoost effect) {
		try {
			return effect.getStrengthSupported();
		} catch (RuntimeException error) {
			return false;
		}
	}

	private static boolean strengthSupported(Virtualizer effect) {
		try {
			return effect.getStrengthSupported();
		} catch (RuntimeException error) {
			return false;
		}
	}

	private static int clampStrength(int strength) {
		return Math.max(0, Math.min(1_000, strength));
	}

	@Nullable
	private static <T extends AudioEffect> T create(EffectFactory<T> factory) {
		try {
			return factory.create();
		} catch (RuntimeException error) {
			Log.d(error, "Native audio effect is unavailable");
			return null;
		}
	}

	private static void release(@Nullable AudioEffect effect) {
		if (effect == null) return;
		try {
			effect.release();
		} catch (RuntimeException ignored) {
			// Releasing one failed effect must not retain another effect resource.
		}
	}

	private interface EffectFactory<T extends AudioEffect> {
		T create();
	}

	@RequiresApi(P)
	private static final class Api28 {
		static AudioEffect createDynamicsProcessing(int audioSessionId) {
			return new android.media.audiofx.DynamicsProcessing(audioSessionId);
		}

		static void setInputGain(AudioEffect effect, float gainDb) {
			((android.media.audiofx.DynamicsProcessing) effect).setInputGainAllChannelsTo(gainDb);
		}
	}
}
