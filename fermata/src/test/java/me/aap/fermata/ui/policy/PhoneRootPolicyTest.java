package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.R;

public class PhoneRootPolicyTest {
	@Test
	public void initialDestinationDependsOnHost() {
		assertEquals(R.id.glass_addons_catalog_fragment,
				PhoneRootPolicy.initialDestination(RuntimeHostMode.PHONE));
		assertEquals(R.id.dashboard_fragment,
				PhoneRootPolicy.initialDestination(RuntimeHostMode.AA_PROJECTION));
		assertEquals(R.id.dashboard_fragment,
				PhoneRootPolicy.initialDestination(RuntimeHostMode.MIRROR));
	}

	@Test
	public void onlyPhoneUsesTwoRootNavigation() {
		assertTrue(PhoneRootPolicy.usesPhoneRoots(RuntimeHostMode.PHONE));
		assertFalse(PhoneRootPolicy.usesPhoneRoots(RuntimeHostMode.AA_PROJECTION));
		assertFalse(PhoneRootPolicy.usesPhoneRoots(RuntimeHostMode.MIRROR));
	}

	@Test
	public void primaryRootDependsOnRuntimeHost() {
		assertTrue(PhoneRootPolicy.isPrimaryRoot(RuntimeHostMode.PHONE,
				R.id.glass_addons_catalog_fragment));
		assertTrue(PhoneRootPolicy.isPrimaryRoot(RuntimeHostMode.AA_PROJECTION,
				R.id.dashboard_fragment));
	}

	@Test
	public void eachSelectedPhoneAreaIsItsOwnBackRoot() {
		assertTrue(PhoneRootPolicy.isPrimaryRoot(RuntimeHostMode.PHONE,
				R.id.glass_addons_catalog_fragment, R.id.glass_addons_catalog_fragment));
		assertTrue(PhoneRootPolicy.isPrimaryRoot(RuntimeHostMode.PHONE,
				R.id.settings_fragment, R.id.settings_fragment));
		assertFalse(PhoneRootPolicy.isPrimaryRoot(RuntimeHostMode.PHONE,
				R.id.glass_addons_catalog_fragment, R.id.youtube_fragment));
		assertTrue(PhoneRootPolicy.isPrimaryRoot(RuntimeHostMode.AA_PROJECTION,
				R.id.youtube_fragment, R.id.dashboard_fragment));
	}

	@Test
	public void validatesAndNormalizesPhoneRoots() {
		assertFalse(PhoneRootPolicy.isPhoneRoot(R.id.folders_fragment));
		assertTrue(PhoneRootPolicy.isPhoneRoot(R.id.glass_addons_catalog_fragment));
		assertTrue(PhoneRootPolicy.isPhoneRoot(R.id.car_finder_fragment));
		assertTrue(PhoneRootPolicy.isPhoneRoot(R.id.settings_fragment));
		assertFalse(PhoneRootPolicy.isPhoneRoot(R.id.youtube_fragment));
		assertEquals(R.id.glass_addons_catalog_fragment,
				PhoneRootPolicy.normalizePhoneRoot(R.id.youtube_fragment));
	}

	@Test
	public void descendantsPreserveThePhoneRoot() {
		assertEquals(R.id.glass_addons_catalog_fragment, PhoneRootPolicy.resolvePhoneRoot(
				R.id.glass_addons_catalog_fragment, R.id.youtube_fragment));
		assertEquals(R.id.glass_addons_catalog_fragment, PhoneRootPolicy.resolvePhoneRoot(
				R.id.glass_addons_catalog_fragment, R.id.folders_fragment));
		assertEquals(R.id.settings_fragment, PhoneRootPolicy.resolvePhoneRoot(
				R.id.glass_addons_catalog_fragment, R.id.settings_fragment));
	}

	@Test
	public void phoneChromeDependsOnSelectedRootAndHiddenBars() {
		assertFalse(PhoneRootPolicy.showAddonNavBar(RuntimeHostMode.PHONE,
				R.id.dashboard_fragment, false));
		assertFalse(PhoneRootPolicy.showAddonNavBar(RuntimeHostMode.PHONE,
				R.id.settings_fragment, false));
		assertFalse(PhoneRootPolicy.showAddonNavBar(RuntimeHostMode.PHONE,
				R.id.dashboard_fragment, true));
		assertTrue(PhoneRootPolicy.showPhoneBottomMenu(RuntimeHostMode.PHONE, false));
		assertFalse(PhoneRootPolicy.showPhoneBottomMenu(RuntimeHostMode.PHONE, true));
	}

	@Test
	public void automotiveHostsKeepAddonNavAndNeverShowPhoneMenu() {
		assertTrue(PhoneRootPolicy.showAddonNavBar(RuntimeHostMode.AA_PROJECTION,
				R.id.dashboard_fragment, false));
		assertTrue(PhoneRootPolicy.showAddonNavBar(RuntimeHostMode.MIRROR,
				R.id.settings_fragment, false));
		assertFalse(PhoneRootPolicy.showAddonNavBar(RuntimeHostMode.AA_PROJECTION,
				R.id.dashboard_fragment, true));
		assertFalse(PhoneRootPolicy.showPhoneBottomMenu(RuntimeHostMode.AA_PROJECTION, false));
		assertFalse(PhoneRootPolicy.showPhoneBottomMenu(RuntimeHostMode.MIRROR, false));
	}
}
