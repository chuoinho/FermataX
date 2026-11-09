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
	}

	@Test
	public void pairedAdbRunsWithoutRoot() {
		CarAppsFeature.setAdbPaired(true);
		assertEquals(CarAppsUiState.State.READY,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.READY));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.AVAILABLE, CarDisplayClient.State.STARTING));
		assertEquals(CarAppsUiState.State.SERVER_ERROR,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.FAILED));
	}

	@Test
	public void nonGrantedRootMapsToNeedsRoot() {
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.ABSENT, CarDisplayClient.State.READY));
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.AVAILABLE, CarDisplayClient.State.READY));
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(null, CarDisplayClient.State.READY));
		assertEquals(CarAppsUiState.State.NEEDS_ROOT,
				CarAppsUiState.from(RootState.AVAILABLE, CarDisplayClient.State.IDLE));
	}

	@Test
	public void retryingMapsToLoading() {
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.RETRYING));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.STARTING));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.IDLE));
		assertEquals(CarAppsUiState.State.LOADING,
				CarAppsUiState.from(RootState.GRANTED, null));
	}

	@Test
	public void failedMapsToServerError() {
		assertEquals(CarAppsUiState.State.SERVER_ERROR,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.FAILED));
	}

	@Test
	public void readyMapsToReady() {
		assertEquals(CarAppsUiState.State.READY,
				CarAppsUiState.from(RootState.GRANTED, CarDisplayClient.State.READY));
	}
}
