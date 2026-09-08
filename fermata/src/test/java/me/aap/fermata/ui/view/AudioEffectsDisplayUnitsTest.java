package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AudioEffectsDisplayUnitsTest {
	@Test
	public void bassStrengthUsesPercentDisplayWithoutChangingRawScale() {
		assertEquals("0%", AudioEffectsDisplayUnits.formatBassStrength(0));
		assertEquals("32%", AudioEffectsDisplayUnits.formatBassStrength(320));
		assertEquals("100%", AudioEffectsDisplayUnits.formatBassStrength(1_000));
		assertEquals(0, AudioEffectsDisplayUnits.parseBassStrength("0"));
		assertEquals(320, AudioEffectsDisplayUnits.parseBassStrength("32"));
		assertEquals(1_000, AudioEffectsDisplayUnits.parseBassStrength("100"));
		assertEquals(1, AudioEffectsDisplayUnits.parseBassStrength("0.1"));
	}

	@Test
	public void loudnessGainUsesDbDisplayForTheExistingMillibelBackendMapping() {
		assertEquals("0 dB", AudioEffectsDisplayUnits.formatLoudnessGain(0));
		assertEquals("1.8 dB", AudioEffectsDisplayUnits.formatLoudnessGain(180));
		assertEquals("10 dB", AudioEffectsDisplayUnits.formatLoudnessGain(1_000));
		assertEquals(0, AudioEffectsDisplayUnits.parseLoudnessGain("0"));
		assertEquals(180, AudioEffectsDisplayUnits.parseLoudnessGain("1.80"));
		assertEquals(1_000, AudioEffectsDisplayUnits.parseLoudnessGain("10"));
		assertEquals(1, AudioEffectsDisplayUnits.parseLoudnessGain("0.01"));
	}
}
