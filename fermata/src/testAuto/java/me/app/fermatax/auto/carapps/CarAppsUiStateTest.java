package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.carapps.RootState;

public class CarAppsUiStateTest {

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
