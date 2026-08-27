package me.aap.fermata.addon.web.stremio;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioWebMediaSessionBridgeTest {
	@Test
	public void usesOnlyTheExactHostedOriginAndRequiredFeatures() {
		assertTrue(StremioWebMediaSessionBridge.isAllowedOrigin("https://web.stremio.com"));
		assertFalse(StremioWebMediaSessionBridge.isAllowedOrigin("http://web.stremio.com"));
		assertFalse(StremioWebMediaSessionBridge.isAllowedOrigin("https://foo.web.stremio.com"));
		assertTrue(StremioWebMediaSessionBridge.supportsBridge(true, true));
		assertFalse(StremioWebMediaSessionBridge.supportsBridge(false, true));
		assertFalse(StremioWebMediaSessionBridge.supportsBridge(true, false));
	}

	@Test
	public void limitsMessagesToTheVersionedControlSchema() {
		assertTrue(StremioWebMediaSessionBridge.isBoundedPayload("{}"));
		assertFalse(StremioWebMediaSessionBridge.isBoundedPayload(null));
		assertFalse(StremioWebMediaSessionBridge.isBoundedPayload("x".repeat(4097)));
		assertTrue(StremioWebMediaSessionBridge.isSupportedMessageType("READY"));
		assertTrue(StremioWebMediaSessionBridge.isSupportedMessageType("HANDLER_REMOVED"));
		assertFalse(StremioWebMediaSessionBridge.isSupportedMessageType("EXECUTE"));
	}

	@Test
	public void acceptsOnlyCurrentDocumentHandlersAndState() {
		var state = new StremioWebMediaSessionBridge.State();
		state.open("first");
		state.setPlayback("first", "playing");
		state.setHandler("first", "play", true);
		state.setHandler("first", "nexttrack", true);
		assertTrue(state.canClaim());
		assertTrue(state.canDispatch("play"));
		assertTrue(state.canDispatch("nexttrack"));
		assertEquals(STATE_PLAYING, state.playbackState());
		assertEquals(ACTION_SKIP_TO_NEXT, state.actions());

		state.setPlayback("stale", "paused");
		state.setHandler("stale", "pause", true);
		assertEquals(STATE_PLAYING, state.playbackState());
		assertFalse(state.canDispatch("pause"));

		state.setHandler("first", "play", false);
		state.setHandler("first", "nexttrack", false);
		assertFalse(state.canClaim());
		assertFalse(state.canDispatch("play"));
		assertEquals(0L, state.actions());
		state.close("first");
		assertEquals(STATE_NONE, state.playbackState());
	}

	@Test
	public void shimIsIdempotentAndObservesNativeMediaSessionWithoutReplacingNavigator() {
		String source = StremioWebMediaSessionBridge.shimSource();
		assertTrue(source.contains("window.__fermataStremioMediaSessionV1"));
		assertTrue(source.contains("window.top !== window"));
		assertTrue(source.contains("Object.assign(Object.create(null), {play:true,pause:true,nexttrack:true})"));
		assertTrue(source.contains("HANDLER_REMOVED"));
		assertTrue(source.contains("var nativeSession = navigator.mediaSession"));
		assertTrue(source.contains("var observeNative = function(mediaSession)"));
		assertTrue(source.contains("setHandler.call(mediaSession, action, callback)"));
		assertFalse(source.contains("Object.defineProperty(navigator, 'mediaSession'" +
				"{value:nativeSession"));
		assertFalse(source.contains("seekforward"));
		assertFalse(source.contains("previoustrack"));
	}
}
