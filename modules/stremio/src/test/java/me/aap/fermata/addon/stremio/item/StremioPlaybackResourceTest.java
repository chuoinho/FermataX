package me.aap.fermata.addon.stremio.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioPlaybackResourceTest {
	@Test
	public void subtitleTrackIdentityIsStableAndProviderSpecific() {
		long first = StremioPlaybackResource.stableTrackId("provider-a:subtitle-1");
		assertEquals(first,
				StremioPlaybackResource.stableTrackId("provider-a:subtitle-1"));
		assertNotEquals(first,
				StremioPlaybackResource.stableTrackId("provider-b:subtitle-1"));
		assertTrue(first > 0x4000_0000_0000_0000L);
	}
}
