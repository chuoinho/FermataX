package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DashboardAddonTypographyPolicyTest {
	@Test
	public void automotiveAddonTitlesReceiveEmphasisOnlyOnAddonCards() {
		assertEquals(1.15F, DashboardModelBuilder.addonTitleScale(true, true), 0F);
		assertEquals(1F, DashboardModelBuilder.addonTitleScale(true, false), 0F);
		assertEquals(1F, DashboardModelBuilder.addonTitleScale(false, true), 0F);
		assertEquals(1F, DashboardModelBuilder.addonTitleScale(false, false), 0F);
	}
}
