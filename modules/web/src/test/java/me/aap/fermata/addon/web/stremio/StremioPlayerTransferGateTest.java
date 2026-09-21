package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The destination may request Play once, only for a paused ready player document. */
public class StremioPlayerTransferGateTest {
	@Test
	public void pausedPlayerWithPlayHandlerDispatchesExactlyOnce() {
		StremioPlayerTransferGate gate = new StremioPlayerTransferGate();
		gate.arm(7L);

		assertFalse(gate.shouldDispatchPlay(6L, true, true));
		assertFalse(gate.shouldDispatchPlay(7L, false, true));
		assertFalse(gate.shouldDispatchPlay(7L, true, false));
		assertTrue(gate.shouldDispatchPlay(7L, true, true));
		assertFalse(gate.shouldDispatchPlay(7L, true, true));
	}

	@Test
	public void cancellationPreventsAStalePlayerFromDispatchingPlay() {
		StremioPlayerTransferGate gate = new StremioPlayerTransferGate();
		gate.arm(7L);
		gate.cancel();
		assertFalse(gate.shouldDispatchPlay(7L, true, true));
	}

	@Test
	public void playerThatStartsItselfCanNeverBeReplayedAfterPause() {
		StremioPlayerTransferGate gate = new StremioPlayerTransferGate();
		gate.arm(7L);
		gate.onPlaying(7L);
		assertFalse(gate.shouldDispatchPlay(7L, true, true));
	}
}
