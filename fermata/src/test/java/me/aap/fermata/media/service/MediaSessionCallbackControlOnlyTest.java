package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

/** Regression coverage for stale SmartTop/Web commands at the callback boundary. */
public class MediaSessionCallbackControlOnlyTest {

	@Test
	public void staleLeaseCannotDispatchToTheCurrentWebOwner() throws Exception {
		FakeDelegate delegate = new FakeDelegate(ACTION_PLAY | ACTION_PAUSE);
		ControlOnlySessionState session = claimed(delegate);
		MediaSessionCallback callback = callback(session);
		long lease = session.presentation().leaseId();

		assertFalse(callback.dispatchControlOnly(lease + 1L,
				MediaSessionCallback.ControlOnlyAction.PLAY));
		assertEquals(0, delegate.calls);
		assertTrue(callback.dispatchControlOnly(lease, MediaSessionCallback.ControlOnlyAction.PLAY));
		assertEquals(1, delegate.calls);
	}

	@Test
	public void currentPresentationRejectsInactiveOwnerAndUnsupportedAction() throws Exception {
		FakeDelegate delegate = new FakeDelegate(ACTION_PLAY);
		ControlOnlySessionState session = claimed(delegate);
		MediaSessionCallback callback = callback(session);
		long lease = session.presentation().leaseId();

		assertFalse(callback.dispatchControlOnly(lease,
				MediaSessionCallback.ControlOnlyAction.PAUSE));
		assertEquals(0, delegate.calls);
		delegate.active = false;
		assertNull(callback.getControlOnlyPresentation());
		assertFalse(callback.dispatchControlOnly(lease,
				MediaSessionCallback.ControlOnlyAction.PLAY));
		assertEquals(0, delegate.calls);
	}

	@Test
	public void mediaSessionPreviousDispatchesTheControlOnlyPreviousActionOnce() throws Exception {
		FakeDelegate delegate = new FakeDelegate(ACTION_SKIP_TO_PREVIOUS);
		MediaSessionCallback callback = callback(claimed(delegate));

		callback.onSkipToPrevious();

		assertEquals(1, delegate.calls);
		assertEquals(MediaSessionCallback.ControlOnlyAction.PREVIOUS_TRACK, delegate.lastAction);
	}

	private static ControlOnlySessionState claimed(FakeDelegate delegate) {
		ControlOnlySessionState session = new ControlOnlySessionState();
		assertTrue(session.claimPresentation(delegate, "document:clip", STATE_PAUSED,
				delegate.actions, null) != null);
		return session;
	}

	private static MediaSessionCallback callback(ControlOnlySessionState session) throws Exception {
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		Field field = MediaSessionCallback.class.getDeclaredField("controlOnlySession");
		field.setAccessible(true);
		field.set(callback, session);
		return callback;
	}

	private static MediaSessionCallback allocate(Class<MediaSessionCallback> type) throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (MediaSessionCallback) unsafeType.getMethod("allocateInstance", Class.class)
				.invoke(field.get(null), type);
	}

	private static final class FakeDelegate implements MediaSessionCallback.ControlOnlyDelegate {
		final long actions;
		boolean active = true;
		int calls;
		MediaSessionCallback.ControlOnlyAction lastAction;

		FakeDelegate(long actions) {
			this.actions = actions;
		}

		@Override
		public boolean isControlOnlyActive() {
			return active;
		}

		@Override
		public String controlOnlyAddonClass() {
			return "test.addon";
		}

		@Override
		public long controlOnlyActions() {
			return actions;
		}

		@Override
		public boolean dispatchControlOnlyAction(MediaSessionCallback.ControlOnlyAction action) {
			calls++;
			lastAction = action;
			return active;
		}
	}
}
