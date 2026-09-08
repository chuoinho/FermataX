package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AudioEffectsDisplayUnitsTest {
	@Test
	public void bassStrengthUsesPercentDisplayWithoutChangingRawScale() {
		assertEquals("0%", AudioEffectsDisplayUnits.formatRelativeLevel(0));
		assertEquals("32%", AudioEffectsDisplayUnits.formatRelativeLevel(320));
		assertEquals("100%", AudioEffectsDisplayUnits.formatRelativeLevel(1_000));
		assertEquals(0, AudioEffectsDisplayUnits.parseRelativeLevel("0"));
		assertEquals(320, AudioEffectsDisplayUnits.parseRelativeLevel("32"));
		assertEquals(1_000, AudioEffectsDisplayUnits.parseRelativeLevel("100"));
		assertEquals(1, AudioEffectsDisplayUnits.parseRelativeLevel("0.1"));
	}

	@Test
	public void loudnessUsesExplicitRelativePercentWithoutChangingBackendRawValues() {
		assertEquals("0%", AudioEffectsDisplayUnits.formatRelativeLevel(0));
		assertEquals("18%", AudioEffectsDisplayUnits.formatRelativeLevel(180));
		assertEquals("100%", AudioEffectsDisplayUnits.formatRelativeLevel(1_000));
		assertEquals(0, AudioEffectsDisplayUnits.parseRelativeLevel("0"));
		assertEquals(180, AudioEffectsDisplayUnits.parseRelativeLevel("18"));
		assertEquals(1_000, AudioEffectsDisplayUnits.parseRelativeLevel("100"));
		assertEquals(1, AudioEffectsDisplayUnits.parseRelativeLevel("0.1"));
	}
}
