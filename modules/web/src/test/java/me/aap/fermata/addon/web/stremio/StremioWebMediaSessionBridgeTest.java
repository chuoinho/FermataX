package me.aap.fermata.addon.web.stremio;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioWebMediaSessionBridgeTest {
	@Test
	public void readyHandlersDoNotAuthorizePlayAndPlayingCannotBeToggled() {
		var state = new StremioWebMediaSessionBridge.State();
		state.open("document");
		state.setHandler("document", "play", true);
		state.setHandler("document", "pause", true);
		assertFalse(state.canClaim());
		assertFalse(state.canDispatch("play"));
		assertFalse(state.canDispatch("pause"));
		state.setPlayback("document", "playing");
		assertFalse(state.canDispatch("play"));
		assertTrue(state.canDispatch("pause"));
		state.setPlayback("document", "paused");
		assertTrue(state.canDispatch("play"));
		assertFalse(state.canDispatch("pause"));
	}

	@Test
	public void revokedBridgeCannotKeepItsPlayableState() throws Exception {
		var bridge = new StremioWebMediaSessionBridge(null);
		var field = StremioWebMediaSessionBridge.class.getDeclaredField("state");
		field.setAccessible(true);
		var state = (StremioWebMediaSessionBridge.State) field.get(bridge);
		state.open("document");
		state.setPlayback("document", "playing");
		state.setHandler("document", "play", true);
		bridge.onControlOnlyRevoked();
		assertFalse(state.canClaim());
		assertFalse(bridge.isPlaybackActive());
	}

	@Test
	public void aNewDocumentHasANewControlOnlyContentIdentity() throws Exception {
		var bridge = new StremioWebMediaSessionBridge(null);
		var field = StremioWebMediaSessionBridge.class.getDeclaredField("state");
		field.setAccessible(true);
		var state = (StremioWebMediaSessionBridge.State) field.get(bridge);
		state.open("first");
		String first = bridge.controlOnlyContentKey();
		state.open("second");
		assertFalse(first.equals(bridge.controlOnlyContentKey()));
	}

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
		assertFalse(state.canDispatch("play"));
		assertTrue(state.canDispatch("nexttrack"));
		assertEquals(STATE_PLAYING, state.playbackState());
		assertEquals(ACTION_SKIP_TO_NEXT, state.actions());
		assertEquals(ACTION_PLAY | ACTION_SKIP_TO_NEXT, state.controlOnlyActions());

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
	public void rejectsStaleDocumentGeneration() {
		assertTrue(StremioWebMediaSessionBridge.isCurrentDocumentGeneration(7L, 7L));
		assertFalse(StremioWebMediaSessionBridge.isCurrentDocumentGeneration(8L, 7L));
		assertTrue(StremioWebMediaSessionBridge.shimSource(37L).contains("var generation = 37"));
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
