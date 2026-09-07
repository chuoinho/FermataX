package me.aap.fermata.media.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GainSafetyPolicyTest {
	@Test
	public void negativePreampIsSafeWithoutALimiter() {
		GainSafetyPolicy.Decision decision = GainSafetyPolicy.evaluate(profile(-3, false,
				AudioEffectsProfile.flatCurveDb()), false);

		assertTrue(decision.isPreampAllowed());
		assertEquals(GainSafetyPolicy.Reason.SAFE, decision.getReason());
	}

	@Test
	public void zeroPreampIsSafeWithoutALimiter() {
		GainSafetyPolicy.Decision decision = GainSafetyPolicy.evaluate(profile(0, false,
				AudioEffectsProfile.flatCurveDb()), false);

		assertTrue(decision.isPreampAllowed());
		assertTrue(decision.isLoudnessAllowed());
	}

	@Test
	public void positivePreampIsBlockedWithoutALimiter() {
		GainSafetyPolicy.Decision decision = GainSafetyPolicy.evaluate(profile(3, false,
				AudioEffectsProfile.flatCurveDb()), false);

		assertFalse(decision.isPreampAllowed());
		assertEquals(GainSafetyPolicy.Reason.POSITIVE_PREAMP_REQUIRES_LIMITER,
				decision.getReason());
	}

	@Test
	public void positivePreampRequiresAnAvailableLimiter() {
		GainSafetyPolicy.Decision decision = GainSafetyPolicy.evaluate(profile(3, false,
				AudioEffectsProfile.flatCurveDb()), true);

		assertTrue(decision.isPreampAllowed());
		assertEquals(GainSafetyPolicy.Reason.SAFE, decision.getReason());
	}

	@Test
	public void loudnessCannotStackWithPositiveEqualizerWithoutALimiter() {
		int[] curve = AudioEffectsProfile.flatCurveDb();
		curve[5] = 4;
		GainSafetyPolicy.Decision decision = GainSafetyPolicy.evaluate(profile(0, true, curve), false);

		assertTrue(decision.isPreampAllowed());
		assertFalse(decision.isLoudnessAllowed());
		assertEquals(GainSafetyPolicy.Reason.LOUDNESS_EQ_STACK_REQUIRES_LIMITER,
				decision.getReason());
	}

	@Test
	public void disabledEqualizerDoesNotBlockLoudnessFromItsStoredCurve() {
		int[] curve = AudioEffectsProfile.flatCurveDb();
		curve[5] = 4;
		AudioEffectsProfile profile = new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
				true, false, curve, 0, false, 0, true, 250, false, 0, 0);

		assertTrue(GainSafetyPolicy.evaluate(profile, false).isLoudnessAllowed());
	}

	private static AudioEffectsProfile profile(int preampDb, boolean loudnessEnabled, int[] curve) {
		return new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION, true, true, curve,
				preampDb, false, 0, loudnessEnabled, 250, false, 0, 0);
	}
}
