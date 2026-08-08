package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeFullscreenGate.NO_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class YoutubeFullscreenCoordinatorTest {
	private static final String PAGE = "https://m.youtube.com/watch?v=video-1";
	private static final String MEDIA = "https://media.example/stream-1";

	@Test
	public void acceptedBrowserTransactionEntersPresentationExactlyOnce() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		long request = host.lastBrowserRequest;

		assertTrue(request != NO_REQUEST);
		assertTrue(coordinator.acceptBrowserEntry(request));
		host.browserFullScreen = true;
		coordinator.onBrowserVisibilityChanged(true);
		host.runDelayed();

		assertEquals(YoutubeFullscreenCoordinator.State.FULLSCREEN, coordinator.getState());
		assertEquals(1, host.enterBrowserPresentationCount);
		assertEquals(0, host.leavePresentationCount);
	}

	@Test
	public void browserViewReplacementFallsBackWithoutLeavingFullscreen() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		assertTrue(coordinator.acceptBrowserEntry(host.lastBrowserRequest));
		host.browserFullScreen = true;
		coordinator.onBrowserVisibilityChanged(true);
		host.browserFullScreen = false;

		coordinator.onBrowserVisibilityChanged(false);
		coordinator.onBrowserVisibilityChanged(false);

		assertEquals(YoutubeFullscreenCoordinator.State.APP_FULLSCREEN,
				coordinator.getState());
		assertEquals(1, host.enterBrowserPresentationCount);
		assertEquals(1, host.enterFallbackPresentationCount);
		assertEquals(0, host.leavePresentationCount);
	}

	@Test
	public void nextVideoKeepsFullscreenAndBackStillExits() {
		FakeHost host = new FakeHost();
		host.browserRequestSupported = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		coordinator.requestAutoEntry("https://m.youtube.com/watch?v=video-2",
				"https://media.example/stream-2");
		host.runPosted();

		assertEquals(YoutubeFullscreenCoordinator.State.APP_FULLSCREEN,
				coordinator.getState());
		assertTrue(host.appVideoMode);
		assertEquals(0, host.leavePresentationCount);
		assertTrue(coordinator.onPlayerBack(true, true, false));
		assertFalse(host.appVideoMode);
		assertEquals(1, host.leavePresentationCount);
	}

	@Test
	public void backInvalidatesEntryBeforePostedRequestCanRun() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		assertTrue(coordinator.onPlayerBack(true, true, false));
		host.runPosted();

		assertEquals(NO_REQUEST, host.lastBrowserRequest);
		assertEquals(YoutubeFullscreenCoordinator.State.USER_EXITED, coordinator.getState());
		assertEquals(1, host.leavePresentationCount);
	}

	@Test
	public void missingBrowserCallbackFallsBackToAppFullscreen() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		long request = host.lastBrowserRequest;
		host.runDelayed();

		assertEquals(YoutubeFullscreenCoordinator.State.APP_FULLSCREEN, coordinator.getState());
		assertEquals(1, host.expirePendingWaitCount);
		assertEquals(1, host.enterFallbackPresentationCount);
		assertTrue(coordinator.isFallbackPresentationActive());
		assertFalse(coordinator.acceptBrowserEntry(request));
		assertFalse(coordinator.acceptBrowserEntry(NO_REQUEST));
		assertEquals(0, host.enterBrowserPresentationCount);
	}

	@Test
	public void backAfterFallbackTimeoutRejectsLateCallback() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		long request = host.lastBrowserRequest;
		host.runDelayed();
		assertTrue(coordinator.onPlayerBack(true, true, false));

		assertFalse(coordinator.acceptBrowserEntry(request));
		assertEquals(YoutubeFullscreenCoordinator.State.USER_EXITED, coordinator.getState());
		assertEquals(2, host.cancelPendingCount);
		assertEquals(1, host.leavePresentationCount);
	}

	@Test
	public void unsupportedBrowserFullscreenUsesSingleFallback() {
		FakeHost host = new FakeHost();
		host.browserRequestSupported = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();

		assertEquals(YoutubeFullscreenCoordinator.State.APP_FULLSCREEN, coordinator.getState());
		assertEquals(1, host.cancelPendingCount);
		assertEquals(1, host.enterFallbackPresentationCount);
		assertEquals(0, host.enterBrowserPresentationCount);
	}

	@Test
	public void backFromFallbackLeavesOnceAndBlocksSameVideoReentry() {
		FakeHost host = new FakeHost();
		host.browserRequestSupported = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		host.runDelayed();

		assertTrue(coordinator.onPlayerBack(true, true, false));
		coordinator.requestAutoEntry(PAGE, "https://media.example/rotated");

		assertEquals(YoutubeFullscreenCoordinator.State.USER_EXITED, coordinator.getState());
		assertEquals(1, host.leavePresentationCount);
		assertEquals(1, host.enterFallbackPresentationCount);
		assertEquals(2, host.cancelPendingCount);
	}

	@Test
	public void playbackCancellationCannotTurnOffAnotherEnginePresentation() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		assertTrue(coordinator.acceptBrowserEntry(host.lastBrowserRequest));
		host.browserFullScreen = true;
		coordinator.onBrowserVisibilityChanged(true);
		host.ownsPlaybackPresentation = false;

		coordinator.cancelPlayback();
		host.browserFullScreen = false;
		coordinator.onBrowserVisibilityChanged(false);

		assertEquals(0, host.leavePresentationCount);
	}

	@Test
	public void inactiveYoutubeFragmentNeverStartsBrowserFullscreen() {
		FakeHost host = new FakeHost();
		host.canEnter = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		host.runDelayed();

		assertEquals(NO_REQUEST, host.lastBrowserRequest);
		assertEquals(YoutubeFullscreenCoordinator.State.CANCELLED, coordinator.getState());
	}

	@Test
	public void recentSelectionWaitsForYoutubeFragmentToResume() {
		FakeHost host = new FakeHost();
		host.canEnter = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		assertEquals(NO_REQUEST, host.lastBrowserRequest);
		assertEquals(YoutubeFullscreenCoordinator.State.ENTRY_REQUESTED, coordinator.getState());

		host.canEnter = true;
		host.runNextDelayed();
		assertTrue(host.lastBrowserRequest != NO_REQUEST);
		assertEquals(YoutubeFullscreenCoordinator.State.ENTRY_REQUESTED, coordinator.getState());
	}

	@Test
	public void hostInterruptionSuspendsWithoutArmingUserExitAndRestoresFallback() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		assertTrue(coordinator.acceptBrowserEntry(host.lastBrowserRequest));
		host.browserFullScreen = true;
		coordinator.onBrowserVisibilityChanged(true);

		YoutubeFullscreenCoordinator.Suspension suspension =
				coordinator.suspendForHostInterruption();

		assertTrue(suspension != null);
		assertTrue(coordinator.isCurrent(suspension));
		assertEquals(YoutubeFullscreenCoordinator.State.HOST_SUSPENDED,
				coordinator.getState());
		assertFalse(host.browserFullScreen);
		assertEquals(1, host.leavePresentationCount);

		assertTrue(coordinator.resumeAfterHostInterruption(suspension));
		assertEquals(YoutubeFullscreenCoordinator.State.APP_FULLSCREEN,
				coordinator.getState());
		assertEquals(1, host.enterFallbackPresentationCount);
		assertFalse(coordinator.isCurrent(suspension));
		assertTrue(coordinator.onPlayerBack(true, true, false));
		assertEquals(YoutubeFullscreenCoordinator.State.USER_EXITED, coordinator.getState());
		assertEquals(2, host.leavePresentationCount);
	}

	@Test
	public void playbackCallbacksCannotReplaceHostSuspension() {
		FakeHost host = new FakeHost();
		host.browserRequestSupported = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		YoutubeFullscreenCoordinator.Suspension suspension =
				coordinator.suspendForHostInterruption();
		coordinator.requestAutoEntry("https://m.youtube.com/watch?v=video-2",
				"https://media.example/stream-2");
		host.runPosted();

		assertTrue(coordinator.isCurrent(suspension));
		assertEquals(YoutubeFullscreenCoordinator.State.HOST_SUSPENDED,
				coordinator.getState());
		assertEquals(1, host.enterFallbackPresentationCount);
	}

	@Test
	public void hostInterruptionCancelsPendingBrowserEntryAndRestoresOnce() {
		FakeHost host = new FakeHost();
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		YoutubeFullscreenCoordinator.Suspension suspension =
				coordinator.suspendForHostInterruption();
		host.runPosted();

		assertTrue(coordinator.isCurrent(suspension));
		assertEquals(NO_REQUEST, host.lastBrowserRequest);
		assertEquals(1, host.cancelPendingCount);
		assertTrue(coordinator.resumeAfterHostInterruption(suspension));
		assertEquals(1, host.enterFallbackPresentationCount);
	}

	@Test
	public void explicitRelaunchDiscardPreventsSuspendedPresentationRestore() {
		FakeHost host = new FakeHost();
		host.browserRequestSupported = false;
		YoutubeFullscreenCoordinator coordinator = new YoutubeFullscreenCoordinator(host);

		coordinator.requestAutoEntry(PAGE, MEDIA);
		host.runPosted();
		YoutubeFullscreenCoordinator.Suspension suspension =
				coordinator.suspendForHostInterruption();
		coordinator.discardHostInterruption(suspension);

		assertEquals(YoutubeFullscreenCoordinator.State.USER_EXITED, coordinator.getState());
		assertFalse(coordinator.resumeAfterHostInterruption(suspension));
		assertEquals(1, host.enterFallbackPresentationCount);
	}

	private static final class FakeHost implements YoutubeFullscreenCoordinator.Host {
		private final List<Runnable> posted = new ArrayList<>();
		private final List<Runnable> delayed = new ArrayList<>();
		private boolean canEnter = true;
		private boolean browserFullScreen;
		private boolean browserRequestSupported = true;
		private boolean ownsPlaybackPresentation = true;
		private boolean appVideoMode;
		private long lastBrowserRequest = NO_REQUEST;
		private int cancelPendingCount;
		private int expirePendingWaitCount;
		private int enterBrowserPresentationCount;
		private int enterFallbackPresentationCount;
		private int leavePresentationCount;

		@Override
		public boolean canEnterFullscreen() {
			return canEnter;
		}

		@Override
		public boolean requestBrowserFullscreen(long request) {
			lastBrowserRequest = request;
			return browserRequestSupported;
		}

		@Override
		public void cancelPendingBrowserFullscreen() {
			cancelPendingCount++;
		}

		@Override
		public void expirePendingBrowserFullscreenWait() {
			expirePendingWaitCount++;
		}

		@Override
		public boolean isBrowserFullscreen() {
			return browserFullScreen;
		}

		@Override
		public boolean isAppVideoMode() {
			return appVideoMode;
		}

		@Override
		public void exitBrowserFullscreen() {
			browserFullScreen = false;
		}

		@Override
		public boolean ownsPlaybackPresentation() {
			return ownsPlaybackPresentation;
		}

		@Override
		public void enterBrowserVideoMode() {
			appVideoMode = true;
			enterBrowserPresentationCount++;
		}

		@Override
		public void enterFallbackVideoMode() {
			appVideoMode = true;
			enterFallbackPresentationCount++;
		}

		@Override
		public void leaveAppVideoMode() {
			appVideoMode = false;
			leavePresentationCount++;
		}

		@Override
		public void post(Runnable task) {
			posted.add(task);
		}

		@Override
		public void postDelayed(Runnable task, long delayMillis) {
			delayed.add(task);
		}

		private void runPosted() {
			runAll(posted);
		}

		private void runDelayed() {
			runAll(delayed);
		}

		private void runNextDelayed() {
			if (!delayed.isEmpty()) delayed.remove(0).run();
		}

		private static void runAll(List<Runnable> tasks) {
			while (!tasks.isEmpty()) tasks.remove(0).run();
		}
	}
}
