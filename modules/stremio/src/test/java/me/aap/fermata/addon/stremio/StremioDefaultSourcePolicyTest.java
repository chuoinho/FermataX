package me.aap.fermata.addon.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Action;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;

public class StremioDefaultSourcePolicyTest {
	@Test
	public void successfulOrExistingDefaultSetsRemovalMarker() {
		assertTrue(StremioDefaultSourcePolicy.marksHandled(outcome(Status.CHANGED, null)));
		assertTrue(StremioDefaultSourcePolicy.marksHandled(outcome(Status.UNCHANGED, null)));
		assertTrue(StremioDefaultSourcePolicy.marksHandled(
				outcome(Status.FAILED, Code.DUPLICATE_TRANSPORT)));
	}

	@Test
	public void transientFailureRemainsRetryable() {
		assertFalse(StremioDefaultSourcePolicy.marksHandled(
				outcome(Status.FAILED, Code.TRANSPORT)));
		assertFalse(StremioDefaultSourcePolicy.marksHandled(
				outcome(Status.CANCELLED, Code.CANCELLED)));
	}

	private static StremioSourceOutcome outcome(Status status, Code code) {
		return new StremioSourceOutcome(Action.INITIALIZE_DEFAULT, status,
				null, null, code);
	}
}
