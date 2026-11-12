package me.aap.fermata.ui.carapps;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static me.aap.fermata.ui.carapps.PhoneAppsGuide.StepState.CURRENT;
import static me.aap.fermata.ui.carapps.PhoneAppsGuide.StepState.DONE;
import static me.aap.fermata.ui.carapps.PhoneAppsGuide.StepState.TODO;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Channel;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Mode;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Page;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.SetupStep;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

public class PhoneAppsGuideTest {

	@After
	public void tearDown() {
		CarAppsFeature.setBackend(null);
		CarAppsFeature.setEnabled(new BasicPreferenceStore(), true);
	}

	/**
	 * Root granted: the root steps. Root only offered: Android 13+ keeps the setup without root (with root as the
	 * alternative), Android 11-12 can only grant root. 11-12 without root and 12L have no way.
	 */
	@Test
	public void channelFollowsRootThenTheAndroidVersion() {
		assertEquals(Channel.ROOT, PhoneAppsGuide.channel(RootState.GRANTED, 30));
		assertEquals(Channel.ROOT, PhoneAppsGuide.channel(RootState.GRANTED, 34));
		assertEquals(Channel.ROOT, PhoneAppsGuide.channel(RootState.AVAILABLE, 30));
		assertEquals(Channel.ROOT, PhoneAppsGuide.channel(RootState.AVAILABLE, 31));
		assertEquals("not granted on 13+: the setup without root", Channel.ADB, PhoneAppsGuide.channel(RootState.AVAILABLE, 33));
		assertEquals(Channel.ADB, PhoneAppsGuide.channel(RootState.AVAILABLE, 34));
		assertEquals(Channel.ADB, PhoneAppsGuide.channel(RootState.ABSENT, 33));
		assertEquals(Channel.ADB, PhoneAppsGuide.channel(RootState.ABSENT, 36));
		assertEquals(Channel.NONE, PhoneAppsGuide.channel(RootState.ABSENT, 30));
		assertEquals(Channel.NONE, PhoneAppsGuide.channel(RootState.ABSENT, 31));
		assertEquals("12L stays hidden even with root", Channel.NONE, PhoneAppsGuide.channel(RootState.GRANTED, 32));
		assertEquals(Channel.NONE, PhoneAppsGuide.channel(null, 34));
	}

	/** The user picks the way first; a way already set up skips the pick; without root needs Android 13. */
	@Test
	public void setupStepFollowsTheWaySetUpThenTheUsersPick() {
		for (RootState root : new RootState[]{RootState.ABSENT, RootState.AVAILABLE}) {
			for (int sdk : new int[]{30, 31, 33, 36}) {
				String c = root + " sdk " + sdk;
				assertEquals(c, SetupStep.CHOOSE, PhoneAppsGuide.setupStep(root, false, sdk, null));
				assertEquals("root seen or hidden: " + c, SetupStep.ROOT, PhoneAppsGuide.setupStep(root, false, sdk, Mode.ROOT));
				assertEquals(c, (sdk >= 33) ? SetupStep.ADB : SetupStep.NO_ROOT_UNSUPPORTED,
						PhoneAppsGuide.setupStep(root, false, sdk, Mode.NO_ROOT));
			}
		}
		assertEquals("granted: no pick", SetupStep.ROOT, PhoneAppsGuide.setupStep(RootState.GRANTED, false, 31, null));
		assertEquals(SetupStep.ROOT, PhoneAppsGuide.setupStep(RootState.GRANTED, false, 34, Mode.NO_ROOT));
		assertEquals("paired: no pick", SetupStep.ADB, PhoneAppsGuide.setupStep(RootState.ABSENT, true, 34, null));
		assertEquals(SetupStep.ADB, PhoneAppsGuide.setupStep(RootState.ABSENT, true, 34, Mode.ROOT));
		assertEquals("12L", SetupStep.NONE, PhoneAppsGuide.setupStep(RootState.GRANTED, false, 32, Mode.ROOT));
		assertEquals(SetupStep.NONE, PhoneAppsGuide.setupStep(RootState.ABSENT, false, 29, Mode.ROOT));
		assertEquals(SetupStep.NONE, PhoneAppsGuide.setupStep(null, false, 34, null));
	}

