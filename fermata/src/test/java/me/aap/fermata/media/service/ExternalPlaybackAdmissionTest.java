package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExternalPlaybackAdmissionTest {
	@Test
	public void claimRequiresPublishedTransitionForThePendingOwner() {
		ClaimCase[] cases = {
				new ClaimCase("spontaneous playback without a request", false, false, false,
						ExternalPlaybackAdmission.Decision.OPEN),
				new ClaimCase("transition without an ownership token", false, true, false,
						ExternalPlaybackAdmission.Decision.REJECT_TRANSITION_WITHOUT_OWNER),
				new ClaimCase("old source during outgoing position capture", true, false, false,
						ExternalPlaybackAdmission.Decision.REJECT_REQUEST_NOT_PUBLISHED),
				new ClaimCase("target callback before transition publication", true, false, true,
						ExternalPlaybackAdmission.Decision.REJECT_REQUEST_NOT_PUBLISHED),
				new ClaimCase("old source after transition publication", true, true, false,
						ExternalPlaybackAdmission.Decision.REJECT_PENDING_SOURCE_MISMATCH),
				new ClaimCase("published pending target completes", true, true, true,
						ExternalPlaybackAdmission.Decision.COMPLETE_PENDING)
		};

		for (ClaimCase test : cases) {
			assertEquals(test.name, test.expected, ExternalPlaybackAdmission.decide(
					test.ownershipPending, test.transitionPending, test.sourceMatchesPending));
		}
	}

	private record ClaimCase(String name, boolean ownershipPending, boolean transitionPending,
			boolean sourceMatchesPending, ExternalPlaybackAdmission.Decision expected) {}
}
