package me.aap.fermata.addon.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;

public class StremioProductUiContractTest {
	@Test
	public void rootExposesTextSearchAndAddonOwnsVoiceTarget() {
		assertTrue(java.util.Arrays.asList(StremioAction.values()).contains(StremioAction.SEARCH));
		assertTrue(VoiceSearchAddon.class.isAssignableFrom(StremioAddon.class));
		assertEquals("stremio", StremioSessionCoordinator.VOICE_TARGET);
	}
}
