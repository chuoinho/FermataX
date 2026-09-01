package me.aap.fermata.media.service;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class AudioEffectsLegacyApplierTest {
	@Test
	public void manualBandsFollowEffectTopologyAndClampTheHardwareRange() {
		short[] levels = AudioEffectsLegacyApplier.clampBandLevels(
				new int[] {-2_000, -500, 750, 2_000}, (short) 3,
				new short[] {-1_500, 1_500});

		assertArrayEquals(new short[] {-1_500, -500, 750}, levels);
	}

	@Test
	public void malformedBandTopologyDoesNotApplyAnyManualBands() {
		assertArrayEquals(new short[0], AudioEffectsLegacyApplier.clampBandLevels(
				new int[] {0}, (short) 1, new short[0]));
	}
}
