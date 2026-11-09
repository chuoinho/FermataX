package me.app.fermatax.auto.carapps;

import static me.app.fermatax.auto.carapps.ServerStarter.Channel;
import static me.app.fermatax.auto.carapps.ServerStarter.choose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;

public class ServerStarterTest {

	@Test
	public void rootWinsWhenGranted() {
		assertEquals(Channel.ROOT, choose(RootState.GRANTED, true, 36));
		assertEquals(Channel.ROOT, choose(RootState.GRANTED, false, 30));
	}

	@Test
	public void pairedAdbRunsWithoutGrantedRoot() {
		assertEquals(Channel.ADB, choose(RootState.ABSENT, true, 36));
		assertEquals(Channel.ADB, choose(RootState.AVAILABLE, true, 36));
		assertEquals(Channel.ADB, choose(RootState.ABSENT, true, 33));
	}

	@Test
	public void nothingRunsWithoutRootOrPairing() {
		assertEquals(Channel.NONE, choose(RootState.ABSENT, false, 36));
		assertEquals(Channel.NONE, choose(RootState.AVAILABLE, false, 36));
		assertEquals(Channel.NONE, choose(null, false, 36));
	}

	@Test
	public void adbIsNotUsedBelowAndroid13() {
		assertEquals(Channel.NONE, choose(RootState.ABSENT, true, 32));
		assertEquals(Channel.NONE, choose(RootState.ABSENT, true, 31));
		assertEquals(Channel.NONE, choose(RootState.AVAILABLE, true, 30));
	}

	@Test
	public void canRunAgreesWithChoose() {
		for (RootState root : new RootState[]{null, RootState.ABSENT, RootState.AVAILABLE, RootState.GRANTED}) {
			for (boolean paired : new boolean[]{false, true}) {
				for (int sdk : new int[]{30, 32, 33, 36}) {
					assertEquals(root + " " + paired + " " + sdk, choose(root, paired, sdk) != Channel.NONE,
							CarAppsFeature.canRun(root, paired, sdk));
				}
			}
		}
	}

	@Test
	public void adbPairingIsTrackedAndTellsListeners() {
		int[] calls = {0};
		Runnable listener = () -> calls[0]++;
		CarAppsFeature.addListener(listener);
		try {
			assertFalse(CarAppsFeature.isAdbPaired());
			CarAppsFeature.setAdbPaired(true);
			assertTrue(CarAppsFeature.isAdbPaired());
			CarAppsFeature.setAdbPaired(true);
			assertEquals(1, calls[0]);
			CarAppsFeature.setAdbPaired(false);
			assertEquals(2, calls[0]);
		} finally {
			CarAppsFeature.removeListener(listener);
			CarAppsFeature.setAdbPaired(false);
		}
	}
}
