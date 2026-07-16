package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubePlaybackIntentGateTest {
	private static final String VIDEO_1 = "https://m.youtube.com/watch?v=video-1";
	private static final String VIDEO_2 = "https://m.youtube.com/watch?v=video-2";

	@Test
	public void feedPreviewIsRejectedEvenAfterTouch() {
		YoutubePlaybackIntentGate gate = new YoutubePlaybackIntentGate();
		gate.armUserGesture(100L);

		assertFalse(gate.accepts("https://m.youtube.com/", 200L, false));
		assertFalse(gate.accepts("https://m.youtube.com/results?search_query=test", 300L, false));
	}

	@Test
	public void firstVideoRequiresAndConsumesARecentGesture() {
		YoutubePlaybackIntentGate gate = new YoutubePlaybackIntentGate();

		assertFalse(gate.accepts(VIDEO_1, 100L, false));
		gate.armUserGesture(200L);
		assertTrue(gate.accepts(VIDEO_1, 300L, false));
		assertTrue(gate.accepts(VIDEO_1, 400L, false));
	}

	@Test
	public void expiredGestureCannotStartPlayback() {
		YoutubePlaybackIntentGate gate = new YoutubePlaybackIntentGate();
		gate.armUserGesture(100L);

		assertFalse(gate.accepts(VIDEO_1,
				100L + YoutubePlaybackIntentGate.USER_GESTURE_WINDOW_MS + 1L, false));
	}

	@Test
	public void explicitResumeCanStartWithoutWebViewGesture() {
		YoutubePlaybackIntentGate gate = new YoutubePlaybackIntentGate();
		gate.armExplicitPlayback();

		assertTrue(gate.accepts(VIDEO_1, 100L, false));
	}

	@Test
	public void activeYoutubeSessionCanAdvanceToAnotherVideo() {
		YoutubePlaybackIntentGate gate = new YoutubePlaybackIntentGate();

		assertTrue(gate.accepts(VIDEO_2, 100L, true));
	}

	@Test
	public void resetRejectsStalePlaybackSignals() {
		YoutubePlaybackIntentGate gate = new YoutubePlaybackIntentGate();
		gate.armUserGesture(100L);
		assertTrue(gate.accepts(VIDEO_1, 200L, false));

		gate.reset();

		assertFalse(gate.accepts(VIDEO_1, 300L, false));
	}

	@Test
	public void voiceResultCollectorAcceptsOnlyYoutubeResultsPages() {
		assertTrue(YoutubeWebView.isVoiceSearchResultsUrl(
				"https://www.youtube.com/results?search_query=numb"));
		assertTrue(YoutubeWebView.isVoiceSearchResultsUrl(
				"https://m.youtube.com/results?search_query=numb"));
		assertFalse(YoutubeWebView.isVoiceSearchResultsUrl("https://www.youtube.com/watch?v=abc"));
		assertFalse(YoutubeWebView.isVoiceSearchResultsUrl("https://example.com/results?q=numb"));
	}
}
