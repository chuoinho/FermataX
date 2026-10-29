package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallerPolicyTest {

	@Test
	public void serverAcceptsOnlyAppUid() {
		int appUid = 10150;
		assertTrue(CallerPolicy.serverAccepts(appUid, appUid));

		assertFalse(CallerPolicy.serverAccepts(0, appUid));
		assertFalse(CallerPolicy.serverAccepts(1000, appUid));
		assertFalse(CallerPolicy.serverAccepts(2000, appUid));
		assertFalse(CallerPolicy.serverAccepts(appUid + 1, appUid));
		assertFalse(CallerPolicy.serverAccepts(appUid - 1, appUid));
	}

	@Test
	public void providerAcceptsOnlyRootAndShell() {
		assertTrue(CallerPolicy.providerAccepts(0));
		assertTrue(CallerPolicy.providerAccepts(2000));

		assertFalse(CallerPolicy.providerAccepts(1000));
		assertFalse(CallerPolicy.providerAccepts(10123));
		assertFalse(CallerPolicy.providerAccepts(-1));
	}
}
