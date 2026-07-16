package me.aap.fermata.media.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaServiceRuntimeGateTest {
	@Test
	public void autoCreateIsPassiveUntilInternalUiBind() {
		MediaServiceRuntimeGate gate = new MediaServiceRuntimeGate();

		assertFalse(gate.takeAutomaticPrepare(true));
		assertFalse(gate.takeAddonAttachOnCreate(true));
		assertTrue(gate.takeAddonAttachOnInternalBind());
		assertFalse(gate.takeAddonAttachOnInternalBind());
		assertTrue(gate.takeAddonDetachOnDestroy());
		assertFalse(gate.takeAddonDetachOnDestroy());
	}

	@Test
	public void mobileCreateKeepsLegacyPrepareAndAddonLifecycle() {
		MediaServiceRuntimeGate gate = new MediaServiceRuntimeGate();

		assertTrue(gate.takeAutomaticPrepare(false));
		assertFalse(gate.takeAutomaticPrepare(false));
		assertTrue(gate.takeAddonAttachOnCreate(false));
		assertFalse(gate.takeAddonAttachOnInternalBind());
		assertTrue(gate.takeAddonDetachOnDestroy());
	}
}
