package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.ui.policy.PlaybackPresentationReducer.State;

public class PlaybackPresentationCoordinatorTest {
	@Test
	public void staleTimeoutCannotHideNewerControls() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(false);
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
		coordinator.enterVideo(false);
		coordinator.showSeekControls(5000);
		coordinator.leaveVideo(true);
		host.run(0);

		assertEquals(new State(false, false, true, false, false, false), host.last());
	}

	@Test
	public void audioVisibilityUpdateReplacesStaleLeaveVideoState() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(false);
		coordinator.leaveVideo(false);
		coordinator.leaveVideo(true);
		coordinator.showControlsPersistent();

		assertEquals(new State(false, false, true, false, false, false), host.last());
	}

	@Test
	public void persistentControlsCancelFullscreenTimeout() {
		FakeHost host = new FakeHost();
		PlaybackPresentationCoordinator coordinator = new PlaybackPresentationCoordinator(host);
		coordinator.enterVideo(false);
		coordinator.toggleControls(3000);
		coordinator.showControlsPersistent();
		host.run(0);

		assertEquals(new State(true, false, true, false, false, false), host.last());
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
