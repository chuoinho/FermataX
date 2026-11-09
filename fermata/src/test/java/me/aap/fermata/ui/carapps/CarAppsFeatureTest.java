package me.aap.fermata.ui.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

public class CarAppsFeatureTest {

	@Before
	@After
	public void resetState() {
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setBackend(null);
	}

	@Test
	public void visibleOnlyForAutoSdk30Not12LAndRootOrAdbSupportedSdk() {
		boolean[] autoBuildOptions = {false, true};
		int[] sdkVersions = {29, 30, 31, 32, 33, 36, 37};
		RootState[] states = RootState.values();

		for (boolean autoBuild : autoBuildOptions) {
			for (int sdk : sdkVersions) {
				for (RootState state : states) {
					boolean expected = autoBuild && (sdk >= 30) && (sdk != 32)
							&& ((state != RootState.ABSENT) || (sdk >= 33));
					boolean actual = CarAppsFeature.visible(autoBuild, sdk, state);
					assertEquals(
							"Failed for autoBuild=" + autoBuild + ", sdk=" + sdk + ", state=" + state,
							expected,
							actual
					);
				}
			}
		}
		assertFalse(CarAppsFeature.visible(true, 30, null));
	}

	@Test
	public void usableOnlyWhenGranted() {
		assertFalse(CarAppsFeature.usable(RootState.ABSENT));
		assertFalse(CarAppsFeature.usable(RootState.AVAILABLE));
		assertTrue(CarAppsFeature.usable(RootState.GRANTED));
		assertFalse(CarAppsFeature.usable(null));
	}

	@Test
	public void canRunWithGrantedRootOrPairedAdbOnSupportedSdk() {
		assertTrue(CarAppsFeature.canRun(RootState.GRANTED, false, 30));
		assertFalse(CarAppsFeature.canRun(RootState.AVAILABLE, false, 36));
		assertFalse(CarAppsFeature.canRun(RootState.ABSENT, false, 36));
		assertTrue(CarAppsFeature.canRun(RootState.ABSENT, true, 33));
		assertTrue(CarAppsFeature.canRun(RootState.AVAILABLE, true, 36));
		assertFalse(CarAppsFeature.canRun(RootState.ABSENT, true, 31));
		assertFalse(CarAppsFeature.canRun(RootState.ABSENT, true, 32));
		assertFalse(CarAppsFeature.canRun(null, true, 30));
	}

	@Test
	public void setStateNotifiesListenersOnlyOnChange() {
		CarAppsFeature.setState(RootState.ABSENT);
		AtomicInteger callCount = new AtomicInteger();
		Runnable listener = callCount::incrementAndGet;

		CarAppsFeature.addListener(listener);
		try {
			CarAppsFeature.setState(RootState.AVAILABLE);
			assertEquals(1, callCount.get());

			// Setting the same state again must not trigger notifications
			CarAppsFeature.setState(RootState.AVAILABLE);
			assertEquals(1, callCount.get());

			CarAppsFeature.setState(RootState.GRANTED);
			assertEquals(2, callCount.get());

			CarAppsFeature.setState(RootState.GRANTED);
			assertEquals(2, callCount.get());

			CarAppsFeature.setState(RootState.ABSENT);
			assertEquals(3, callCount.get());
		} finally {
			CarAppsFeature.removeListener(listener);
		}
	}

	@Test
	public void removedListenerIsNotNotified() {
		CarAppsFeature.setState(RootState.ABSENT);
		AtomicInteger callCount = new AtomicInteger();
		Runnable listener = callCount::incrementAndGet;

		CarAppsFeature.addListener(listener);
		CarAppsFeature.setState(RootState.AVAILABLE);
		assertEquals(1, callCount.get());

		CarAppsFeature.removeListener(listener);
		CarAppsFeature.setState(RootState.GRANTED);
		assertEquals(1, callCount.get());
	}

	@Test
	public void backendGetterAndSetter() {
		CarAppsFeature.setBackend(null);
		assertNull(CarAppsFeature.backend());

		CarAppsFeature.Backend backend = new CarAppsFeature.Backend() {
			@Override
			public FutureSupplier<Boolean> requestRoot() {
				return Completed.completed(true);
			}

			@Override
			public ActivityFragment createFragment() {
				return null;
			}
		};

		CarAppsFeature.setBackend(backend);
		assertSame(backend, CarAppsFeature.backend());
	}
}
