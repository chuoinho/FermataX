package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PlaybackForegroundCoordinatorTest {
	@Test
	public void acquisitionPromotesBeforeRetainingAndCommitKeepsForeground() {
		FakeHost host = new FakeHost();
		PlaybackForegroundCoordinator coordinator = new PlaybackForegroundCoordinator(host);
		PlaybackForegroundCoordinator.Lease lease = coordinator.acquire();

		assertNotNull(lease);
		assertEquals(List.of("promote", "retain"), host.events);
		lease.commit();
		assertEquals(List.of("promote", "retain"), host.events);
	}

	@Test
	public void failedAttemptRollsBackOnlyItsOwnPromotion() {
		FakeHost host = new FakeHost();
		PlaybackForegroundCoordinator coordinator = new PlaybackForegroundCoordinator(host);
		PlaybackForegroundCoordinator.Lease first = coordinator.acquire();
		PlaybackForegroundCoordinator.Lease second = coordinator.acquire();

		first.rollback();
		assertEquals(List.of("promote", "retain", "retain"), host.events);
		second.rollback();
		assertEquals(List.of("promote", "retain", "retain"), host.events);
	}

	@Test
	public void focusFailureDemotesOnlyWhenAttemptPromotedService() {
		FakeHost host = new FakeHost();
		PlaybackForegroundCoordinator coordinator = new PlaybackForegroundCoordinator(host);
		PlaybackForegroundCoordinator.Lease lease = coordinator.acquire();
		lease.rollback();

		assertEquals(List.of("promote", "retain", "demote:false"), host.events);

		coordinator.keepForeground();
		PlaybackForegroundCoordinator.Lease alreadyForeground = coordinator.acquire();
		alreadyForeground.rollback();
		assertEquals(List.of("promote", "retain", "demote:false", "promote", "retain", "retain"),
				host.events);
	}

	@Test
	public void promotionFailureIsReportedAndNoLeaseEscapes() {
		FakeHost host = new FakeHost();
		host.failPromotion = true;
		PlaybackForegroundCoordinator coordinator = new PlaybackForegroundCoordinator(host);

		assertNull(coordinator.acquire());
		assertEquals(List.of("promote", "demote:false", "failed"), host.events);
	}

	private static final class FakeHost implements PlaybackForegroundCoordinator.Host {
		private final List<String> events = new ArrayList<>();
		private boolean failPromotion;

		@Override
		public void promote() {
			events.add("promote");
			if (failPromotion) throw new SecurityException("denied");
		}

		@Override
		public void retainLifetime() {
			events.add("retain");
		}

		@Override
		public void demote(boolean removeNotification) {
			events.add("demote:" + removeNotification);
		}

		@Override
		public void failed(RuntimeException error) {
			events.add("failed");
		}
	}
}
