package me.aap.fermata.addon.web.tiktok;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.MediaMetadataCompat;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.media.service.MediaSessionCallback.ControlOnlyAction;

public class TikTokMediaSessionBridgeTest {

	@Test
	public void testJsBridgeCallbacks() {
		AtomicBoolean playingRef = new AtomicBoolean(false);
		AtomicReference<String> titleRef = new AtomicReference<>();

		TikTokJsBridge bridge = new TikTokJsBridge(new TikTokJsBridge.Listener() {
			@Override
			public void onPlayingChanged(boolean playing) {
				playingRef.set(playing);
			}

			@Override
			public void onNowPlayingChanged(String id, String name, String art, long positionMs,
					long durationMs, boolean playing) {
				titleRef.set(name);
				playingRef.set(playing);
			}
		});

		bridge.setPlaying(true);
		assertTrue(playingRef.get());

		bridge.setPlaying(false);
		assertFalse(playingRef.get());

		bridge.setNowPlaying("v123", "Cool Video", "", 5000, 30000, true);
		assertEquals("Cool Video", titleRef.get());
		assertTrue(playingRef.get());

		// Ensure webLog does not crash
		bridge.webLog("test log message");
	}

	@Test
	public void testStateTransitionsAndPlaybackState() {
		TikTokMediaSessionBridge.State state = new TikTokMediaSessionBridge.State();
		assertFalse(state.isPlaybackActive());
		assertEquals(STATE_NONE, state.playbackState());
		assertFalse(state.canClaim());

		state.setPlaying(true);
		assertTrue(state.isPlaybackActive());
		assertTrue(state.isPlaying());
		assertFalse(state.isPaused());
		assertEquals(STATE_PLAYING, state.playbackState());
		assertTrue(state.canClaim());

		long playingActions = state.actions();
		assertEquals(ACTION_PAUSE | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS, playingActions);

		state.setPlaying(false);
		assertTrue(state.isPlaybackActive());
		assertFalse(state.isPlaying());
		assertTrue(state.isPaused());
		assertEquals(STATE_PAUSED, state.playbackState());
		assertTrue(state.canClaim());

		long pausedActions = state.actions();
		assertEquals(ACTION_PLAY | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS, pausedActions);

		long controlOnlyActions = state.controlOnlyActions();
		assertEquals(ACTION_PLAY | ACTION_PAUSE | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS,
				controlOnlyActions);

		state.reset();
		assertFalse(state.isPlaybackActive());
		assertEquals(STATE_NONE, state.playbackState());
		assertFalse(state.canClaim());
	}

	@Test
	public void testMetadataConstruction() {
		TikTokMediaSessionBridge.State state = new TikTokMediaSessionBridge.State();
		state.setMetadata("v789", "Funny Cat", "TikTok");

		assertEquals("Funny Cat", state.getTitle());
		assertEquals("TikTok", state.getArtist());
		assertEquals("v789", state.getId());
		assertNotNull(state.metadata());

		// Fallback when title is empty
		state.setMetadata("v789", "", "");
		assertEquals("TikTok Video", state.getTitle());
		assertNotNull(state.metadata());
	}

	@Test
	public void testDispatchActionScripts() {
		String nextScript = TikTokMediaSessionBridge.getActionScript(ControlOnlyAction.NEXT_TRACK);
		assertNotNull(nextScript);
		assertTrue("Must call __cntNext", nextScript.contains("__cntNext"));

		String prevScript = TikTokMediaSessionBridge.getActionScript(ControlOnlyAction.PREVIOUS_TRACK);
		assertNotNull(prevScript);
		assertTrue("Must call __cntPrev", prevScript.contains("__cntPrev"));

		String playScript = TikTokMediaSessionBridge.getActionScript(ControlOnlyAction.PLAY);
		assertNotNull(playScript);
		assertTrue("Must call play()", playScript.contains(".play()"));

		String pauseScript = TikTokMediaSessionBridge.getActionScript(ControlOnlyAction.PAUSE);
		assertNotNull(pauseScript);
		assertTrue("Must call pause()", pauseScript.contains(".pause()"));
	}

	@Test
	public void testControlOnlyDelegateAttributes() {
		TikTokMediaSessionBridge bridge = new TikTokMediaSessionBridge(null);
		assertEquals(TikTokAddon.class.getName(), bridge.controlOnlyAddonClass());

		String key1 = bridge.controlOnlyContentKey();
		assertNotNull(key1);

		bridge.onControlOnlyRevoked();
		assertFalse(bridge.isControlOnlyActive());
	}
}
