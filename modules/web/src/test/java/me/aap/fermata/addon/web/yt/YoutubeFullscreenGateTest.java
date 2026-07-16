package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeFullscreenGate.NO_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeFullscreenGateTest {
	@Test
	public void userBackInvalidatesScheduledEntryAndSuppressesCurrentVideo() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		long request = gate.requestAutoEntry(page, "https://media.example/stream-1");

		assertNotEquals(NO_REQUEST, request);
		assertTrue(gate.accepts(request));

		gate.onUserExit();

		assertFalse(gate.accepts(request));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(page, "https://media.example/stream-2"));
	}

	@Test
	public void repeatedPlaybackCallbacksCannotReenterFullscreen() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";

		assertNotEquals(NO_REQUEST, gate.requestAutoEntry(page, "https://media.example/stream-1"));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(page, "https://media.example/stream-1"));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(page, "https://media.example/rotated-stream"));
	}

	@Test
	public void playerBackCancelsEntryWhileBrowserFullscreenIsStillTransitioning() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		long request = gate.requestAutoEntry(page, "https://media.example/stream-1");

		assertTrue(gate.onUserBack(true, true, false));
		assertFalse(gate.accepts(request));
		assertEquals(NO_REQUEST,
				gate.requestAutoEntry(page, "https://media.example/rotated-stream"));
		assertFalse(gate.acceptsBrowserEntry(request));
		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));
	}

	@Test
	public void untaggedBrowserCallbackCannotUndoPlayerbarBack() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		long request = gate.requestAutoEntry(page, "https://media.example/stream-1");

		assertTrue(gate.onUserBack(true, true, true));

		assertFalse(gate.acceptsBrowserEntry(request));
		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));
		assertEquals(NO_REQUEST,
				gate.requestAutoEntry(page, "https://media.example/late-playing"));
		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));
	}

	@Test
	public void sameVideoCanReenterOnlyAfterFreshManualGesture() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		gate.requestAutoEntry(page, "https://media.example/stream-1");
		gate.onUserExit();

		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));
		long permit = gate.grantManualBrowserEntry();
		assertNotEquals(NO_REQUEST, permit);
		assertTrue(gate.acceptsBrowserEntry(NO_REQUEST));

		gate.onUserExit();
		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));
	}

	@Test
	public void expiredManualGestureCannotAuthorizeLateCallback() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		gate.requestAutoEntry(page, "https://media.example/stream-1");
		gate.onUserExit();
		long permit = gate.grantManualBrowserEntry();

		gate.expireManualBrowserEntry(permit);

		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-2", "https://media.example/history-stream"));
	}

	@Test
	public void transientPageIdentityCannotUndoExplicitBack() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		long request = gate.requestAutoEntry(page, "https://media.example/stream-1");
		gate.onUserExit();

		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/", "blob:https://m.youtube.com/rotated"));
		assertFalse(gate.acceptsBrowserEntry(request));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				page, "https://media.example/stream-2"));
	}

	@Test
	public void playerbarBackThenWebHistoryCannotAutoEnterAnotherVideo() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String oldPage = "https://m.youtube.com/watch?v=video-1";
		long oldRequest = gate.requestAutoEntry(oldPage, "https://media.example/stream-1");

		assertTrue(gate.onUserBack(true, true, true));
		assertFalse(gate.acceptsBrowserEntry(oldRequest));
		assertEquals(NO_REQUEST,
				gate.requestAutoEntry(oldPage, "https://media.example/late-playing-1"));

		gate.cancelCurrentPlayback();
		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/", "blob:https://m.youtube.com/late-playing-2"));
		assertEquals(NO_REQUEST,
				gate.requestAutoEntry(oldPage, "https://media.example/late-playing-3"));

		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-2", "https://media.example/history-stream"));
		assertFalse(gate.acceptsBrowserEntry(NO_REQUEST));

		gate.grantManualBrowserEntry();
		long newRequest = gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-3", "https://media.example/selected-stream");
		assertNotEquals(NO_REQUEST, newRequest);
		assertTrue(gate.acceptsBrowserEntry(newRequest));
	}

	@Test
	public void normalWebBackDoesNotSuppressFutureFullscreenEntry() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();

		assertFalse(gate.onUserBack(true, false, false));
		assertFalse(gate.onUserBack(false, true, false));
		assertNotEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-1", "https://media.example/stream-1"));
	}

	@Test
	public void selectingAnotherVideoRequiresAFreshWebViewGesture() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		long oldRequest = gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-1", "https://media.example/stream-1");
		gate.onUserExit();

		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-2", "https://media.example/history-stream"));
		gate.grantManualBrowserEntry();
		long newRequest = gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-2", "https://media.example/stream-2");

		assertNotEquals(NO_REQUEST, newRequest);
		assertNotEquals(oldRequest, newRequest);
		assertTrue(gate.accepts(newRequest));
	}

	@Test
	public void stoppingPlaybackRejectsStaleCallbacksUntilVideoIdentityChanges() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();
		String page = "https://m.youtube.com/watch?v=video-1";
		long oldRequest = gate.requestAutoEntry(page, "https://media.example/stream-1");

		gate.cancelCurrentPlayback();

		assertFalse(gate.accepts(oldRequest));
		assertEquals(NO_REQUEST,
				gate.requestAutoEntry(page, "https://media.example/stale-stream"));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/", "blob:https://m.youtube.com/stale"));
		assertNotEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/watch?v=video-2", "https://media.example/stream-2"));
	}

	@Test
	public void playbackIdentityUsesYoutubeVideoIdInsteadOfRotatingMediaUrl() {
		assertEquals("watch:abc", YoutubeFullscreenGate.playbackKey(
				"https://m.youtube.com/watch?feature=share&v=abc", "https://media.example/one"));
		assertEquals("shorts:xyz", YoutubeFullscreenGate.playbackKey(
				"https://m.youtube.com/shorts/xyz?feature=share", "https://media.example/two"));
		assertEquals("page:https://m.youtube.com/", YoutubeFullscreenGate.playbackKey(
				"https://m.youtube.com/#menu", "https://media.example/rotating-preview"));
	}

	@Test
	public void feedPreviewCanNeverRequestAutomaticFullscreen() {
		YoutubeFullscreenGate gate = new YoutubeFullscreenGate();

		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/", "blob:https://m.youtube.com/preview"));
		assertEquals(NO_REQUEST, gate.requestAutoEntry(
				"https://m.youtube.com/results?search_query=test",
				"blob:https://m.youtube.com/search-preview"));
	}
}
