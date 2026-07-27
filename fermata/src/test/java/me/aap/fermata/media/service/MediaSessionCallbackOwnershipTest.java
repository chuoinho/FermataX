package me.aap.fermata.media.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaSessionCallbackOwnershipTest {
	@Test
	public void oldEngineCallbacksAreRejectedWhileAnotherItemIsPending() {
		assertFalse(MediaSessionCallback.acceptsCallbackOwnership(true, true, false));
		assertTrue(MediaSessionCallback.acceptsCallbackOwnership(true, true, true));
	}

	@Test
	public void inactiveEngineCallbacksAreAlwaysRejected() {
		assertFalse(MediaSessionCallback.acceptsCallbackOwnership(false, false, false));
		assertFalse(MediaSessionCallback.acceptsCallbackOwnership(false, true, true));
	}

	@Test
	public void currentEngineIsAcceptedWithoutPendingTransition() {
		assertTrue(MediaSessionCallback.acceptsCallbackOwnership(true, false, false));
	}

	@Test
	public void differentVideoClearsPreviousDecoderFrame() {
		Object current = new Object();
		assertTrue(MediaSessionCallback.shouldClearPlaybackSurfaces(
				true, current, new Object()));
		assertTrue(MediaSessionCallback.shouldClearPlaybackSurfaces(
				true, null, new Object()));
		assertFalse(MediaSessionCallback.shouldClearPlaybackSurfaces(
				true, current, current));
		assertFalse(MediaSessionCallback.shouldClearPlaybackSurfaces(
				false, current, new Object()));
	}
}
