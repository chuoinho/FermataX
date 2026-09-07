package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.media.audio.AudioEffectsProfile;

public class EqualizerCurveGeometryTest {
	@Test
	public void canonicalFrequencyPositionsAreLogarithmicAndSpanTheCurveWidth() {
		float previous = -1f;
		for (int frequency : AudioEffectsProfile.CANONICAL_FREQ_HZ) {
			float position = EqualizerCurveGeometry.frequencyPosition(frequency);
			assertTrue(position > previous);
			previous = position;
		}
		assertEquals(0f, EqualizerCurveGeometry.frequencyPosition(31), 0.0001f);
		assertEquals(1f, EqualizerCurveGeometry.frequencyPosition(16_000), 0.0001f);
	}

	@Test
	public void gainPositionsKeepNeutralAtTheCenterAndClampToTheProductRange() {
		assertEquals(0.5f, EqualizerCurveGeometry.gainPosition(0), 0.0001f);
		assertEquals(0f, EqualizerCurveGeometry.gainPosition(15), 0.0001f);
		assertEquals(1f, EqualizerCurveGeometry.gainPosition(-15), 0.0001f);
		assertEquals(0f, EqualizerCurveGeometry.gainPosition(100), 0.0001f);
		assertEquals(1f, EqualizerCurveGeometry.gainPosition(-100), 0.0001f);
	}
}
