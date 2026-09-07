package me.aap.fermata.media.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NativeEqualizerCurveMapperTest {
	@Test
	public void mapsExactCanonicalFrequency() {
		int[] curve = {-9, -7, -5, -3, -1, 1, 3, 5, 7, 9};

		assertEquals(1F, NativeEqualizerCurveMapper.interpolateDb(1_000,
				AudioEffectsProfile.CANONICAL_FREQ_HZ, curve), 0F);
	}

	@Test
	public void interpolatesIntermediateFrequencyOnALogScale() {
		int[] curve = AudioEffectsProfile.flatCurveDb();
		curve[5] = 0;
		curve[6] = 10;

		assertEquals(5F, NativeEqualizerCurveMapper.interpolateDb(1_414,
				AudioEffectsProfile.CANONICAL_FREQ_HZ, curve), 0.02F);
	}

	@Test
	public void clampsOutsideTheCanonicalRange() {
		int[] curve = {-8, -6, -4, -2, 0, 2, 4, 6, 8, 10};

		assertEquals(-8F, NativeEqualizerCurveMapper.interpolateDb(20,
				AudioEffectsProfile.CANONICAL_FREQ_HZ, curve), 0F);
		assertEquals(10F, NativeEqualizerCurveMapper.interpolateDb(20_000,
				AudioEffectsProfile.CANONICAL_FREQ_HZ, curve), 0F);
	}

	@Test
	public void mapsAndClampsAnyNativeTopology() {
		int[] curve = AudioEffectsProfile.flatCurveDb();
		curve[0] = -30;
		curve[5] = 5;
		curve[9] = 30;

		assertArrayEquals(new short[]{-1_500, 500, 1_500},
				NativeEqualizerCurveMapper.mapBandLevels(curve, new int[]{31, 1_000, 16_000},
						new short[]{-1_500, 1_500}));
	}

	@Test
	public void flatCurveStaysFlatForDifferentBandCounts() {
		assertArrayEquals(new short[]{0, 0, 0, 0, 0}, NativeEqualizerCurveMapper.mapBandLevels(
				AudioEffectsProfile.flatCurveDb(), new int[]{60, 240, 1_000, 4_000, 15_000},
				new short[]{-3_000, 3_000}));
	}
}
