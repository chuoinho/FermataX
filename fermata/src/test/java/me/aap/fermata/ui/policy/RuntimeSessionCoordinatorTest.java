package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.policy.RuntimeSessionCoordinator.Token;

public class RuntimeSessionCoordinatorTest {
	@Test
	public void reconnectRejectsCallbacksFromDestroyedPresentation() {
		RuntimeSessionCoordinator coordinator = new RuntimeSessionCoordinator();
		Token first = coordinator.attach(new Object(), RuntimeHostMode.AA_PROJECTION);
		assertTrue(coordinator.detach(first));
		Token second = coordinator.attach(new Object(), RuntimeHostMode.AA_PROJECTION);

		assertFalse(coordinator.isCurrent(first));
		assertTrue(coordinator.isCurrent(second));
		assertSame(second, coordinator.getCurrent());
	}

	@Test
	public void staleDetachCannotReleaseCurrentHost() {
		RuntimeSessionCoordinator coordinator = new RuntimeSessionCoordinator();
		Token first = coordinator.attach(new Object(), RuntimeHostMode.PHONE);
		Token second = coordinator.attach(new Object(), RuntimeHostMode.MIRROR);

		assertFalse(coordinator.detach(first));
		assertTrue(coordinator.isCurrent(second));
	}

	@Test
	public void oneHundredHostPermutationsKeepOnlyLatestPresentation() {
		RuntimeSessionCoordinator coordinator = new RuntimeSessionCoordinator();
		Token previous = null;
		for (int i = 0; i < 100; i++) {
			Token current = coordinator.attach(new Object(), RuntimeHostMode.values()[i % 3]);
			if (previous != null) assertFalse(coordinator.isCurrent(previous));
			assertTrue(coordinator.isCurrent(current));
			previous = current;
		}
	}
}
