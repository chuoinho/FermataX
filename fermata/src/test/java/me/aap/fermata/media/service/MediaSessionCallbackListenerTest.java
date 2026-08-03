package me.aap.fermata.media.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.support.v4.media.session.PlaybackStateCompat;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.utils.event.EventBroadcaster;

public class MediaSessionCallbackListenerTest {
	@Test
	public void listenerDoesNotExposeLegacyPlaybackStateCallback() {
		for (var method : MediaSessionCallback.Listener.class.getDeclaredMethods())
			assertFalse("Legacy playback-state callback must stay retired",
					method.getName().equals("onPlaybackStateChanged"));
	}

	@Test
	public void snapshotOnlyListenerReceivesNormalStateTransition() {
		PlaybackStateCompat previousState = new PlaybackStateCompat.Builder()
				.setState(PlaybackStateCompat.STATE_PAUSED, 100L, 1F).build();
		PlaybackStateCompat currentState = new PlaybackStateCompat.Builder()
				.setState(PlaybackStateCompat.STATE_PLAYING, 150L, 1F).build();
		PlaybackSnapshot previous = new PlaybackSnapshot(1L, null, previousState, null);
		PlaybackSnapshot current = new PlaybackSnapshot(2L, null, currentState, null);
		AtomicReference<PlaybackSnapshot> receivedPrevious = new AtomicReference<>();
		AtomicReference<PlaybackSnapshot> receivedCurrent = new AtomicReference<>();
		SnapshotBroadcaster broadcaster = new SnapshotBroadcaster();
		MediaSessionCallback.Listener listener = new MediaSessionCallback.Listener() {
			@Override
			public void onPlaybackSnapshotChanged(MediaSessionCallback callback,
					PlaybackSnapshot oldSnapshot, PlaybackSnapshot newSnapshot) {
				receivedPrevious.set(oldSnapshot);
				receivedCurrent.set(newSnapshot);
			}
		};

		broadcaster.addBroadcastListener(listener);
		broadcaster.publish(previous, current);
		broadcaster.removeBroadcastListener(listener);

		assertSame(previous, receivedPrevious.get());
		assertSame(current, receivedCurrent.get());
		assertSame(currentState, receivedCurrent.get().getState());
	}

	@Test
	public void snapshotOnlyListenerReceivesInitialNullItemState() {
		PlaybackStateCompat initialState = new PlaybackStateCompat.Builder()
				.setState(PlaybackStateCompat.STATE_NONE, 0L, 0F).build();
		PlaybackSnapshot current = new PlaybackSnapshot(1L, null, initialState, null);
		AtomicReference<PlaybackSnapshot> receivedPrevious = new AtomicReference<>();
		AtomicReference<PlaybackSnapshot> receivedCurrent = new AtomicReference<>();
		SnapshotBroadcaster broadcaster = new SnapshotBroadcaster();
		MediaSessionCallback.Listener listener = new MediaSessionCallback.Listener() {
			@Override
			public void onPlaybackSnapshotChanged(MediaSessionCallback callback,
					PlaybackSnapshot oldSnapshot, PlaybackSnapshot newSnapshot) {
				receivedPrevious.set(oldSnapshot);
				receivedCurrent.set(newSnapshot);
			}
		};

		broadcaster.addBroadcastListener(listener);
		broadcaster.publish(null, current);
		broadcaster.removeBroadcastListener(listener);

		assertNull(receivedPrevious.get());
		assertSame(current, receivedCurrent.get());
		assertNull(receivedCurrent.get().getItem());
		assertSame(initialState, receivedCurrent.get().getState());
	}

	private static final class SnapshotBroadcaster
			implements EventBroadcaster<MediaSessionCallback.Listener> {
		private final Collection<ListenerRef<MediaSessionCallback.Listener>> listeners =
				new LinkedList<>();

		@Override
		public Collection<ListenerRef<MediaSessionCallback.Listener>> getBroadcastEventListeners() {
			return listeners;
		}

		void publish(PlaybackSnapshot previous, PlaybackSnapshot current) {
			fireBroadcastEvent(listener ->
					listener.onPlaybackSnapshotChanged(null, previous, current));
		}
	}
}
