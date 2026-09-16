package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlOnlySessionStateTest {
	@Test
	public void changingClipInvalidatesPreviousLease() {
		ControlOnlySessionState state = new ControlOnlySessionState();
		FakeDelegate delegate = new FakeDelegate();
		long oldLease = state.claim(delegate, "doc1:clip1");
		long currentLease = state.claim(delegate, "doc1:clip2");

		assertFalse(state.isCurrent(delegate, oldLease));
		assertTrue(state.isCurrent(delegate, currentLease));
	}

	@Test
	public void staleReleaseDoesNotRevokeNewOwner() {
		ControlOnlySessionState state = new ControlOnlySessionState();
		FakeDelegate first = new FakeDelegate();
		FakeDelegate second = new FakeDelegate();
		state.claim(first, "first");
		assertTrue(state.release(first));
		long currentLease = state.claim(second, "second");

		assertFalse(state.release(first));
		assertTrue(state.isCurrent(second, currentLease));
	}

	@Test
	public void presentationRequiresANativeAddonRoute() {
		ControlOnlySessionState state = new ControlOnlySessionState();
		assertNull(state.claimPresentation(new FakeDelegate(), "document:clip", 0, 0L, null));
	}

	@Test
	public void supportsPreviousTrackOnlyWhenThePresentationAdvertisesIt() {
		assertTrue(ControlOnlySessionState.supports(ACTION_SKIP_TO_PREVIOUS,
				MediaSessionCallback.ControlOnlyAction.PREVIOUS_TRACK));
		assertFalse(ControlOnlySessionState.supports(0L,
				MediaSessionCallback.ControlOnlyAction.PREVIOUS_TRACK));
	}

	private static final class FakeDelegate implements MediaSessionCallback.ControlOnlyDelegate {
		@Override
		public boolean isControlOnlyActive() {
			return true;
		}

		@Override
		public boolean dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction action) {
			return true;
		}
	}
}
