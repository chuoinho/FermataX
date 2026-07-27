package me.aap.fermata.addon.stremio.ui.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioConfigWebIsolationTest {
	@Test
	public void bothIsolationFeaturesAreMandatory() {
		assertTrue(StremioConfigWebIsolation.requiredFeaturesAvailable(true, true));
		assertFalse(StremioConfigWebIsolation.requiredFeaturesAvailable(false, true));
		assertFalse(StremioConfigWebIsolation.requiredFeaturesAvailable(true, false));
	}

	@Test
	public void documentStartGuardBlocksDirectNetworkEscapePrimitives() {
		String script = StremioConfigWebIsolation.networkGuardScript();
		assertTrue(script.contains("WebSocket"));
		assertTrue(script.contains("WebTransport"));
		assertTrue(script.contains("Worker"));
		assertTrue(script.contains("RTCPeerConnection"));
		assertTrue(script.contains("sendBeacon"));
		assertTrue(script.contains("XMLHttpRequest.prototype"));
		assertTrue(script.contains("HTMLFormElement.prototype"));
		assertTrue(script.contains("m!=='GET'"));
	}
}
