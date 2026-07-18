package me.aap.fermata.media.lib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ItemContainerPolicyTest {
	@Test
	public void missingIdsArePrunedOnlyAfterDefinitiveResolution() {
		assertTrue(ItemContainer.shouldPruneMissing(false, false));
		assertFalse(ItemContainer.shouldPruneMissing(true, false));
		assertFalse(ItemContainer.shouldPruneMissing(false, true));
		assertFalse(ItemContainer.shouldPruneMissing(true, true));
	}
}
