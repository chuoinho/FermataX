package me.aap.utils.pref;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreferenceViewStoreTest {
	@Test
	public void pastedUriIsStoredImmediately() {
		assertTrue(PreferenceView.shouldStoreOnChange(true, 0, 60));
		assertTrue(PreferenceView.shouldStoreOnChange(true, 5, 60));
	}

	@Test
	public void typedUriWaitsForSubmit() {
		assertFalse(PreferenceView.shouldStoreOnChange(true, 0, 1));
		assertFalse(PreferenceView.shouldStoreOnChange(true, 1, 0));
	}

	@Test
	public void plainTextIsAlwaysStored() {
		assertTrue(PreferenceView.shouldStoreOnChange(false, 0, 1));
	}
}
