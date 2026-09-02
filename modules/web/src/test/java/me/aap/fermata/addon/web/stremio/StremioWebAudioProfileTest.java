package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.media.audio.AudioEffectsProfile;

public class StremioWebAudioProfileTest {
	@Test
	public void serializesTheCanonicalTenBandProfileOnly() {
		int[] curve = {-20, -15, -3, 0, 2, 5, 15, 16, 3, -4};
		StremioWebAudioProfile web = StremioWebAudioProfile.from(profile(1, true, true, curve, -6));
		assertArrayEquals(new int[] {-15, -15, -3, 0, 2, 5, 15, 15, 3, -4}, web.bandsDb());
		assertTrue(web.toJavascriptObject().contains("b:[-15,-15,-3,0,2,5,15,15,3,-4]"));
		assertTrue(web.isProcessingEnabled());
	}

	@Test
	public void keepsOnlyNegativePreampWithoutALimiter() {
		assertEquals(0, StremioWebAudioProfile.from(profile(1, true, true,
				AudioEffectsProfile.flatCurveDb(), 6)).preampDb());
		StremioWebAudioProfile negative = StremioWebAudioProfile.from(profile(1, true, true,
				AudioEffectsProfile.flatCurveDb(), -6));
		assertEquals(-6, negative.preampDb());
		assertEquals(0.501187, negative.preampGain(), 0.000001);
	}

	@Test
	public void invalidProfilesAndDisabledProcessingFailToUnity() {
		assertFalse(StremioWebAudioProfile.from(profile(2, true, true,
				AudioEffectsProfile.flatCurveDb(), -6)).isProcessingEnabled());
		assertFalse(StremioWebAudioProfile.from(profile(1, false, true,
				AudioEffectsProfile.flatCurveDb(), -6)).isProcessingEnabled());
		assertFalse(StremioWebAudioProfile.from(profile(1, true, false,
				AudioEffectsProfile.flatCurveDb(), -6)).isProcessingEnabled());
	}

	@Test
	public void validatesBoundedFiniteWirePayloads() {
		double[] bands = new double[10];
		assertTrue(StremioWebAudioProfile.isValidWireProfile(1, true, true, bands, -6));
		bands[4] = Double.NaN;
		assertFalse(StremioWebAudioProfile.isValidWireProfile(1, true, true, bands, -6));
		bands[4] = 0;
		assertFalse(StremioWebAudioProfile.isValidWireProfile(1, true, true, bands, 1));
		assertFalse(StremioWebAudioProfile.isValidWireProfile(2, true, true, bands, -6));
	}

	private static AudioEffectsProfile profile(int schema, boolean enabled, boolean equalizer,
			int[] curve, int preamp) {
		return new AudioEffectsProfile(schema, enabled, equalizer, curve, preamp,
				false, 0, false, 0, false, 0, 0);
	}
}
