package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainActivityPrefsTest {
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
}
