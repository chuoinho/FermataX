package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CarAppsUiStateTest {

	@After
	public void tearDown() {
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setEnabled(new me.aap.utils.pref.BasicPreferenceStore(), true);
	}

	/** Turned off with the screen open: it says so, it does not wait for a server that never comes. */
	@Test
	public void turnedOffIsItsOwnStateWhateverTheClient() {
		CarAppsFeature.setAdbPaired(true);
		CarAppsFeature.setEnabled(new me.aap.utils.pref.BasicPreferenceStore(), false);
		for (CarDisplayClient.State s : CarDisplayClient.State.values()) {
			assertEquals(CarAppsUiState.State.OFF, CarAppsUiState.from(RootState.GRANTED, s, true));
			assertEquals(CarAppsUiState.State.OFF, CarAppsUiState.from(RootState.ABSENT, s, false));
		}
	}

	@Test
	public void pairedAdbRunsWithoutRoot() {
		CarAppsFeature.setAdbPaired(true);
		assertEquals(CarAppsUiState.State.READY,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.READY, true));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.AVAILABLE, CarDisplayClient.State.STARTING, true));
		assertEquals(CarAppsUiState.State.SERVER_ERROR,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.FAILED, true));
	}

	@Test
	public void nonGrantedRootMapsToNeedsRoot() {
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.READY, true));
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.AVAILABLE, CarDisplayClient.State.READY, true));
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(null, CarDisplayClient.State.READY, true));
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.AVAILABLE, CarDisplayClient.State.IDLE, true));
	}

	@Test
	public void retryingMapsToLoading() {
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.RETRYING, true));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.STARTING, true));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.IDLE, true));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, null, true));
	}

	@Test
	public void failedMapsToServerError() {
		assertEquals(CarAppsUiState.State.SERVER_ERROR,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.FAILED, true));
	}

	@Test
	public void readyMapsToReady() {
		assertEquals(CarAppsUiState.State.READY,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.READY, true));
	}

	@Test
	public void noWifiOverAdbAsksForWifiUntilTheServerRuns() {
		CarAppsFeature.setAdbPaired(true);
		for (CarDisplayClient.State st : new CarDisplayClient.State[]{CarDisplayClient.State.IDLE,
				CarDisplayClient.State.STARTING, CarDisplayClient.State.RETRYING, CarDisplayClient.State.FAILED}) {
			assertEquals(st.name(), CarAppsUiState.State.NEEDS_WIFI,
					CarAppsUiState.from(RootState.ABSENT, st, false));
		}
		assertEquals(CarAppsUiState.State.NEEDS_WIFI, CarAppsUiState.from(RootState.ABSENT, null, false));
		assertEquals("a running server needs no Wi-Fi", CarAppsUiState.State.READY,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.READY, false));
		assertEquals(CarAppsUiState.State.SERVER_ERROR,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.FAILED, true));
	}

	@Test
	public void rootPhonesNeverAskForWifi() {
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.STARTING, false));
		assertEquals(CarAppsUiState.State.SERVER_ERROR,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.FAILED, false));
	}

	@Test
	public void withoutAPairingTheScreenStillNeedsSetupNotWifi() {
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.IDLE, false));
	}
}
