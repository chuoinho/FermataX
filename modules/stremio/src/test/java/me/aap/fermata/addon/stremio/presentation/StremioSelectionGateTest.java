package me.aap.fermata.addon.stremio.presentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioSelectionGateTest {
	@Test
	public void blocksOnlyRapidRepeatOfSameSelection() {
		StremioSelectionGate gate = new StremioSelectionGate(700L);

		assertTrue(gate.accept("item-a", 1_000L));
		assertFalse(gate.accept("item-a", 1_699L));
		assertTrue(gate.accept("item-b", 1_699L));
		assertTrue(gate.accept("item-b", 2_399L));
	}

	@Test
	public void failedSelectionCanBeReleasedForImmediateRetry() {
		StremioSelectionGate gate = new StremioSelectionGate(700L);
		assertTrue(gate.accept("item-a", 1_000L));
		gate.release("item-a");
		assertTrue(gate.accept("item-a", 1_001L));
	}

	@Test
	public void clockRollbackDoesNotLockSelection() {
		StremioSelectionGate gate = new StremioSelectionGate(700L);
		assertTrue(gate.accept("item-a", 5_000L));
		assertTrue(gate.accept("item-a", 10L));
	}
}
