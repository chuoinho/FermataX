package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.policy.PlaybackPresentationReducer.State;

public class PlaybackPresentationReducerTest {
	@Test
	public void fullscreenStartsWithChromeAndControlsHidden() {
		assertEquals(new State(true, false, false, true, false, false),
				PlaybackPresentationReducer.enterVideo(false));
	}

	@Test
	public void splitAlwaysKeepsChromeAndControlsVisible() {
		State split = PlaybackPresentationReducer.enterVideo(true);
		assertEquals(new State(true, true, true, false, false, false), split);
		assertEquals(split, PlaybackPresentationReducer.toggleControls(split, 3000));
		assertEquals(split, PlaybackPresentationReducer.timeout(split));
	}

	@Test
	public void fullscreenTapShowsThenHidesControlsWithChrome() {
		State hidden = PlaybackPresentationReducer.enterVideo(false);
		State shown = PlaybackPresentationReducer.toggleControls(hidden, 3000);

		assertEquals(new State(true, false, true, false, true, false), shown);
		assertEquals(hidden, PlaybackPresentationReducer.toggleControls(shown, 3000));
		assertEquals(hidden, PlaybackPresentationReducer.timeout(shown));
	}

	@Test
	public void zeroDelayKeepsFullscreenControlsHidden() {
		State hidden = PlaybackPresentationReducer.enterVideo(false);
		assertEquals(hidden, PlaybackPresentationReducer.toggleControls(hidden, 0));
	}

	@Test
	public void seekUsesItsOwnTimeoutMode() {
		State shown = PlaybackPresentationReducer.showSeekControls(
				PlaybackPresentationReducer.enterVideo(false), 5000);
		assertEquals(new State(true, false, true, false, true, true), shown);
		assertEquals(PlaybackPresentationReducer.enterVideo(false),
				PlaybackPresentationReducer.timeout(shown));
	}

	@Test
	public void preparationControlsRemainVisibleUntilTimedRelease() {
		State hidden = PlaybackPresentationReducer.enterVideo(false);
		State persistent = PlaybackPresentationReducer.showControlsPersistent(hidden);
		assertEquals(new State(true, false, true, false, false, false), persistent);

		State released = PlaybackPresentationReducer.showControls(persistent, 5000);
		assertEquals(new State(true, false, true, false, true, false), released);
		assertEquals(hidden, PlaybackPresentationReducer.timeout(released));
	}

	@Test
	public void leavingVideoRestoresOnlySupportedAudioPlayerBar() {
		assertEquals(new State(false, false, true, false, false, false),
				PlaybackPresentationReducer.leaveVideo(true));
		assertEquals(new State(false, false, false, false, false, false),
				PlaybackPresentationReducer.leaveVideo(false));
	}

	@Test
	public void pausedVideoKeepsControlsVisibleWithoutTimeout() {
		State paused = PlaybackPresentationReducer.enterVideo(false, false);
		assertEquals(new State(true, false, true, false, false, false), paused);
		assertEquals(paused, PlaybackPresentationReducer.toggleControls(paused, 5000, false));
		assertEquals(new State(true, false, true, false, false, true),
				PlaybackPresentationReducer.showSeekControls(paused, 5000, false));
	}

	@Test
	public void pauseCancelsPendingTimeoutAndResumeArmsItAgain() {
		State shown = PlaybackPresentationReducer.showControls(
				PlaybackPresentationReducer.enterVideo(false), 5000, true);
		State paused = PlaybackPresentationReducer.playingChanged(shown, false, 5000);
		assertEquals(new State(true, false, true, false, false, false), paused);
		assertEquals(new State(true, false, true, false, true, false),
				PlaybackPresentationReducer.playingChanged(paused, true, 5000));
	}
}
