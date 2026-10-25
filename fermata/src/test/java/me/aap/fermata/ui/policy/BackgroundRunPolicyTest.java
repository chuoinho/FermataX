package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundRunPolicyTest {
	@Test
	public void backgroundIsUnrestrictedOnlyWhenBothSystemChecksPass() {
		assertTrue(BackgroundRunPolicy.isUnrestricted(true, false));
		assertFalse(BackgroundRunPolicy.isUnrestricted(false, false));
		assertFalse(BackgroundRunPolicy.isUnrestricted(true, true));
		assertFalse(BackgroundRunPolicy.isUnrestricted(false, true));
	}

	@Test
	public void selectsKnownManagerForHonorAndXiaomi() {
		var honor = BackgroundRunPolicy.selectVendorTarget("HONOR",
				target -> target.packageName().equals("com.huawei.systemmanager"));
		var xiaomi = BackgroundRunPolicy.selectVendorTarget("Redmi",
				target -> target.packageName().equals("com.miui.securitycenter"));

		assertEquals("com.huawei.systemmanager", honor.packageName());
		assertEquals("com.miui.securitycenter", xiaomi.packageName());
		assertFalse(honor.appDetailsFallback());
		assertFalse(xiaomi.appDetailsFallback());
		var honorVariant = BackgroundRunPolicy.selectVendorTarget("Honor",
				target -> target.packageName().equals("com.hihonor.systemmanager"));
		assertEquals("com.hihonor.systemmanager", honorVariant.packageName());
	}

	@Test
	public void recognizesAllRequestedOemFamiliesAndAliases() {
		assertEquals(BackgroundRunPolicy.Vendor.XIAOMI, BackgroundRunPolicy.vendorFor("POCO"));
		assertEquals(BackgroundRunPolicy.Vendor.OPPO_REALME_ONEPLUS, BackgroundRunPolicy.vendorFor("realme"));
		assertEquals(BackgroundRunPolicy.Vendor.OPPO_REALME_ONEPLUS, BackgroundRunPolicy.vendorFor("OnePlus"));
		assertEquals(BackgroundRunPolicy.Vendor.OPPO_REALME_ONEPLUS, BackgroundRunPolicy.vendorFor("OPPO"));
		assertEquals(BackgroundRunPolicy.Vendor.VIVO, BackgroundRunPolicy.vendorFor("iQOO"));
	}

	@Test
	public void fallsBackToAppDetailsWhenKnownVendorHasNoAvailableManager() {
		var selected = BackgroundRunPolicy.selectVendorTarget("OnePlus", target -> false);

		assertNotNull(selected);
		assertTrue(selected.appDetailsFallback());
	}

	@Test
	public void unrecognizedManufacturerDoesNotGetAnOemGuide() {
		assertNull(BackgroundRunPolicy.selectVendorTarget("Google", target -> true));
	}

	@Test
	public void initialWarningIsShownOnlyWhileRestrictedAndNotPreviouslyAsked() {
		assertTrue(BackgroundRunPolicy.shouldOfferInitialWarning(false, false));
		assertFalse(BackgroundRunPolicy.shouldOfferInitialWarning(true, false));
		assertFalse(BackgroundRunPolicy.shouldOfferInitialWarning(false, true));
	}

	@Test
	public void staleBackgroundRestrictionMessageRequiresAlwaysLocationAndRestrictedBackground() {
		assertTrue(BackgroundRunPolicy.shouldExplainBackgroundRestriction(true, true, false));
		assertFalse(BackgroundRunPolicy.shouldExplainBackgroundRestriction(false, true, false));
		assertFalse(BackgroundRunPolicy.shouldExplainBackgroundRestriction(true, false, false));
		assertFalse(BackgroundRunPolicy.shouldExplainBackgroundRestriction(true, true, true));
	}
}
