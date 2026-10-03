package me.aap.fermata.addon.web.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TikTokFloatingNavViewTest {

	@Test
	public void testJsBridgeMultiListenerSupport() {
		TikTokJsBridge bridge = new TikTokJsBridge();
		AtomicBoolean listener1Playing = new AtomicBoolean(false);
		AtomicBoolean listener2Playing = new AtomicBoolean(false);
		AtomicInteger listener1Count = new AtomicInteger(0);

		TikTokJsBridge.Listener l1 = new TikTokJsBridge.Listener() {
			@Override
			public void onPlayingChanged(boolean playing) {
				listener1Playing.set(playing);
				listener1Count.incrementAndGet();
			}

			@Override
			public void onNowPlayingChanged(String id, String name, String art, long positionMs,
					long durationMs, boolean playing) {
			}
		};

		TikTokJsBridge.Listener l2 = new TikTokJsBridge.Listener() {
			@Override
			public void onPlayingChanged(boolean playing) {
				listener2Playing.set(playing);
			}

			@Override
			public void onNowPlayingChanged(String id, String name, String art, long positionMs,
					long durationMs, boolean playing) {
			}
		};

		bridge.addListener(l1);
		bridge.addListener(l2);

		bridge.setPlaying(true);
		assertTrue(listener1Playing.get());
		assertTrue(listener2Playing.get());
		assertEquals(1, listener1Count.get());

		bridge.setPlaying(false);
		assertFalse(listener1Playing.get());
		assertFalse(listener2Playing.get());
		assertEquals(2, listener1Count.get());

		bridge.removeListener(l1);
		bridge.setPlaying(true);
		assertFalse(listener1Playing.get()); // l1 removed, stayed false
		assertTrue(listener2Playing.get());  // l2 received true
		assertEquals(2, listener1Count.get());
	}

	@Test
	public void testMediaSessionBridgeActionScripts() {
		String nextScript = TikTokMediaSessionBridge.getActionScript(
				me.aap.fermata.media.service.MediaSessionCallback.ControlOnlyAction.NEXT_TRACK);
		assertNotNull(nextScript);
		assertTrue(nextScript.contains("__cntNext"));

		String prevScript = TikTokMediaSessionBridge.getActionScript(
				me.aap.fermata.media.service.MediaSessionCallback.ControlOnlyAction.PREVIOUS_TRACK);
		assertNotNull(prevScript);
		assertTrue(prevScript.contains("__cntPrev"));

		String playScript = TikTokMediaSessionBridge.getActionScript(
				me.aap.fermata.media.service.MediaSessionCallback.ControlOnlyAction.PLAY);
		assertNotNull(playScript);
		assertTrue(playScript.contains("v.play()"));

		String pauseScript = TikTokMediaSessionBridge.getActionScript(
				me.aap.fermata.media.service.MediaSessionCallback.ControlOnlyAction.PAUSE);
		assertNotNull(pauseScript);
		assertTrue(pauseScript.contains("v.pause()"));
	}
}
