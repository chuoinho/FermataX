package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.carapps.CarAppsChecks;

public class CarAppsChecksConstantsMatchAidlTest {

	@Test
	public void carAppsChecksConstantsMatchAidl() {
		assertEquals(ICarDisplayServer.CAP_VD, CarAppsChecks.CAP_VD);
		assertEquals(ICarDisplayServer.CAP_TRUSTED, CarAppsChecks.CAP_TRUSTED);
		assertEquals(ICarDisplayServer.CAP_INJECT, CarAppsChecks.CAP_INJECT);
		assertEquals(ICarDisplayServer.CAP_IME, CarAppsChecks.CAP_IME);
		assertEquals(ICarDisplayServer.CAP_OWN_POWER_GROUP, CarAppsChecks.CAP_OWN_POWER_GROUP);
		assertEquals(ICarDisplayServer.CAP_TASKS, CarAppsChecks.CAP_TASKS);
		assertEquals(ICarDisplayServer.CAP_FOCUS, CarAppsChecks.CAP_FOCUS);
	}
}
