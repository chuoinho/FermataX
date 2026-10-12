package me.aap.fermata.ui.glass.media;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;

/**
 * Controller connecting the Glass Media Player card with Fermata's media service and binder.
 */
public class GlassMediaPlayerController implements FermataServiceUiBinder.Listener {

    public interface Listener {
        void onMediaUpdated();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String displayedTitle = "No Media Playing";
    private String displayedArtist = "FermataX";
    private Bitmap albumArt = null;
    private long positionMs = 0L;
    private long durationMs = 0L;
    private boolean playing = false;
    private boolean favorite = false;

    @Nullable
    private FermataServiceUiBinder binder;
    @Nullable
    private Listener listener;

    public GlassMediaPlayerController() {
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public synchronized void bind(@NonNull FermataServiceUiBinder binder) {
        this.binder = binder;
        binder.addBroadcastListener(this);
        PlaybackSnapshot snapshot = binder.getPlaybackSnapshot();
        if (snapshot != null) {
            applySnapshot(snapshot);
        } else {
            resetToIdle();
        }
    }

    public synchronized void unbind() {
        if (binder != null) {
            binder.removeBroadcastListener(this);
            binder = null;
        }
    }

    public synchronized void applySnapshot(@Nullable PlaybackSnapshot snapshot) {
        if (snapshot == null) {
            resetToIdle();
            return;
        }

        MediaMetadataCompat metadata = snapshot.getMetadata();
        PlaybackStateCompat state = snapshot.getState();
        MediaLib.PlayableItem item = snapshot.getItem();

        String title = snapshot.getDisplayTitle().toString();
        String artist = "FermataX";
        Bitmap art = null;
        long duration = 0L;

        if (metadata != null) {
            String metaArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            if (metaArtist != null && !metaArtist.trim().isEmpty()) {
                artist = metaArtist.trim();
            }
            art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
            if (art == null) {
                art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            }
            duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }

        boolean isPlay = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;
        long pos = state != null ? state.getPosition() : 0L;
        boolean fav = item != null && item.isFavoriteItem();

        updatePlayback(title, artist, pos, duration, isPlay, fav);
        this.albumArt = art;
    }

    public synchronized void applyMetadataAndState(@Nullable MediaMetadataCompat metadata, @Nullable PlaybackStateCompat state) {
        String title = "Unknown Title";
        String artist = "FermataX";
        Bitmap art = null;
        long duration = 0L;

        if (metadata != null) {
            CharSequence t = metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE);
            if (t != null && t.length() > 0) title = t.toString();
            CharSequence a = metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST);
            if (a != null && a.length() > 0) artist = a.toString();
            art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }

        boolean isPlay = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;
        long pos = state != null ? state.getPosition() : 0L;

        updatePlayback(title, artist, pos, duration, isPlay, false);
        this.albumArt = art;
    }

    public synchronized void updatePlayback(String title, String artist, long positionMs, long durationMs,
                                           boolean isPlaying, boolean isFavorite) {
        this.displayedTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Unknown";
        this.displayedArtist = (artist != null && !artist.trim().isEmpty()) ? artist.trim() : "FermataX";
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.playing = isPlaying;
        this.favorite = isFavorite;
        notifyListener();
    }

    public synchronized void resetToIdle() {
        this.displayedTitle = "No Media Playing";
        this.displayedArtist = "FermataX";
        this.albumArt = null;
        this.positionMs = 0L;
        this.durationMs = 0L;
        this.playing = false;
        this.favorite = false;
        notifyListener();
    }

    private void notifyListener() {
        if (listener == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onMediaUpdated();
        } else {
            mainHandler.post(listener::onMediaUpdated);
        }
    }

    // Transport Actions
    public void togglePlayPause() {
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb == null) return;
        if (playing) {
            cb.onPause();
        } else {
            cb.onPlay();
        }
    }

    public void skipNext() {
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb != null) {
            cb.onSkipToNext();
        }
    }

    public void skipPrevious() {
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb != null) {
            cb.onSkipToPrevious();
        }
    }

    public void seekTo(long pos) {
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb != null) {
            cb.onSeekTo(pos);
        }
    }

    public void toggleFavorite() {
        if (binder == null) return;
        MediaLib.PlayableItem item = binder.getCurrentItem();
        if (item != null && item.getLib() != null && item.getLib().getFavorites() != null) {
            if (item.isFavoriteItem()) {
                item.getLib().getFavorites().removeItem(item);
            } else {
                item.getLib().getFavorites().addItem(item);
            }
            this.favorite = item.isFavoriteItem();
            notifyListener();
        }
    }

    @Override
    public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
        if (binder != null) {
            applySnapshot(binder.getPlaybackSnapshot());
        }
    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        if (binder != null) {
            applySnapshot(binder.getPlaybackSnapshot());
        }
    }

    @Override
    public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
        applySnapshot(snapshot);
    }

    @Override
    public void onPlaybackTimelineChanged(PlaybackTimelineSnapshot snapshot) {
        if (binder != null) {
            applySnapshot(binder.getPlaybackSnapshot());
        }
    }

    public String getDisplayedTitle() {
        return displayedTitle;
    }

    public String getDisplayedArtist() {
        return displayedArtist;
    }

    @Nullable
    public Bitmap getAlbumArt() {
        return albumArt;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
