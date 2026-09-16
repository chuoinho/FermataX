package me.aap.fermata.ui.control;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode;

/** Immutable, side-effect-free state for the phone control surface. */
public final class ControlPresentation {
	public final boolean hasMedia;
	public final boolean playing;
	public final boolean canPlayPause;
	public final boolean canPrevious;
	public final boolean canNext;
	public final boolean canSeek;
	public final long positionMillis;
	public final long durationMillis;
	@NonNull
	public final CharSequence title;
	@NonNull
	public final CharSequence subtitle;
	@NonNull
	public final AutomotiveConnectionState.State automotiveState;
	@Nullable
	public final MediaMetadataCompat metadata;

	private ControlPresentation(boolean hasMedia, boolean playing, boolean canPlayPause,
			boolean canPrevious, boolean canNext, boolean canSeek, long positionMillis,
			long durationMillis, @NonNull CharSequence title, @NonNull CharSequence subtitle,
			@NonNull AutomotiveConnectionState.State automotiveState,
			@Nullable MediaMetadataCompat metadata) {
		this.hasMedia = hasMedia;
		this.playing = playing;
		this.canPlayPause = canPlayPause;
		this.canPrevious = canPrevious;
		this.canNext = canNext;
		this.canSeek = canSeek;
		this.positionMillis = positionMillis;
		this.durationMillis = durationMillis;
		this.title = title;
		this.subtitle = subtitle;
		this.automotiveState = automotiveState;
		this.metadata = metadata;
	}

	@NonNull
	public static ControlPresentation from(@Nullable PlaybackSnapshot playback,
			@Nullable PlaybackTimelineSnapshot timeline,
			@Nullable AutomotiveConnectionState.State automotiveState) {
		AutomotiveConnectionState.State resolvedAutomotiveState = (automotiveState == null) ?
				AutomotiveConnectionState.State.DISCONNECTED : automotiveState;
		if (playback == null) return empty(resolvedAutomotiveState);

		PlayableItem item = playback.getItem();
		MediaMetadataCompat metadata = playback.getMetadata();
		PlaybackStateCompat state = playback.getState();
		int playbackState = state.getState();
		long actions = state.getActions();
		if (!hasPresentableSession(item != null, playbackState, actions)) {
			return empty(resolvedAutomotiveState);
		}
		boolean playing = (playbackState == STATE_PLAYING) || (playbackState == STATE_BUFFERING);
		boolean canSeek = (item != null) && hasAction(actions, ACTION_SEEK_TO) &&
				(timeline != null) && Objects.equals(item, timeline.item()) &&
				(timeline.mode() == Mode.SEEKABLE) && (timeline.durationMillis() > 0L);
		long duration = canSeek ? timeline.durationMillis() : 0L;
		long position = canSeek ? Math.min(Math.max(0L, timeline.positionMillis()), duration) : 0L;

		return new ControlPresentation(true, playing,
				playing ? hasAnyAction(actions, ACTION_PAUSE, ACTION_PLAY_PAUSE) :
						hasAnyAction(actions, ACTION_PLAY, ACTION_PLAY_PAUSE),
				hasAction(actions, ACTION_SKIP_TO_PREVIOUS),
				hasAction(actions, ACTION_SKIP_TO_NEXT), canSeek, position, duration,
				text(playback.getDisplayTitle()), subtitle(metadata), resolvedAutomotiveState, metadata);
	}

	static boolean hasPresentableSession(boolean hasItem, int state, long actions) {
		if (hasItem) return true;
		boolean active = (state == STATE_PLAYING) || (state == STATE_PAUSED) ||
				(state == STATE_BUFFERING);
		long transport = ACTION_PLAY | ACTION_PAUSE | ACTION_PLAY_PAUSE |
				ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS;
		return active && ((actions & transport) != 0L);
	}

	@NonNull
	private static ControlPresentation empty(@NonNull AutomotiveConnectionState.State automotiveState) {
		return empty(automotiveState, null);
	}

	@NonNull
	private static ControlPresentation empty(@NonNull AutomotiveConnectionState.State automotiveState,
			@Nullable MediaMetadataCompat metadata) {
		return new ControlPresentation(false, false, false, false, false, false, 0L, 0L, "", "",
				automotiveState, null);
	}

	private static boolean hasAction(long actions, long action) {
		return (actions & action) != 0L;
	}

	private static boolean hasAnyAction(long actions, long first, long second) {
		return hasAction(actions, first) || hasAction(actions, second);
	}

	@NonNull
	private static CharSequence subtitle(@Nullable MediaMetadataCompat metadata) {
		if (metadata == null) return "";
		String artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
		if ((artist == null) || artist.trim().isEmpty()) {
			artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE);
		}
		return text(artist);
	}

	@NonNull
	private static CharSequence text(@Nullable CharSequence value) {
		return (value == null) ? "" : value;
	}
}
