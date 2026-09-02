package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.utils.ui.view.NavBarView;

public class MainActivityPrefsTest {
	@Test
	public void voiceControlIsEnabledByDefault() {
		assertTrue(MainActivityPrefs.VOICE_CONTROl_ENABLED.getDefaultValue().getAsBoolean());
	}

	@Test
	public void smartTopV2IsEnabledByDefaultAfterReleaseGatesPass() {
		assertTrue(MainActivityPrefs.SMART_TOP_V2_ENABLED.getDefaultValue().getAsBoolean());
		assertTrue(MainActivityPrefs.SMART_TOP_BACKGROUND_ENABLED.getDefaultValue().getAsBoolean());
	}

	@Test
	public void setupIsShownOnlyForAnEmptyFreshInstall() {
		assertTrue(MainActivityPrefs.shouldShowInitialSetup(10, 10, false, false));
		assertFalse(MainActivityPrefs.shouldShowInitialSetup(10, 10, true, false));
		assertFalse(MainActivityPrefs.shouldShowInitialSetup(10, 10, true, true));
	}

	@Test
	public void setupIsSkippedForUpdatesAndRestoredPreferences() {
		assertFalse(MainActivityPrefs.shouldShowInitialSetup(10, 20, false, false));
		assertFalse(MainActivityPrefs.shouldShowInitialSetup(10, 10, true, false));
		assertFalse(MainActivityPrefs.shouldShowInitialSetup(10, 10, false, true));
	}

	@Test
	public void legacyAndInvalidNavPositionsMigrateOneWayToLeft() {
		assertEquals(NavBarView.POSITION_LEFT,
				MainActivityPrefs.normalizeNavBarPosition(NavBarView.POSITION_BOTTOM));
		assertEquals(NavBarView.POSITION_LEFT,
				MainActivityPrefs.normalizeNavBarPosition(-1));
		assertEquals(NavBarView.POSITION_LEFT,
				MainActivityPrefs.normalizeNavBarPosition(NavBarView.POSITION_LEFT));
		assertEquals(NavBarView.POSITION_RIGHT,
				MainActivityPrefs.normalizeNavBarPosition(NavBarView.POSITION_RIGHT));
	}
}
