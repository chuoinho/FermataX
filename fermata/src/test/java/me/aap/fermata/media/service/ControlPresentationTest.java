package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.reflect.Proxy;

import org.junit.Test;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.control.ControlPresentation;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode;

public class ControlPresentationTest {
	@Test
	public void emptySnapshotHasNoMediaOrTitle() {
		ControlPresentation presentation = ControlPresentation.from(snapshot(null, STATE_STOPPED, 0L,
				null), null, AutomotiveConnectionState.State.DISCONNECTED);

		assertFalse(presentation.hasMedia);
		assertEquals("", presentation.title);
		assertEquals("", presentation.subtitle);
		assertFalse(presentation.canPlayPause);
	}

	@Test
	public void playingAndBufferingPreferPauseWhenSupported() {
		ControlPresentation playing = ControlPresentation.from(snapshot(item("Episode"), STATE_PLAYING,
				ACTION_PAUSE, metadata()), null, AutomotiveConnectionState.State.CONNECTED);
		ControlPresentation buffering = ControlPresentation.from(snapshot(item("Episode"), STATE_BUFFERING,
				ACTION_PAUSE, metadata()), null, AutomotiveConnectionState.State.CONNECTED);

		assertTrue(playing.playing);
		assertTrue(playing.canPlayPause);
		assertTrue(buffering.playing);
		assertTrue(buffering.canPlayPause);
	}

	@Test
	public void pausedAndStoppedUsePlayWhenSupported() {
		ControlPresentation paused = ControlPresentation.from(snapshot(item("Episode"), STATE_PAUSED,
				ACTION_PLAY, metadata()), null, AutomotiveConnectionState.State.DISCONNECTED);
		ControlPresentation stopped = ControlPresentation.from(snapshot(item("Episode"), STATE_STOPPED,
				ACTION_PLAY, metadata()), null, AutomotiveConnectionState.State.DISCONNECTED);

		assertFalse(paused.playing);
		assertTrue(paused.canPlayPause);
		assertFalse(stopped.playing);
		assertTrue(stopped.canPlayPause);
	}

	@Test
	public void previousAndNextFollowIndependentActionBits() {
		ControlPresentation presentation = ControlPresentation.from(snapshot(item("Episode"), STATE_PAUSED,
				ACTION_SKIP_TO_PREVIOUS, metadata()), null, AutomotiveConnectionState.State.DISCONNECTED);

		assertTrue(presentation.canPrevious);
		assertFalse(presentation.canNext);
	}

	@Test
	public void controlOnlyPlayingDoesNotRequireAnItem() {
		ControlPresentation presentation = ControlPresentation.from(snapshot(null, STATE_PLAYING,
				ACTION_PAUSE | ACTION_SKIP_TO_NEXT, metadata()), null,
				AutomotiveConnectionState.State.CONNECTED);

		assertTrue(presentation.hasMedia);
		assertTrue(presentation.canPlayPause);
		assertTrue(presentation.canNext);
		assertFalse(presentation.canPrevious);
		assertFalse(presentation.canSeek);
	}

	@Test
	public void staleMetadataAndDefaultActionsDoNotCreatePlayback() {
		ControlPresentation presentation = ControlPresentation.from(snapshot(null, STATE_NONE,
				ACTION_PLAY | ACTION_PAUSE, metadata()), null,
				AutomotiveConnectionState.State.DISCONNECTED);

		assertFalse(presentation.hasMedia);
		assertFalse(presentation.canPlayPause);
		assertEquals(null, presentation.metadata);
	}

	@Test
	public void playPauseToggleSupportsBothDirections() {
		ControlPresentation playing = ControlPresentation.from(snapshot(null, STATE_PLAYING,
				ACTION_PLAY_PAUSE, metadata()), null, AutomotiveConnectionState.State.CONNECTED);
		ControlPresentation paused = ControlPresentation.from(snapshot(null, STATE_PAUSED,
				ACTION_PLAY_PAUSE, metadata()), null, AutomotiveConnectionState.State.CONNECTED);

		assertTrue(playing.canPlayPause);
		assertTrue(paused.canPlayPause);
	}

	@Test
	public void seekRequiresMatchingSeekableTimelineAndClampsToDuration() {
		PlayableItem item = item("Episode");
		ControlPresentation presentation = ControlPresentation.from(snapshot(item, STATE_PLAYING,
				ACTION_PAUSE | ACTION_SEEK_TO, metadata()), new PlaybackTimelineSnapshot(item, 1L, Mode.SEEKABLE,
				20_000L, 10_000L, true), AutomotiveConnectionState.State.DISCONNECTED);

		assertTrue(presentation.canSeek);
		assertEquals(10_000L, presentation.durationMillis);
		assertEquals(10_000L, presentation.positionMillis);
	}

	@Test
	public void seekRequiresAdvertisedSeekAction() {
		PlayableItem item = item("Episode");
		ControlPresentation presentation = ControlPresentation.from(snapshot(item, STATE_PLAYING,
				ACTION_PAUSE, metadata()), new PlaybackTimelineSnapshot(item, 1L, Mode.SEEKABLE,
				5_000L, 10_000L, true), AutomotiveConnectionState.State.DISCONNECTED);

		assertFalse(presentation.canSeek);
	}

	@Test
	public void timelineForDifferentItemIsIgnored() {
		ControlPresentation presentation = ControlPresentation.from(snapshot(item("Current"), STATE_PLAYING,
				ACTION_PAUSE, metadata()), new PlaybackTimelineSnapshot(item("Stale"), 1L,
				Mode.SEEKABLE, 5_000L, 10_000L, true), AutomotiveConnectionState.State.DISCONNECTED);

		assertFalse(presentation.canSeek);
		assertEquals(0L, presentation.durationMillis);
		assertEquals(0L, presentation.positionMillis);
	}

	@Test
	public void automotiveStatesRemainDistinctPresentationValues() {
		PlaybackSnapshot snapshot = snapshot(item("Episode"), STATE_PAUSED, ACTION_PLAY, metadata());
		ControlPresentation disconnected = ControlPresentation.from(snapshot, null,
				AutomotiveConnectionState.State.DISCONNECTED);
		ControlPresentation connected = ControlPresentation.from(snapshot, null,
				AutomotiveConnectionState.State.CONNECTED);
		ControlPresentation visible = ControlPresentation.from(snapshot, null,
				AutomotiveConnectionState.State.APP_VISIBLE);

		assertNotEquals(disconnected.automotiveState, connected.automotiveState);
		assertNotEquals(connected.automotiveState, visible.automotiveState);
	}

	private static PlaybackSnapshot snapshot(PlayableItem item, int state, long actions,
			MediaMetadataCompat metadata) {
		return new PlaybackSnapshot(1L, item, new PlaybackStateCompat.Builder()
				.setActions(actions).setState(state, 0L, 1F).build(), metadata);
	}

	private static MediaMetadataCompat metadata() {
		return new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Episode title")
				.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Podcast")
				.build();
	}

	private static PlayableItem item(String name) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> name;
					case "getName" -> name;
					default -> null;
				});
	}
}
