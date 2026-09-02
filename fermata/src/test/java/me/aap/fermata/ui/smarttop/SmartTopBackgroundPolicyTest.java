package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.media.lib.ExtRoot;

public class SmartTopBackgroundPolicyTest {
	@Test
	public void selectionOrderIsArtworkThenProvenAudioThenFallback() {
		assertEquals(SmartTopBackground.Kind.EMPTY,
				SmartTopBackgroundPolicy.select(true, true, true));
		assertEquals(SmartTopBackground.Kind.ARTWORK,
				SmartTopBackgroundPolicy.select(false, true, true));
		assertEquals(SmartTopBackground.Kind.AUDIO_SPECTRUM,
				SmartTopBackgroundPolicy.select(false, false, true));
		assertEquals(SmartTopBackground.Kind.SOURCE_FALLBACK,
				SmartTopBackgroundPolicy.select(false, false, false));
	}

	@Test
	public void artworkDimensionBoundariesAreInclusive() {
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(179, 179, false));
		assertTrue(SmartTopBackgroundPolicy.eligibleDimensions(180, 180, false));
		assertTrue(SmartTopBackgroundPolicy.eligibleDimensions(180, 225, false));
		assertTrue(SmartTopBackgroundPolicy.eligibleDimensions(225, 180, false));
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(179, 225, false));
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(226, 180, false));
	}

	@Test
	public void wideBrokenAndAnimatedImagesAreRejected() {
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(320, 180, false));
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(0, 512, false));
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(512, -1, false));
		assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(512, 512, true));
	}

	@Test
	public void spectrumAcceptsOnlyExplicitAudioRootCapabilities() {
		assertTrue(SmartTopArtworkResolver.isProvenAudioRoot(
				new ExtRoot("Radio", null, AddonCapability.RADIO)));
		assertTrue(SmartTopArtworkResolver.isProvenAudioRoot(
				new ExtRoot("Podcast", null, AddonCapability.PODCAST)));
		assertTrue(SmartTopArtworkResolver.isProvenAudioRoot(
				new ExtRoot("Audiobook", null, AddonCapability.AUDIOBOOK)));
		assertFalse(SmartTopArtworkResolver.isProvenAudioRoot(
				new ExtRoot("TV", null, AddonCapability.TV)));
		assertFalse(SmartTopArtworkResolver.isProvenAudioRoot(new ExtRoot("Legacy", null)));
	}
}
