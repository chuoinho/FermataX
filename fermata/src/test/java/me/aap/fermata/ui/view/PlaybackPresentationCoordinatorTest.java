package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.ui.policy.PlaybackPresentationReducer;
import me.aap.fermata.ui.policy.PlaybackPresentationReducer.State;
import me.aap.fermata.ui.policy.PlaybackPresentationOwner.Identity;
import me.aap.fermata.ui.policy.PlaybackPresentationOwner.Token;

public class PlaybackPresentationCoordinatorTest {
	@Test
	public void staleTimeoutCannotHideNewerControls() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(owner("one"), false);
		coordinator.toggleControls(3000);
		coordinator.refreshTimeout(5000);

		host.run(0);
		assertEquals(new State(true, false, true, false, true, false), host.last());
		host.run(1);
		assertEquals(new State(true, false, false, true, false, false), host.last());
	}

	@Test
	public void leavingVideoCancelsPendingTimeoutAndRestoresAudioBar() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(owner("one"), false);
		coordinator.showSeekControls(5000);
		coordinator.leaveVideo(true);
		host.run(0);

		assertEquals(new State(false, false, true, false, false, false), host.last());
	}

	@Test
	public void audioVisibilityUpdateReplacesStaleLeaveVideoState() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(owner("one"), false);
		coordinator.leaveVideo(false);
		coordinator.leaveVideo(true);
		coordinator.showControlsPersistent();

		assertEquals(new State(false, false, true, false, false, false), host.last());
	}

	@Test
	public void persistentControlsCancelFullscreenTimeout() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(owner("one"), false);
		coordinator.toggleControls(3000);
		coordinator.showControlsPersistent();
		host.run(0);

		assertEquals(new State(true, false, true, false, false, false), host.last());
	}

	@Test
	public void staleOwnerCannotReleaseNewPlaybackPresentation() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		Token first = coordinator.enterVideo(owner("one"), false);
		Token second = coordinator.enterVideo(owner("two"), false);

		assertFalse(coordinator.leaveVideo(first, false));
		assertTrue(coordinator.isCurrent(second));
		assertEquals(PlaybackPresentationReducer.enterVideo(false), host.last());
	}

	@Test
	public void sameIdentityReusesOwnerGeneration() {
		PlaybackPresentationCoordinator coordinator =
				new PlaybackPresentationCoordinator(new FakeHost());
		Identity identity = owner("one");
		Token first = coordinator.enterVideo(identity, false);
		Token second = coordinator.enterVideo(identity, true);

		assertEquals(first, second);
	}

	@Test
	public void tenAddonSwitchRoundsRejectEveryStaleOwner() {
		PlaybackPresentationCoordinator coordinator =
				new PlaybackPresentationCoordinator(new FakeHost());
		List<Token> stale = new ArrayList<>();
		Token current = null;

		for (int round = 0; round < 10; round++) {
			for (int addon = 0; addon < 4; addon++) {
				if (current != null) stale.add(current);
				current = coordinator.enterVideo(new Identity(addon + 1, addon + 101,
						"round-" + round + "-item-" + addon), addon == 0);
				for (Token token : stale) assertFalse(coordinator.leaveVideo(token, false));
				assertTrue(coordinator.isCurrent(current));
			}
		}
	}

	@Test
	public void pausedControlsNeverScheduleTimeoutUntilPlaybackResumes() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(owner("paused"), false, false);
		coordinator.showControls(5000, false);
		assertTrue(host.scheduled.isEmpty());
		assertFalse(host.last().timeoutPending());

		coordinator.playingChanged(true, 5000);
		assertEquals(1, host.scheduled.size());
		assertTrue(host.last().timeoutPending());
	}

	private static Identity owner(String itemId) {
		return new Identity(10, 20, itemId);
	}

	private static final class FakeHost implements PlaybackPresentationCoordinator.Host {
		final List<State> applied = new ArrayList<>();
		final List<Runnable> scheduled = new ArrayList<>();

		@Override
		public void apply(State state) {
			applied.add(state);
		}

		@Override
		public void postDelayed(Runnable task, long delay) {
			scheduled.add(task);
		}

		void run(int index) {
			scheduled.get(index).run();
		}

		State last() {
			return applied.get(applied.size() - 1);
		}
	}
}
