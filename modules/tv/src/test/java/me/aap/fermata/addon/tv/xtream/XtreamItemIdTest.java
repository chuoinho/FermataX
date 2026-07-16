package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

public class XtreamItemIdTest extends Assert {
	private static final String SCHEME = "tvxs";

	@Test
	public void origKeepsOriginalSchemeTail() {
		String tail = "tvxs:42:cat%3Alive%2Fhd:Live%3A%20HD:1234";
		String id = "SOURCE_TYPE#1:" + tail;

		assertEquals(tail, XtreamItemId.orig(id, SCHEME));
		assertEquals(id, XtreamItemId.orig(id, "missing"));
	}
}
