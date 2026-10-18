package me.aap.fermata.media.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Thread-safe volume fader that smoothly interpolates audio volume over a specified duration
 * (by default 150ms) using a {@link Handler} and {@link Consumer}.
 *
 * Designed to prevent abrupt volume snapping and popping during rapid automotive audio ducking/unducking.
 */
public class AudioVolumeFader {
	public static final long DEFAULT_FADE_DURATION_MS = 150L;
	public static final long DEFAULT_STEP_INTERVAL_MS = 15L;
	public static final float DUCK_VOLUME = 0.2f;
	public static final float NORMAL_VOLUME = 1.0f;
	public static final float MUTED_VOLUME = 0.0f;

	private final Handler handler;
	private final Consumer<Float> volumeSetter;
	private final LongSupplier timeProvider;
	private final long stepIntervalMs;
	private final Object lock = new Object();

	private float currentVolume;
	private float preDuckVolume = NORMAL_VOLUME;
	private boolean isDucked = false;
	private boolean muted = false;
	private FadeTask activeFade;

	public AudioVolumeFader(@NonNull Consumer<Float> volumeSetter) {
		this(new Handler(Looper.getMainLooper()), volumeSetter, NORMAL_VOLUME);
	}

	public AudioVolumeFader(@NonNull Handler handler, @NonNull Consumer<Float> volumeSetter) {
		this(handler, volumeSetter, NORMAL_VOLUME);
	}

	public AudioVolumeFader(@NonNull Handler handler, @NonNull Consumer<Float> volumeSetter, float initialVolume) {
		this(handler, volumeSetter, initialVolume, SystemClock::uptimeMillis, DEFAULT_STEP_INTERVAL_MS);
	}

	AudioVolumeFader(@NonNull Handler handler, @NonNull Consumer<Float> volumeSetter, float initialVolume,
	                 @Nullable LongSupplier timeProvider, long stepIntervalMs) {
		this.handler = Objects.requireNonNull(handler, "handler must not be null");
		this.volumeSetter = Objects.requireNonNull(volumeSetter, "volumeSetter must not be null");
		this.currentVolume = clamp(initialVolume);
		this.preDuckVolume = this.currentVolume;
		this.muted = (this.currentVolume <= 0.001f);
		this.timeProvider = (timeProvider != null) ? timeProvider : SystemClock::uptimeMillis;
		this.stepIntervalMs = (stepIntervalMs > 0) ? stepIntervalMs : DEFAULT_STEP_INTERVAL_MS;
	}

	/**
	 * Convenience static helper to fade from a specified starting volume to a target volume.
	 */
	public static AudioVolumeFader fade(@NonNull Handler handler, float fromVolume, float toVolume,
	                                    long durationMs, @NonNull Consumer<Float> volumeSetter) {
		float clampedFrom = clamp(fromVolume);
		AudioVolumeFader fader = new AudioVolumeFader(handler, volumeSetter, clampedFrom);
		volumeSetter.accept(clampedFrom);
		fader.fadeTo(toVolume, durationMs);
		return fader;
	}

	/**
	 * Clamps volume to [0.0f, 1.0f].
	 */
	public static float clamp(float volume) {
		if (Float.isNaN(volume)) return 0.0f;
		if (volume < 0.0f) return 0.0f;
		if (volume > 1.0f) return 1.0f;
		return volume;
	}

	/**
	 * Returns current volume clamped to [0.0f, 1.0f].
	 */
	public float getCurrentVolume() {
		synchronized (lock) {
			return currentVolume;
		}
	}

	/**
	 * Immediately cancels any active fade and sets volume directly.
	 */
	public void setCurrentVolume(float volume) {
		float clamped = clamp(volume);
		synchronized (lock) {
			cancelInternal();
			currentVolume = clamped;
			muted = (clamped <= 0.001f);
			if (!isDucked) {
				preDuckVolume = clamped;
			}
			volumeSetter.accept(clamped);
		}
	}

	/**
	 * Cancels any pending or in-flight fade, keeping current volume at its present level.
	 */
	public void cancel() {
		synchronized (lock) {
			cancelInternal();
		}
	}

	private void cancelInternal() {
		if (activeFade != null) {
			handler.removeCallbacks(activeFade);
			activeFade.cancel();
			activeFade = null;
		}
	}

	/**
	 * Fades volume from current level to {@code targetVolume} over {@link #DEFAULT_FADE_DURATION_MS}.
	 */
	public void fadeTo(float targetVolume) {
		fadeTo(targetVolume, DEFAULT_FADE_DURATION_MS);
	}

