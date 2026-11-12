package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.pref.BasicPreferenceStore;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DashboardItemsCarAppsTest {

	private Context context;

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setBackend(null);
	}

	@After
	public void tearDown() {
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setBackend(null);
		CarAppsFeature.setEnabled(new BasicPreferenceStore(), true);
	}

	/** Turned off, it is hidden like a disabled addon; the layout editor (includeDisabled) still lists it. */
	@Test
	public void turnedOffItIsHiddenLikeADisabledAddon() {
		CarAppsFeature.setState(RootState.GRANTED);
		CarAppsFeature.setEnabled(new BasicPreferenceStore(), false);
		assertNull(DashboardItems.getItem(context, DashboardItems.CAR_APPS, false));
		assertNotNull(DashboardItems.getItem(context, DashboardItems.CAR_APPS, true));
		for (DashboardItems.Item item : DashboardItems.getDashboardItems(context, new BasicPreferenceStore())) {
			assertFalse(DashboardItems.CAR_APPS.equals(item.name));
		}
		CarAppsFeature.setEnabled(new BasicPreferenceStore(), true);
		assertEquals(BuildConfig.AUTO, DashboardItems.getItem(context, DashboardItems.CAR_APPS, false) != null);
	}

	@Test
	public void itemOfferedWithoutRootOnAdbSupportedAndroid() {
		CarAppsFeature.setState(RootState.ABSENT);
		assertEquals(BuildConfig.AUTO, DashboardItems.getItem(context, DashboardItems.CAR_APPS, false) != null);
	}

	/**
	 * Android 11-12 without root seen: offered (root may be hidden, the setup lets the user pick root). Picking
	 * without root there turns the addon off, which hides it like any disabled addon.
	 */
	@Test
	@Config(sdk = 31)
	public void android12WithoutRootSeenIsOfferedUntilTurnedOff() {
		CarAppsFeature.setState(RootState.ABSENT);
		assertEquals(BuildConfig.AUTO, DashboardItems.getItem(context, DashboardItems.CAR_APPS, false) != null);
		CarAppsFeature.setEnabled(new BasicPreferenceStore(), false);

		// getItem with includeDisabled=false returns null
		assertNull(DashboardItems.getItem(context, DashboardItems.CAR_APPS, false));

		// getItem with includeDisabled=true returns item for configuration
		DashboardItems.Item configItem = DashboardItems.getItem(context, DashboardItems.CAR_APPS, true);
		assertNotNull(configItem);
		assertEquals(R.id.car_apps_fragment, configItem.id);

		BasicPreferenceStore store = new BasicPreferenceStore();
		List<DashboardItems.Item> dashboardItems = DashboardItems.getDashboardItems(context, store);
		for (DashboardItems.Item item : dashboardItems) {
			assertFalse("Dashboard should not include CAR_APPS when root is absent",
					DashboardItems.CAR_APPS.equals(item.name));
		}

		List<DashboardItems.Item> configItems = DashboardItems.getConfigItems(context, store);
		boolean foundInConfig = false;
		for (DashboardItems.Item item : configItems) {
			if (DashboardItems.CAR_APPS.equals(item.name)) {
				foundInConfig = true;
				break;
			}
		}
		assertTrue("Config items should include CAR_APPS even when root is absent", foundInConfig);
	}

	@Test
	public void itemPresentWhenRootAvailable() {
		CarAppsFeature.setState(RootState.AVAILABLE);

		if (BuildConfig.AUTO) {
			DashboardItems.Item item = DashboardItems.getItem(context, DashboardItems.CAR_APPS, false);
			assertNotNull("CAR_APPS item should be non-null when root is AVAILABLE in auto build", item);
			assertEquals(R.id.car_apps_fragment, item.id);
			assertEquals(R.drawable.ic_car_apps, item.icon);
			assertEquals(context.getString(R.string.car_apps_title), item.title);
			assertEquals(context.getString(R.string.car_apps_dashboard_sub), item.subtitle);

			BasicPreferenceStore store = new BasicPreferenceStore();
			List<DashboardItems.Item> dashboardItems = DashboardItems.getDashboardItems(context, store);
			boolean foundInDashboard = false;
			for (DashboardItems.Item i : dashboardItems) {
				if (DashboardItems.CAR_APPS.equals(i.name)) {
					foundInDashboard = true;
					break;
				}
			}
			assertTrue("Dashboard should include CAR_APPS when root is AVAILABLE in auto build", foundInDashboard);
		} else {
			assertNull(DashboardItems.getItem(context, DashboardItems.CAR_APPS, false));
			DashboardItems.Item item = DashboardItems.getItem(context, DashboardItems.CAR_APPS, true);
			assertNotNull(item);
			assertEquals(R.id.car_apps_fragment, item.id);
		}
	}

	@Test
	public void existingLayoutGainsItemOnce() {
		CarAppsFeature.setState(RootState.AVAILABLE);

		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyStringArrayPref(DashboardItems.PREF, new String[]{
				DashboardItems.FOLDERS,
				DashboardItems.FAVORITES,
				DashboardItems.RECENT,
				DashboardItems.PLAYLISTS
		});
		store.applyIntPref(DashboardItems.LAYOUT_VERSION, 2);

		List<String> layout = DashboardItems.getConfigLayout(store);
		assertTrue("Layout should contain CAR_APPS", layout.contains(DashboardItems.CAR_APPS));
		assertEquals("CAR_APPS should be positioned right after PLAYLISTS",
				layout.indexOf(DashboardItems.PLAYLISTS) + 1,
				layout.indexOf(DashboardItems.CAR_APPS));

		// Check for no duplicates
		int occurrences = 0;
		for (String name : layout) {
			if (DashboardItems.CAR_APPS.equals(name)) {
				occurrences++;
			}
		}
		assertEquals("CAR_APPS should appear exactly once in layout", 1, occurrences);
	}

	@Test
	public void navItemMapping() {
		DashboardItems.NavItem navItem = DashboardItems.getNavItem(DashboardItems.CAR_APPS);
		assertNotNull(navItem);
		assertEquals(DashboardItems.CAR_APPS, navItem.name);
		assertEquals(R.id.car_apps_fragment, navItem.id);
		assertEquals(R.drawable.ic_car_apps, navItem.icon);
		assertEquals(R.string.car_apps_title, navItem.title);
	}

	@Test
	public void idToNameMapping() {
		assertEquals(DashboardItems.CAR_APPS, DashboardItems.idToName(R.id.car_apps_fragment));
	}
}
