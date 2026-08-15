package me.aap.fermata.ui.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BodyLayoutTest {
	@Test
	public void postLayoutVideoShowIsCoalescedUntilTheNextPreDraw() {
		BodyLayout.PostLayoutVideoShowGate gate = new BodyLayout.PostLayoutVideoShowGate();

		long request = gate.schedule();
		assertTrue(request != BodyLayout.PostLayoutVideoShowGate.NO_REQUEST);
		assertTrue(gate.schedule() == BodyLayout.PostLayoutVideoShowGate.NO_REQUEST);
		assertTrue(gate.complete(request));
		assertFalse(gate.complete(request));
		assertTrue(gate.schedule() != BodyLayout.PostLayoutVideoShowGate.NO_REQUEST);
	}

	@Test
	public void leavingVideoInvalidatesThePendingPreDrawCallback() {
		BodyLayout.PostLayoutVideoShowGate gate = new BodyLayout.PostLayoutVideoShowGate();
		long staleRequest = gate.schedule();

		gate.cancel();

		assertFalse(gate.complete(staleRequest));
		long currentRequest = gate.schedule();
		assertTrue(gate.complete(currentRequest));
	}
}
