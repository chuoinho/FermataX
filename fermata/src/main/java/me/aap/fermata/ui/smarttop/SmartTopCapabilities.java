package me.aap.fermata.ui.smarttop;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

/** Addon-neutral capabilities used to derive the remaining SmartTop controls. */
public record SmartTopCapabilities(boolean canFavorite, boolean canPlayPause) {
	public SmartTopCapabilities(boolean canFavorite) { this(canFavorite, true); }
	public static final SmartTopCapabilities NONE = new SmartTopCapabilities(false, false);

	public static SmartTopCapabilities current(boolean favorite) {
		return new SmartTopCapabilities(favorite, true);
	}

	public static SmartTopCapabilities suggestion(boolean favorite) {
		return new SmartTopCapabilities(favorite, true);
	}

	public static SmartTopCapabilities web(boolean playPause) { return new SmartTopCapabilities(false, playPause); }

	public static SmartTopCapabilities web(long actions, int state) {
		boolean playing = (state == STATE_PLAYING) || (state == STATE_BUFFERING);
		return web((actions & (playing ? ACTION_PAUSE : ACTION_PLAY)) != 0L);
	}
}