	/**
	 * Fades volume from current level to {@code targetVolume} over {@code durationMs}.
	 * If a fade is already in progress, it is smoothly cancelled and the new ramp begins
	 * from the current interpolated volume.
	 */
	public void fadeTo(float targetVolume, long durationMs) {
		float clampedTarget = clamp(targetVolume);
		synchronized (lock) {
			cancelInternal();
			if (clampedTarget > 0.001f) {
				muted = false;
			}
			float start = currentVolume;
			if (durationMs <= 0 || Float.compare(start, clampedTarget) == 0) {
				currentVolume = clampedTarget;
				muted = (clampedTarget <= 0.001f);
				volumeSetter.accept(clampedTarget);
				return;
			}

			activeFade = new FadeTask(start, clampedTarget, durationMs);
			long initialDelay = Math.min(stepIntervalMs, durationMs);
			handler.postDelayed(activeFade, initialDelay);
		}
	}

	/**
	 * Smoothly ducks volume to {@link #DUCK_VOLUME} over {@link #DEFAULT_FADE_DURATION_MS}.
	 * If pre-duck volume was muted (<= 0.001f), remains muted.
	 */
	public void duck() {
		duck(DUCK_VOLUME, DEFAULT_FADE_DURATION_MS);
	}

	public void duck(float duckVolume, long durationMs) {
		synchronized (lock) {
			if (!isDucked) {
				if (muted) {
					preDuckVolume = MUTED_VOLUME;
				} else if (activeFade != null && activeFade.getTargetVolume() > currentVolume) {
					preDuckVolume = Math.max(currentVolume, activeFade.getTargetVolume());
				} else {
					preDuckVolume = currentVolume;
				}
				isDucked = true;
			}
			if (muted || preDuckVolume <= 0.001f) {
				cancelInternal();
				return;
			}
			float target = Math.min(preDuckVolume, clamp(duckVolume));
			fadeTo(target, durationMs);
		}
	}

	/**
	 * Smoothly restores volume to pre-duck level over {@link #DEFAULT_FADE_DURATION_MS}.
	 * If pre-duck volume was muted (<= 0.001f), does NOT auto-unmute.
	 */
	public void unduck() {
		unduck(DEFAULT_FADE_DURATION_MS);
	}

	public void unduck(long durationMs) {
		synchronized (lock) {
			if (!isDucked) return;
			isDucked = false;
			if (muted || preDuckVolume <= 0.001f) {
				return;
			}
			fadeTo(preDuckVolume, durationMs);
		}
	}

	public void mute() {
		synchronized (lock) {
			isDucked = false;
			muted = true;
			setCurrentVolume(MUTED_VOLUME);
		}
	}

	public void unmute() {
		synchronized (lock) {
			isDucked = false;
			muted = false;
			fadeTo(NORMAL_VOLUME, DEFAULT_FADE_DURATION_MS);
		}
	}

	public boolean isDucked() {
		synchronized (lock) {
			return isDucked;
		}
	}

	public boolean isMuted() {
		synchronized (lock) {
			return muted;
		}
	}

	public float getPreDuckVolume() {
		synchronized (lock) {
			return preDuckVolume;
		}
	}

	private final class FadeTask implements Runnable {
		private final float startVolume;
		private final float targetVolume;
		private final long durationMs;
		private final long startTime;
		private volatile boolean cancelled;

		FadeTask(float startVolume, float targetVolume, long durationMs) {
			this.startVolume = startVolume;
			this.targetVolume = targetVolume;
			this.durationMs = durationMs;
			this.startTime = timeProvider.getAsLong();
		}

		float getTargetVolume() {
			return targetVolume;
		}

		void cancel() {
			cancelled = true;
		}

		@Override
		public void run() {
			synchronized (lock) {
				if (cancelled || activeFade != this) {
					return;
				}

				long now = timeProvider.getAsLong();
				long elapsed = Math.max(0L, now - startTime);

				if (elapsed >= durationMs) {
					currentVolume = targetVolume;
					muted = (targetVolume <= 0.001f);
					activeFade = null;
					volumeSetter.accept(targetVolume);
					return;
				}

				float fraction = Math.min(1.0f, Math.max(0.0f, (float) elapsed / (float) durationMs));
				currentVolume = startVolume + (targetVolume - startVolume) * fraction;
				volumeSetter.accept(currentVolume);

				long remaining = durationMs - elapsed;
				long nextDelay = Math.min(stepIntervalMs, Math.max(1L, remaining));
				handler.postDelayed(this, nextDelay);
			}
		}
	}
}