	/** Without root on 11-12 turns the addon off (it stays in Addons to turn on again); any other pick turns it on. */
	@Test
	public void pickingStoresTheWayAndWithoutRootOnAndroid11Or12TurnsTheAddonOff() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		assertNull("no pick by default", PhoneAppsGuide.mode(store));
		PhoneAppsGuide.choose(store, Mode.NO_ROOT, 31);
		assertEquals(Mode.NO_ROOT, PhoneAppsGuide.mode(store));
		assertFalse(CarAppsFeature.isEnabled());
		assertFalse(store.getBooleanPref(CarAppsFeature.PREF_ENABLED));
		PhoneAppsGuide.choose(store, Mode.ROOT, 31);
		assertEquals(Mode.ROOT, PhoneAppsGuide.mode(store));
		assertTrue(CarAppsFeature.isEnabled());
		PhoneAppsGuide.choose(store, Mode.NO_ROOT, 34);
		assertTrue("13+: without root works", CarAppsFeature.isEnabled());
		store.applyIntPref(PhoneAppsGuide.PREF_MODE, 99);
		assertNull("an unknown value is no pick", PhoneAppsGuide.mode(store));
	}

	@Test
	public void stepsBeforeTheFirstOpenOneAreDoneAndTheLastDoneMeansAllDone() {
		assertArrayEquals(new Object[]{CURRENT, TODO, TODO}, PhoneAppsGuide.progress(false, false, false));
		assertArrayEquals(new Object[]{DONE, CURRENT, TODO}, PhoneAppsGuide.progress(true, false, false));
		assertArrayEquals(new Object[]{DONE, DONE, CURRENT}, PhoneAppsGuide.progress(true, true, false));
		assertArrayEquals("a later step done does not skip an open one",
				new Object[]{CURRENT, TODO, TODO}, PhoneAppsGuide.progress(false, true, false));
		assertArrayEquals("paired: Developer options turned off later does not matter",
				new Object[]{DONE, DONE, DONE}, PhoneAppsGuide.progress(false, false, true));
	}

	@Test
	public void firstOpenShowsOnceOnThePhoneOnlyAndOnlyWhereSupported() {
		assertTrue(PhoneAppsGuide.shouldShowFirstOpen(false, true, true, true, false));
		assertFalse("already seen", PhoneAppsGuide.shouldShowFirstOpen(true, true, true, true, false));
		assertFalse("unsupported", PhoneAppsGuide.shouldShowFirstOpen(false, false, true, true, false));
		assertFalse("turned off", PhoneAppsGuide.shouldShowFirstOpen(false, true, false, true, false));
		assertFalse("car display host", PhoneAppsGuide.shouldShowFirstOpen(false, true, true, false, false));
		assertFalse("car session running", PhoneAppsGuide.shouldShowFirstOpen(false, true, true, true, true));
		assertEquals(Page.SETUP_THEN_USAGE, PhoneAppsGuide.firstOpenPage(false));
		assertEquals(Page.USAGE, PhoneAppsGuide.firstOpenPage(true));
		assertFalse("not seen by default", new BasicPreferenceStore().getBooleanPref(PhoneAppsGuide.PREF_SEEN));
	}

	@Test
	public void guidesOpenThroughTheBackendAndAreReopenable() {
		CarAppsFeature.setBackend(null);
		assertFalse("Mobile: no backend, nothing opens", PhoneAppsGuide.open(null, Page.USAGE));
		List<Page> opened = new ArrayList<>();
		CarAppsFeature.setBackend(backend(opened));
		assertTrue(PhoneAppsGuide.open(null, Page.USAGE));
		assertTrue(PhoneAppsGuide.open(null, Page.USAGE));
		assertEquals(List.of(Page.USAGE, Page.USAGE), opened);
	}

	@Test
	public void statusLineForEachState() {
		assertEquals(PhoneAppsStatus.NOT_SUPPORTED, PhoneAppsStatus.of(false, true, RootState.ABSENT, false, false, 31));
		assertEquals(PhoneAppsStatus.OFF, PhoneAppsStatus.of(true, false, RootState.GRANTED, false, false, 34));
		assertEquals(PhoneAppsStatus.READY_ROOT, PhoneAppsStatus.of(true, true, RootState.GRANTED, false, false, 34));
		assertEquals(PhoneAppsStatus.READY_ADB, PhoneAppsStatus.of(true, true, RootState.ABSENT, true, false, 34));
		assertEquals(PhoneAppsStatus.FAILED, PhoneAppsStatus.of(true, true, RootState.ABSENT, true, true, 34));
		assertEquals("a root server failure is retried, not re-paired",
				PhoneAppsStatus.READY_ROOT, PhoneAppsStatus.of(true, true, RootState.GRANTED, false, true, 34));
		assertEquals(PhoneAppsStatus.NEEDS_ROOT_GRANT, PhoneAppsStatus.of(true, true, RootState.AVAILABLE, false, false, 31));
		assertEquals("13+: setup without root, or root", PhoneAppsStatus.NEEDS_SETUP,
				PhoneAppsStatus.of(true, true, RootState.AVAILABLE, false, false, 34));
		assertEquals(PhoneAppsStatus.NEEDS_SETUP, PhoneAppsStatus.of(true, true, RootState.ABSENT, false, false, 34));

		assertEquals(R.string.car_apps_status_ready_root, PhoneAppsStatus.READY_ROOT.textRes);
		assertEquals(R.string.car_apps_status_ready_adb, PhoneAppsStatus.READY_ADB.textRes);
		assertEquals(R.string.car_apps_readiness_status_need_setup, PhoneAppsStatus.NEEDS_SETUP.textRes);
		assertEquals(R.string.car_apps_status_off, PhoneAppsStatus.OFF.textRes);
		assertEquals(R.string.car_apps_status_unsupported, PhoneAppsStatus.NOT_SUPPORTED.textRes);
		for (PhoneAppsStatus s : PhoneAppsStatus.values()) {
			boolean action = (s == PhoneAppsStatus.NEEDS_SETUP) || (s == PhoneAppsStatus.NEEDS_ROOT_GRANT)
					|| (s == PhoneAppsStatus.FAILED);
			assertEquals(s.name(), action, s.needsAction);
		}
	}

	/** The Settings switch writes the pref itself; syncing applies it once, like the car catalog toggle. */
	@Test
	public void syncFromTheStoreAppliesASwitchWrittenElsewhereOnce() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		CarAppsFeature.restoreEnabled(store);
		List<Boolean> heard = new ArrayList<>();
		CarAppsFeature.Backend b = new CarAppsFeature.Backend() {
			@Override
			public FutureSupplier<Boolean> requestRoot() {
				return Completed.completed(true);
			}

			@Override
			public ActivityFragment createFragment() {
				return null;
			}

			@Override
			public void onEnabledChanged(boolean enabled) {
				heard.add(enabled);
			}
		};
		CarAppsFeature.setBackend(b);
		PreferenceStore.Listener sync = (s, prefs) -> {
			if (prefs.contains(CarAppsFeature.PREF_ENABLED)) CarAppsFeature.syncEnabled(s);
		};
		store.addBroadcastListener(sync);
		try {
			store.applyBooleanPref(CarAppsFeature.PREF_ENABLED, false); // the Settings switch
			assertFalse(CarAppsFeature.isEnabled());
			assertEquals(List.of(false), heard);
			CarAppsFeature.setEnabled(store, true); // the car catalog toggle, with the page listening
			assertTrue(CarAppsFeature.isEnabled());
			assertTrue(store.getBooleanPref(CarAppsFeature.PREF_ENABLED));
			assertEquals("each change heard once", List.of(false, true), heard);
		} finally {
			store.removeBroadcastListener(sync);
		}
	}

	private static CarAppsFeature.Backend backend(List<Page> opened) {
		return new CarAppsFeature.Backend() {
			@Override
			public FutureSupplier<Boolean> requestRoot() {
				return Completed.completed(true);
			}

			@Override
			public ActivityFragment createFragment() {
				return null;
			}

			@Override
			public void openGuide(android.content.Context ctx, Page page) {
				opened.add(page);
			}
		};
	}
}
