package me.aap.fermata.addon.tv;

import org.junit.Assert;
import org.junit.Test;

import me.aap.utils.pref.BasicPreferenceStore;

public class TvRootItemTest extends Assert {

	@Test
	public void missingSourceTypeDefaultsToM3uForLegacySources() {
		BasicPreferenceStore ps = new BasicPreferenceStore();

		assertEquals(TvSourceItem.TYPE_M3U, TvRootItem.getSourceType(ps, 3));
	}

	@Test
	public void xtreamSourceTypeIsPreserved() {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		ps.applyStringPref(TvRootItem.sourceTypePref(4), TvSourceItem.TYPE_XTREAM);

		assertEquals(TvSourceItem.TYPE_XTREAM, TvRootItem.getSourceType(ps, 4));
	}

	@Test
	public void unknownSourceTypeFallsBackToM3u() {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		ps.applyStringPref(TvRootItem.sourceTypePref(5), "unknown");

		assertEquals(TvSourceItem.TYPE_M3U, TvRootItem.getSourceType(ps, 5));
	}
}
