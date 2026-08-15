package me.aap.fermata.engine.exoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

import androidx.media3.common.VideoSize;

import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;
import me.aap.fermata.ui.policy.VideoFormatSnapshot;

public class ExoPlayerEngineProviderTest {
	@Test
	public void supportsTheCompleteValidatedPlaybackProfile() {
		assertTrue(ExoPlayerEngineProvider.playbackCapabilities()
				.containsAll(EnumSet.allOf(EngineCapability.class)));
	}

	@Test
	public void rotatedPixelAspectRatioIsInvertedWithTheRotatedFrame() {
		VideoFormatSnapshot format = ExoPlayerEngine.videoFormatSnapshot(
				new VideoSize(720, 576, 16f / 11f), 90);

		assertEquals(576f, format.displayWidth(), 0f);
		assertEquals(720f, format.displayHeight(), 0f);
		assertEquals(11f / 16f, format.normalizedPixelWidthHeightRatio(), 0f);
	}
}
