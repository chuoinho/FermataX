package me.aap.fermata.media.service;

import static me.aap.fermata.media.service.AutomotiveRuntimeGate.State.ACTIVE;
import static me.aap.fermata.media.service.AutomotiveRuntimeGate.State.OPEN_UNCONFIRMED;
import static me.aap.fermata.media.service.AutomotiveRuntimeGate.State.QUIESCENT;
import static me.aap.fermata.media.service.AutomotiveRuntimeGate.State.SHUTTING_DOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutomotiveRuntimeGateTest {
	@Before
	public void setUp() {
		AutomotiveRuntimeGate.resetForTests();
	}

	@After
	public void tearDown() {
		AutomotiveRuntimeGate.resetForTests();
	}

	@Test
	public void startupIsOpenUntilOfficialProjectionArrives() {
		assertEquals(OPEN_UNCONFIRMED, AutomotiveRuntimeGate.stateForTests());
		assertTrue(AutomotiveRuntimeGate.allowsNewWork());
		assertEquals(0L, AutomotiveRuntimeGate.currentGeneration());
	}

	@Test
	public void shutdownBlocksLateWorkUntilNewProjection() {
		long first = AutomotiveRuntimeGate.projectionConnected();
		assertEquals(ACTIVE, AutomotiveRuntimeGate.stateForTests());
		assertEquals(first, AutomotiveRuntimeGate.beginShutdown());
		assertEquals(SHUTTING_DOWN, AutomotiveRuntimeGate.stateForTests());
		assertFalse(AutomotiveRuntimeGate.allowsNewWork());
		assertEquals(-1L, AutomotiveRuntimeGate.projectionConnected());

		AutomotiveRuntimeGate.completeShutdown(first);
		assertEquals(QUIESCENT, AutomotiveRuntimeGate.stateForTests());
		assertFalse(AutomotiveRuntimeGate.allowsNewWork());

		long second = AutomotiveRuntimeGate.projectionConnected();
		assertTrue(second > first);
		assertTrue(AutomotiveRuntimeGate.isActiveGeneration(second));
		assertFalse(AutomotiveRuntimeGate.isActiveGeneration(first));
	}

	@Test
	public void staleCompletionCannotCloseNewGeneration() {
		long first = AutomotiveRuntimeGate.projectionConnected();
		AutomotiveRuntimeGate.beginShutdown();
		AutomotiveRuntimeGate.completeShutdown(first);
		long second = AutomotiveRuntimeGate.projectionConnected();
		AutomotiveRuntimeGate.completeShutdown(first);
		assertEquals(ACTIVE, AutomotiveRuntimeGate.stateForTests());
		assertTrue(AutomotiveRuntimeGate.isActiveGeneration(second));
	}
}
