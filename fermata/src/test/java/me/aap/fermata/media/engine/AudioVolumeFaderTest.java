package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class AudioVolumeFaderTest {
	private Handler handler;

	@Before
	public void setUp() {
		handler = new Handler(Looper.getMainLooper());
	}

	private void advanceTime(long millis) {
		advanceTimeWithClock(millis, null);
	}

	private void advanceTimeWithClock(long millis, long[] timeHolder) {
		long step = 15L;
		for (long elapsed = 0; elapsed < millis; elapsed += step) {
			long increment = Math.min(step, millis - elapsed);
			if (timeHolder != null) {
				timeHolder[0] += increment;
			}
			ShadowLooper.idleMainLooper(increment, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	public void linearStepInterpolationOver150ms() {
		List<Float> recordedVolumes = new ArrayList<>();
		AudioVolumeFader fader = new AudioVolumeFader(handler, recordedVolumes::add, 1.0f);

		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);

		fader.fadeTo(0.2f, 150L);

		advanceTime(150L);

		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);
		assertTrue("Expected multiple intermediate interpolation steps", recordedVolumes.size() >= 5);

		// Verify monotonic linear decrease
		float prev = 1.0f;
		for (float vol : recordedVolumes) {
			assertTrue("Volume should decrease monotonically: " + vol + " <= " + prev, vol <= prev + 0.001f);
			assertTrue("Volume should be >= 0.2f: " + vol, vol >= 0.199f);
			prev = vol;
		}
		assertEquals(0.2f, recordedVolumes.get(recordedVolumes.size() - 1), 0.001f);
	}

	@Test
	public void stepInterpolationProgression() {
		List<Float> recordedVolumes = new ArrayList<>();
		long[] timeHolder = new long[]{1000L};
		AudioVolumeFader fader = new AudioVolumeFader(
				handler,
				recordedVolumes::add,
				1.0f,
				() -> timeHolder[0],
				15L
		);

		fader.fadeTo(0.2f, 150L);

		// Advance 75ms (halfway)
		advanceTimeWithClock(75L, timeHolder);

		// At 75ms / 150ms (50% progress), volume should be around 0.6f (1.0 - 0.8 * 0.5)
		float midVolume = fader.getCurrentVolume();
		assertEquals(0.6f, midVolume, 0.05f);

		// Advance to 150ms (full duration)
		advanceTimeWithClock(75L, timeHolder);

		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);
	}

	@Test
	public void cancelInFlightFadeWhenNewTargetVolumeRequested() {
		List<Float> recordedVolumes = new ArrayList<>();
		long[] timeHolder = new long[]{1000L};
		AudioVolumeFader fader = new AudioVolumeFader(
				handler,
				recordedVolumes::add,
				1.0f,
				() -> timeHolder[0],
				15L
		);

		// Start duck: 1.0f -> 0.2f over 150ms
		fader.fadeTo(0.2f, 150L);

		// Advance 45ms into the duck (partial fade)
		advanceTimeWithClock(45L, timeHolder);
		float volumeAtDuckInterrupt = fader.getCurrentVolume();
		assertTrue("Volume should have dropped below 1.0f", volumeAtDuckInterrupt < 1.0f);
		assertTrue("Volume should not yet have reached 0.2f", volumeAtDuckInterrupt > 0.2f);

		recordedVolumes.clear();

		// Rapid unduck: target 1.0f over 150ms
		fader.fadeTo(1.0f, 150L);

		// The new fade must start from current volume, not jump back to 0.2 or 1.0 abruptly
		assertEquals(volumeAtDuckInterrupt, fader.getCurrentVolume(), 0.001f);

		// Advance unduck to completion
		advanceTimeWithClock(150L, timeHolder);

		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);

		// Verify unduck interpolated upwards from volumeAtDuckInterrupt to 1.0f
		float prev = volumeAtDuckInterrupt;
		for (float vol : recordedVolumes) {
			assertTrue("Volume should increase monotonically during unduck: " + vol + " >= " + prev,
					vol >= prev - 0.001f);
			prev = vol;
		}
	}

	@Test
	public void explicitCancelStopsFade() {
		List<Float> recordedVolumes = new ArrayList<>();
		long[] timeHolder = new long[]{1000L};
		AudioVolumeFader fader = new AudioVolumeFader(
				handler,
				recordedVolumes::add,
				1.0f,
				() -> timeHolder[0],
				15L
		);

		fader.fadeTo(0.2f, 150L);

		advanceTimeWithClock(30L, timeHolder);
		float volumeAtCancel = fader.getCurrentVolume();

		fader.cancel();
		int countAtCancel = recordedVolumes.size();

		// Advance further time
		advanceTimeWithClock(200L, timeHolder);

		assertEquals(volumeAtCancel, fader.getCurrentVolume(), 0.001f);
		assertEquals(countAtCancel, recordedVolumes.size());
	}

	@Test
	public void boundaryClamping() {
		AtomicReference<Float> current = new AtomicReference<>(0.5f);
		AudioVolumeFader fader = new AudioVolumeFader(handler, current::set, 0.5f);

		// Target above 1.0f clamped to 1.0f
		fader.fadeTo(1.8f, 150L);
		advanceTime(150L);
		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);
		assertEquals(1.0f, current.get(), 0.001f);

		// Target below 0.0f clamped to 0.0f
		fader.fadeTo(-0.5f, 150L);
		advanceTime(150L);
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);
		assertEquals(0.0f, current.get(), 0.001f);

		// setCurrentVolume clamping
		fader.setCurrentVolume(-0.2f);
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);
		assertEquals(0.0f, current.get(), 0.001f);

		fader.setCurrentVolume(1.5f);
		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);
		assertEquals(1.0f, current.get(), 0.001f);
	}

	@Test
	public void staticFadeHelper() {
		List<Float> recorded = new ArrayList<>();
		AudioVolumeFader fader = AudioVolumeFader.fade(handler, 1.0f, 0.2f, 150L, recorded::add);

		assertEquals(1.0f, recorded.get(0), 0.001f);
		advanceTime(150L);
		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);
		assertEquals(0.2f, recorded.get(recorded.size() - 1), 0.001f);
	}

	@Test
	public void zeroDurationAppliesTargetImmediately() {
		List<Float> recorded = new ArrayList<>();
		AudioVolumeFader fader = new AudioVolumeFader(handler, recorded::add, 1.0f);

		fader.fadeTo(0.2f, 0L);
		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);
		assertEquals(1, recorded.size());
		assertEquals(0.2f, recorded.get(0), 0.001f);
	}

	@Test
	public void duckAndUnduckSmoothProgression() {
		List<Float> recordedVolumes = new ArrayList<>();
		AudioVolumeFader fader = new AudioVolumeFader(handler, recordedVolumes::add, 1.0f);

		fader.duck();
		assertTrue(fader.isDucked());
		assertEquals(1.0f, fader.getPreDuckVolume(), 0.001f);

		advanceTime(150L);
		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);

		fader.unduck();
		assertFalse(fader.isDucked());

		advanceTime(150L);
		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);
	}

	@Test
	public void duckWhenAlreadyMutedDoesNotUnmuteOnUnduck() {
		List<Float> recordedVolumes = new ArrayList<>();
		AudioVolumeFader fader = new AudioVolumeFader(handler, recordedVolumes::add, 1.0f);

		// Mute the audio first
		fader.mute();
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);
		recordedVolumes.clear();

		// Trigger duck while already muted
		fader.duck();
		assertTrue(fader.isDucked());
		assertEquals(0.0f, fader.getPreDuckVolume(), 0.001f);

		advanceTime(150L);
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);

		// Trigger unduck
		fader.unduck();
		assertFalse(fader.isDucked());

		advanceTime(150L);
		// Volume must remain 0.0f (not auto-unmuted)
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);
	}

	@Test
	public void consecutiveDuckingPreservesInitialPreDuckVolume() {
		AudioVolumeFader fader = new AudioVolumeFader(handler, vol -> {}, 0.8f);

		fader.duck();
		assertEquals(0.8f, fader.getPreDuckVolume(), 0.001f);
		advanceTime(150L);
		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);

		// Second duck call should not overwrite preDuckVolume with 0.2f
		fader.duck();
		assertEquals(0.8f, fader.getPreDuckVolume(), 0.001f);

		fader.unduck();
		advanceTime(150L);
		assertEquals(0.8f, fader.getCurrentVolume(), 0.001f);
	}

	@Test
	public void muteAndUnmuteSmoothRamp() {
		AudioVolumeFader fader = new AudioVolumeFader(handler, vol -> {}, 1.0f);

		fader.mute();
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);

		fader.unmute();
		advanceTime(150L);
		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);
	}

	@Test
	public void muteThenUnmuteThenImmediateDuckDucksToTargetAndDoesNotRampToNormal() {
		List<Float> recordedVolumes = new ArrayList<>();
		long[] timeHolder = new long[]{1000L};
		AudioVolumeFader fader = new AudioVolumeFader(
				handler,
				recordedVolumes::add,
				1.0f,
				() -> timeHolder[0],
				15L
		);

		fader.mute();
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);
		assertTrue(fader.isMuted());

		// Start unmuting (target 1.0f, step interval 15ms)
		fader.unmute();
		assertFalse(fader.isMuted());
		// No time has advanced yet (0ms) - current volume is still 0.0f and first tick has not run
		assertEquals(0.0f, fader.getCurrentVolume(), 0.001f);

		// Immediate duck arrives before the first tick
		fader.duck();
		assertTrue(fader.isDucked());
		// The intended pre-duck volume must be preserved as 1.0f (target of unmute), not 0.0f
		assertEquals(1.0f, fader.getPreDuckVolume(), 0.001f);

		recordedVolumes.clear();
		// Advance through the entire duck duration (150ms)
		advanceTimeWithClock(150L, timeHolder);

		// Volume should have reached duck volume (0.2f), NOT ramped up to 1.0f!
		assertEquals(0.2f, fader.getCurrentVolume(), 0.001f);
		for (float vol : recordedVolumes) {
			assertTrue("Volume during duck must never exceed duck volume 0.2f, got: " + vol, vol <= 0.201f);
		}

		// Subsequent unduck must restore to the intended pre-duck volume (1.0f)
		fader.unduck();
		advanceTimeWithClock(150L, timeHolder);
		assertEquals(1.0f, fader.getCurrentVolume(), 0.001f);
	}
}
