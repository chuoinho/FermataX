package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

	@Test
	public void sliderPositionsRoundToWholeDbAndKeepEndpoints() {
		assertEquals(15, EqualizerCurveGeometry.gainDb(0f));
		assertEquals(0, EqualizerCurveGeometry.gainDb(0.5f));
		assertEquals(-15, EqualizerCurveGeometry.gainDb(1f));
		assertEquals(15, EqualizerCurveGeometry.gainDb(-1f));
		assertEquals(-15, EqualizerCurveGeometry.gainDb(2f));
		assertEquals(1, EqualizerCurveGeometry.gainDb(EqualizerCurveGeometry.gainPosition(1)));
	}

	@Test
	public void responsiveBandSizingScrollsOnlyWhenTheAvailableWidthCannotFitTheBands() {
		assertTrue(AudioEffectsScreenLayoutPolicy.needsHorizontalScroll(320, false));
		assertTrue(AudioEffectsScreenLayoutPolicy.needsHorizontalScroll(640, true));
		assertEquals(640, AudioEffectsScreenLayoutPolicy.bandStripWidthDp(false));
		assertEquals(800, AudioEffectsScreenLayoutPolicy.bandStripWidthDp(true));
		assertFalse(AudioEffectsScreenLayoutPolicy.needsHorizontalScroll(640, false));
	}

	@Test
	public void fixedActionsRemainInsideShortContentBounds() {
		assertEquals(256, AudioEffectsScreenLayoutPolicy.contentHeightDp(320, 64));
		assertEquals(0, AudioEffectsScreenLayoutPolicy.contentHeightDp(48, 64));
		assertTrue(AudioEffectsScreenLayoutPolicy.actionIsWithinBounds(320, 256, 64));
		assertFalse(AudioEffectsScreenLayoutPolicy.actionIsWithinBounds(320, 257, 64));
	}
}
