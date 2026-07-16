package me.aap.fermata.addon.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TvRefreshPolicyTest {
	@Test
	public void autoReloadCooldownRemainsTenMinutes() {
		assertEquals(600_000L, TvSourceRefreshCoordinator.AUTO_RELOAD_INTERVAL);
	}
}
