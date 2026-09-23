package me.aap.fermata.addon.web.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.media.audio.AudioEffectsProfile;

public class WebAudioProfileTest {
	@Test
	public void serializesOnlyTheCanonicalTenBandGlobalProfile() {
		int[] curve = {-20, -15, -3, 0, 2, 5, 15, 16, 3, -4};
		WebAudioProfile web = WebAudioProfile.from(profile(1, true, true, curve, -6));
		assertArrayEquals(new int[] {-15, -15, -3, 0, 2, 5, 15, 15, 3, -4}, web.bandsDb());
		assertTrue(web.toJavascriptObject().contains("b:[-15,-15,-3,0,2,5,15,15,3,-4]"));
		assertTrue(web.masterEnabled());
		assertTrue(web.equalizerEnabled());
	}

	@Test
	public void keepsNegativePreampOnly() {
		assertEquals(0, WebAudioProfile.from(profile(1, true, true,
				AudioEffectsProfile.flatCurveDb(), 6)).preampDb());
		WebAudioProfile negative = WebAudioProfile.from(profile(1, true, true,
				AudioEffectsProfile.flatCurveDb(), -6));
		assertEquals(-6, negative.preampDb());
	}

	@Test
	public void disabledOrInvalidProfilesFallBackToUnity() {
		WebAudioProfile invalidSchema = WebAudioProfile.from(profile(2, true, true,
				AudioEffectsProfile.flatCurveDb(), -6));
		assertFalse(invalidSchema.masterEnabled());
		assertFalse(invalidSchema.equalizerEnabled());

		WebAudioProfile masterDisabled = WebAudioProfile.from(profile(1, false, true,
				AudioEffectsProfile.flatCurveDb(), -6));
		assertFalse(masterDisabled.masterEnabled());
		assertTrue(masterDisabled.equalizerEnabled());

		WebAudioProfile eqDisabled = WebAudioProfile.from(profile(1, true, false,
				AudioEffectsProfile.flatCurveDb(), -6));
		assertTrue(eqDisabled.masterEnabled());
		assertFalse(eqDisabled.equalizerEnabled());

		WebAudioProfile fromNull = WebAudioProfile.from(null);
		assertFalse(fromNull.masterEnabled());
		assertFalse(fromNull.equalizerEnabled());
		assertEquals(0, fromNull.preampDb());
	}

	@Test
	public void unityProfileHasZeroGainsAndDisabledFlags() {
		WebAudioProfile unity = WebAudioProfile.unity();
		assertFalse(unity.masterEnabled());
		assertFalse(unity.equalizerEnabled());
		assertEquals(0, unity.preampDb());
		assertArrayEquals(new int[10], unity.bandsDb());
		assertEquals("{v:1,m:false,e:false,b:[0,0,0,0,0,0,0,0,0,0],p:0}", unity.toJavascriptObject());
	}

	private static AudioEffectsProfile profile(int schema, boolean enabled, boolean equalizer,
			int[] curve, int preamp) {
		return new AudioEffectsProfile(schema, enabled, equalizer, curve, preamp,
				false, 0, false, 0, false, 0, 0);
	}
}
