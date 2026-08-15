package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.policy.VideoFormatSnapshot;

public class MediaPlayerEngineTest {
	@Test
	public void platformPlayerFormatKeepsTheReportedFrameGeometry() {
		VideoFormatSnapshot format = MediaPlayerEngine.videoFormatSnapshot(720, 576);

		assertEquals(720f, format.codedWidth(), 0f);
		assertEquals(576f, format.codedHeight(), 0f);
		assertEquals(720f, format.displayWidth(), 0f);
		assertEquals(576f, format.displayHeight(), 0f);
		assertEquals(1f, format.normalizedPixelWidthHeightRatio(), 0f);
	}
}
