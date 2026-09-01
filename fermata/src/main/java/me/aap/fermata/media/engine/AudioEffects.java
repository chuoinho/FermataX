package me.aap.fermata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class AudioEffects {
	private static final byte EQUALIZER = 1;
	private static final byte VIRTUALIZER = 2;
	private static final byte BASS_BOOST = 4;
	private static final byte LOUDNESS_ENHANCER = 8;
	private static final byte supported;
	private final Equalizer equalizer;
	private final Virtualizer virtualizer;
	private final BassBoost bassBoost;
	private final LoudnessEnhancer loudnessEnhancer;

	static {
		byte s = 0;
		for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
			if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) s |= EQUALIZER;
			else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) s |= VIRTUALIZER;
			else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) s |= BASS_BOOST;
			else if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) s |= LOUDNESS_ENHANCER;
		}
		supported = s;
	}

	private AudioEffects(@Nullable Equalizer equalizer, @Nullable Virtualizer virtualizer,
			@Nullable BassBoost bassBoost, @Nullable LoudnessEnhancer loudnessEnhancer) {
		this.equalizer = equalizer;
		this.virtualizer = virtualizer;
		this.bassBoost = bassBoost;
		this.loudnessEnhancer = loudnessEnhancer;
	}

	private static boolean supported(byte type) {
		return (supported & type) != 0;
	}

	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (supported == 0) return null;

		try {
			return createOnce(priority, audioSessionId);
		} catch (Exception ex) {
			// Sometimes it fails with RuntimeException: AudioEffect: set/get parameter error
			Log.w("Failed to create AudioEffects - retrying...");

			try {
				Thread.sleep(300);
				return createOnce(priority, audioSessionId);
			} catch (Exception ex1) {
				Log.e(ex1, "Failed to create AudioEffects");
				return null;
			}
		}
	}

	private static AudioEffects createOnce(int priority, int audioSessionId) {
		CreationTransaction transaction = new CreationTransaction();
		try {
			Equalizer equalizer = supported(EQUALIZER) ? transaction.acquire(
					() -> new Equalizer(priority, audioSessionId), AudioEffect::release) : null;
			Virtualizer virtualizer = (SDK_INT < VANILLA_ICE_CREAM) && supported(VIRTUALIZER) ?
					transaction.acquire(() -> new Virtualizer(priority, audioSessionId),
							AudioEffect::release) : null;
			BassBoost bassBoost = supported(BASS_BOOST) ? transaction.acquire(
					() -> new BassBoost(priority, audioSessionId), AudioEffect::release) : null;
			LoudnessEnhancer loudnessEnhancer = supported(LOUDNESS_ENHANCER) ?
					transaction.acquire(() -> new LoudnessEnhancer(audioSessionId),
							AudioEffect::release) : null;
			AudioEffects effects = new AudioEffects(equalizer, virtualizer, bassBoost,
					loudnessEnhancer);
			transaction.commit();
			return effects;
		} finally {
			transaction.rollback();
		}
	}

	static final class CreationTransaction {
		private final List<Runnable> rollbacks = new ArrayList<>();
		private boolean committed;

		<T> T acquire(Supplier<T> creator, Consumer<T> releaser) {
			T resource = creator.get();
			rollbacks.add(() -> releaseQuietly(resource, releaser));
			return resource;
		}

		void commit() {
			committed = true;
			rollbacks.clear();
		}

		void rollback() {
			if (committed) return;
			for (int i = rollbacks.size() - 1; i >= 0; i--) rollbacks.get(i).run();
			rollbacks.clear();
		}

		private static <T> void releaseQuietly(T resource, Consumer<T> releaser) {
			try {
				releaser.accept(resource);
			} catch (RuntimeException ignored) {
				// Releasing one partially created effect must not retain another one.
			}
		}
	}

	@Nullable
	public Equalizer getEqualizer() {
		return equalizer;
	}

	@Nullable
	public Virtualizer getVirtualizer() {
		return virtualizer;
	}

	@Nullable
	public BassBoost getBassBoost() {
		return bassBoost;
	}

	@Nullable
	public LoudnessEnhancer getLoudnessEnhancer() {
		return loudnessEnhancer;
	}

	public void release() {
		releaseQuietly(equalizer);
		releaseQuietly(virtualizer);
		releaseQuietly(bassBoost);
		releaseQuietly(loudnessEnhancer);
	}

	private static void releaseQuietly(@Nullable AudioEffect effect) {
		if (effect == null) return;
		try {
			effect.release();
		} catch (RuntimeException ignored) {
			// Releasing one effect must not retain another one.
		}
	}
}
