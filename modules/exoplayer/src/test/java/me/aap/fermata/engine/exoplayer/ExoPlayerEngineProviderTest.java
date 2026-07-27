package me.aap.fermata.engine.exoplayer;

import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;

public class ExoPlayerEngineProviderTest {
	@Test
	public void supportsTheCompleteValidatedPlaybackProfile() {
		assertTrue(ExoPlayerEngineProvider.playbackCapabilities()
				.containsAll(EnumSet.allOf(EngineCapability.class)));
	}
}
