package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ControlFragmentTimeTest {
	@Test
	public void displayTimeKeepsZeroWhileSeekbarRetainsUsableMinimum() {
		assertEquals(0, ControlFragment.displaySeconds(0L));
		assertEquals(1, ControlFragment.progressSeconds(0L));
		assertEquals(1, ControlFragment.displaySeconds(1_999L));
	}
}
