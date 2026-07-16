package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RadioRefreshPolicyTest {
	@Test
	public void autoReloadCooldownRemainsTenMinutes() {
		assertEquals(600_000L, RadioRefreshCoordinator.AUTO_RELOAD_INTERVAL);
	}
}
