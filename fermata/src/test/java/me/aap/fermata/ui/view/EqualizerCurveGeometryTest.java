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
	public void responsiveBandSizingFitsAllBandsWithoutPaging() {
		assertEquals(32, AudioEffectsScreenLayoutPolicy.bandWidthDp(344, 10));
		assertEquals(58, AudioEffectsScreenLayoutPolicy.bandWidthDp(600, 10));
	}

	@Test
	public void fixedActionsRemainInsideShortContentBounds() {
		assertEquals(264, AudioEffectsScreenLayoutPolicy.contentHeightDp(320, 56));
		assertEquals(0, AudioEffectsScreenLayoutPolicy.contentHeightDp(48, 56));
		assertTrue(AudioEffectsScreenLayoutPolicy.actionIsWithinBounds(320, 264, 56));
		assertFalse(AudioEffectsScreenLayoutPolicy.actionIsWithinBounds(320, 265, 56));
	}

	@Test
	public void bandGestureArbitrationClaimsVerticalDragsAndYieldsHorizontalDrags() {
		assertEquals(AudioEffectsScreenLayoutPolicy.GestureAxis.VERTICAL,
				AudioEffectsScreenLayoutPolicy.resolveBandGestureAxis(2, 18, 8));
		assertEquals(AudioEffectsScreenLayoutPolicy.GestureAxis.HORIZONTAL,
				AudioEffectsScreenLayoutPolicy.resolveBandGestureAxis(18, 2, 8));
		assertEquals(AudioEffectsScreenLayoutPolicy.GestureAxis.UNDECIDED,
				AudioEffectsScreenLayoutPolicy.resolveBandGestureAxis(4, 5, 8));
	}

}
