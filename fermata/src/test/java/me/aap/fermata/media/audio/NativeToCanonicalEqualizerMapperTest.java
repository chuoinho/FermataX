package me.aap.fermata.media.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class NativeToCanonicalEqualizerMapperTest {
	@Test
	public void mapsExactCanonicalCentersFromMillibels() {
		int[] levels = {-500, -400, -300, -200, -100, 0, 100, 200, 300, 400};

		assertArrayEquals(new int[]{-5, -4, -3, -2, -1, 0, 1, 2, 3, 4},
				NativeToCanonicalEqualizerMapper.map(levels, canonicalTopology()));
	}

	@Test
	public void usesLogFrequencyInterpolationAndEndpointClamping() {
		NativeEqualizerTopology topology = NativeEqualizerTopology.create(
				new int[]{125_000, 500_000}, new short[]{-1_500, 1_500});

		int[] curve = NativeToCanonicalEqualizerMapper.map(new int[]{0, 400}, topology);

		assertEquals(0, curve[0]);
		assertEquals(0, curve[2]);
		assertEquals(2, curve[3]);
		assertEquals(4, curve[4]);
		assertEquals(4, curve[curve.length - 1]);
	}

	@Test
	public void mapsFlatPositiveNegativeAndMixedNativeCurves() {
		NativeEqualizerTopology topology = NativeEqualizerTopology.create(
				new int[]{250_000, 1_000_000, 4_000_000}, new short[]{-1_500, 1_500});

		assertArrayEquals(new int[]{3, 3, 3, 3, 3, 3, 3, 3, 3, 3},
				NativeToCanonicalEqualizerMapper.map(new int[]{300, 300, 300}, topology));
		assertArrayEquals(new int[]{-3, -3, -3, -3, -3, -3, -3, -3, -3, -3},
				NativeToCanonicalEqualizerMapper.map(new int[]{-300, -300, -300}, topology));
		assertEquals(-5, NativeToCanonicalEqualizerMapper.map(new int[]{-500, 0, 500}, topology)[0]);
		assertEquals(5, NativeToCanonicalEqualizerMapper.map(new int[]{-500, 0, 500}, topology)[9]);
	}

	@Test
	public void supportsOneFiveAndTenBandTopologiesWithoutInventingCenters() {
		NativeEqualizerTopology oneBand = NativeEqualizerTopology.create(
				new int[]{1_000_000}, new short[]{-1_500, 1_500});
		assertArrayEquals(new int[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2},
				NativeToCanonicalEqualizerMapper.map(new int[]{200}, oneBand));

		NativeEqualizerTopology fiveBand = NativeEqualizerTopology.create(
				new int[]{31_000, 125_000, 500_000, 2_000_000, 8_000_000},
				new short[]{-1_500, 1_500});
		int[] curve = NativeToCanonicalEqualizerMapper.map(new int[]{-500, -200, 0, 200, 500}, fiveBand);
		assertEquals(-5, curve[0]);
		assertEquals(-2, curve[2]);
		assertEquals(0, curve[4]);
		assertEquals(2, curve[6]);
		assertEquals(5, curve[8]);

		assertEquals(10, canonicalTopology().bandCount());
	}

	@Test
	public void roundsHalfDbAwayFromZeroDeterministically() {
		assertEquals(0, NativeToCanonicalEqualizerMapper.roundCanonicalDb(-0.4F));
		assertEquals(-1, NativeToCanonicalEqualizerMapper.roundCanonicalDb(-0.5F));
		assertEquals(0, NativeToCanonicalEqualizerMapper.roundCanonicalDb(0.4F));
		assertEquals(1, NativeToCanonicalEqualizerMapper.roundCanonicalDb(0.5F));
	}

	@Test
	public void clampsOnlyToThePortableCanonicalDomain() {
		NativeEqualizerTopology topology = NativeEqualizerTopology.create(
				new int[]{1_000_000}, new short[]{-3_000, 3_000});

		assertArrayEquals(new int[]{15, 15, 15, 15, 15, 15, 15, 15, 15, 15},
				NativeToCanonicalEqualizerMapper.map(new int[]{3_000}, topology));
		assertArrayEquals(new int[]{-15, -15, -15, -15, -15, -15, -15, -15, -15, -15},
				NativeToCanonicalEqualizerMapper.map(new int[]{-3_000}, topology));
	}

	@Test
	public void rejectsInvalidOrMismatchedTopology() {
		assertInvalid(new int[0], new short[]{-1, 1});
		assertInvalid(new int[]{0}, new short[]{-1, 1});
		assertInvalid(new int[]{1_000, 1_000}, new short[]{-1, 1});
		assertInvalid(new int[]{2_000, 1_000}, new short[]{-1, 1});
		assertInvalid(new int[]{1_000}, new short[]{1, -1});

		try {
			NativeToCanonicalEqualizerMapper.map(new int[]{0}, canonicalTopology());
			fail("Mismatched native level count must be rejected");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static NativeEqualizerTopology canonicalTopology() {
		int[] centers = new int[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
		for (int i = 0; i < centers.length; i++) centers[i] = AudioEffectsProfile.CANONICAL_FREQ_HZ[i] * 1_000;
		return NativeEqualizerTopology.create(centers, new short[]{-1_500, 1_500});
	}

	private static void assertInvalid(int[] centers, short[] range) {
		try {
			NativeEqualizerTopology.create(centers, range);
			fail("Invalid topology must be rejected");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}
}
