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
		CarAppsFeature.setBackend(null);
	}

	@Test
	public void visibleOnlyForAutoSdk33AndRootNotAbsent() {
		boolean[] autoBuildOptions = {false, true};
		int[] sdkVersions = {28, 32, 33, 36};
		RootState[] states = RootState.values();

		for (boolean autoBuild : autoBuildOptions) {
			for (int sdk : sdkVersions) {
				for (RootState state : states) {
					boolean expected = autoBuild && (sdk >= 33) && (state != RootState.ABSENT);
					boolean actual = CarAppsFeature.visible(autoBuild, sdk, state);
					assertEquals(
							"Failed for autoBuild=" + autoBuild + ", sdk=" + sdk + ", state=" + state,
							expected,
							actual
					);
				}
			}
		}
		assertFalse(CarAppsFeature.visible(true, 33, null));
	}

	@Test
	public void usableOnlyWhenGranted() {
		assertFalse(CarAppsFeature.usable(RootState.ABSENT));
		assertFalse(CarAppsFeature.usable(RootState.AVAILABLE));
		assertTrue(CarAppsFeature.usable(RootState.GRANTED));
		assertFalse(CarAppsFeature.usable(null));
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
