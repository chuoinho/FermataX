package me.aap.fermata.ui.glass.media;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.activity.MainActivityPrefs;

/**
 * Controller connecting the Glass Media Player card with Fermata's media service and binder.
 */
public class GlassMediaPlayerController implements FermataServiceUiBinder.Listener {

    public interface Listener {
        void onMediaUpdated();
    }

    public enum PresentationState {
        IDLE_NO_RECENT,
        IDLE_WITH_RECENT,
        BUFFERING,
        PLAYING,
        PAUSED,
        LIVE,
        ERROR
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = this::onTick;

    private PresentationState presentationState = PresentationState.IDLE_NO_RECENT;
    private long presentationGeneration = 0L;
    private MediaLib.PlayableItem currentItem = null;
    private me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode timelineMode = me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE;

    private String displayedTitle = "No Media Playing";
    private String displayedArtist = "FermataX";
    private Bitmap albumArt = null;
    private long positionMs = 0L;
    private long durationMs = 0L;
    private long lastPositionUpdateTime = 0L;
    private float playbackSpeed = 1.0f;
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
        stopTicker();
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

        this.presentationGeneration++;
        this.currentItem = snapshot.getItem();

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

        int stateInt = state != null ? state.getState() : PlaybackStateCompat.STATE_NONE;
        boolean isPlay = stateInt == PlaybackStateCompat.STATE_PLAYING;
        long pos = state != null ? state.getPosition() : 0L;
        boolean fav = item != null && item.isFavoriteItem();
        if (state != null) {
            float sp = state.getPlaybackSpeed();
            this.playbackSpeed = sp > 0 ? sp : 1.0f;
        }

        if (item == null && (stateInt == PlaybackStateCompat.STATE_NONE || stateInt == PlaybackStateCompat.STATE_STOPPED)) {
            resetToIdle();
            return;
        }

        if (stateInt == PlaybackStateCompat.STATE_ERROR) {
            this.presentationState = PresentationState.ERROR;
        } else if (stateInt == PlaybackStateCompat.STATE_BUFFERING || stateInt == PlaybackStateCompat.STATE_CONNECTING) {
            this.presentationState = PresentationState.BUFFERING;
        } else if (duration <= 0L && isPlay) {
            this.timelineMode = me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE;
            this.presentationState = PresentationState.LIVE;
        } else if (isPlay) {
            this.timelineMode = me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE;
            this.presentationState = PresentationState.PLAYING;
        } else {
            this.timelineMode = (duration > 0L) ? me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE : me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE;
            this.presentationState = (this.timelineMode == me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE) ? PresentationState.LIVE : PresentationState.PAUSED;
        }

        updatePlayback(title, artist, pos, duration, isPlay, fav);
        this.albumArt = art;
    }

    public synchronized void applyMetadataAndState(@Nullable MediaMetadataCompat metadata, @Nullable PlaybackStateCompat state) {
        this.presentationGeneration++;
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

        int stateInt = state != null ? state.getState() : PlaybackStateCompat.STATE_NONE;
        boolean isPlay = stateInt == PlaybackStateCompat.STATE_PLAYING;
        long pos = state != null ? state.getPosition() : 0L;
        if (state != null) {
            float sp = state.getPlaybackSpeed();
            this.playbackSpeed = sp > 0 ? sp : 1.0f;
        }

        if (stateInt == PlaybackStateCompat.STATE_ERROR) {
            this.presentationState = PresentationState.ERROR;
        } else if (stateInt == PlaybackStateCompat.STATE_BUFFERING || stateInt == PlaybackStateCompat.STATE_CONNECTING) {
            this.presentationState = PresentationState.BUFFERING;
        } else if (duration <= 0L && isPlay) {
            this.timelineMode = me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE;
            this.presentationState = PresentationState.LIVE;
        } else if (isPlay) {
            this.timelineMode = me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE;
            this.presentationState = PresentationState.PLAYING;
        } else {
            this.timelineMode = (duration > 0L) ? me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE : me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE;
            this.presentationState = (this.timelineMode == me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE) ? PresentationState.LIVE : PresentationState.PAUSED;
        }

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
        this.lastPositionUpdateTime = SystemClock.elapsedRealtime();
        updateTicker();
        notifyListener();
    }

    public synchronized void resetToIdle() {
        stopTicker();
        this.currentItem = null;
        this.albumArt = null;
        this.positionMs = 0L;
        this.durationMs = 0L;
        this.lastPositionUpdateTime = 0L;
        this.playing = false;
        this.favorite = false;
        this.timelineMode = me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.HIDDEN;

        String recentTitle = null;
        String recentSubtitle = null;
        try {
            MainActivityPrefs prefs = MainActivityPrefs.get();
            if (prefs != null) {
                recentTitle = prefs.getStringPref(MainActivityPrefs.SMART_TOP_RECENT_TITLE);
                recentSubtitle = prefs.getStringPref(MainActivityPrefs.SMART_TOP_RECENT_SUBTITLE);
            }
        } catch (Throwable ignored) {}

        if (recentTitle != null && !recentTitle.trim().isEmpty()) {
            this.displayedTitle = recentTitle.trim();
            this.displayedArtist = (recentSubtitle != null && !recentSubtitle.trim().isEmpty()) ? recentSubtitle.trim() : "Recent Media";
            this.presentationState = PresentationState.IDLE_WITH_RECENT;
        } else {
            this.displayedTitle = "No Media Playing";
            this.displayedArtist = "FermataX";
            this.presentationState = PresentationState.IDLE_NO_RECENT;
        }
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
        if (snapshot == null) return;
        synchronized (this) {
            if (snapshot.presentationGeneration() < this.presentationGeneration) {
                return;
            }
            if (this.currentItem != null && snapshot.item() != null && !snapshot.item().equals(this.currentItem)) {
                return;
            }
            this.presentationGeneration = snapshot.presentationGeneration();
            this.currentItem = snapshot.item();
            this.timelineMode = snapshot.mode();
            this.positionMs = Math.max(0L, snapshot.positionMillis());
            if (snapshot.durationMillis() > 0L) {
                this.durationMs = snapshot.durationMillis();
            } else if (snapshot.mode() == me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE) {
                this.durationMs = 0L;
            }
            this.playing = snapshot.playing();
            if (snapshot.mode() == me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE) {
                this.presentationState = PresentationState.LIVE;
            } else {
                this.presentationState = this.playing ? PresentationState.PLAYING : PresentationState.PAUSED;
            }
            this.lastPositionUpdateTime = SystemClock.elapsedRealtime();
            updateTicker();
        }
        notifyListener();
    }

    private void onTick() {
        synchronized (this) {
            if (!playing) return;
            if (binder != null) {
                MediaEngine eng = (binder.getMediaSessionCallback() != null) ?
                        binder.getMediaSessionCallback().getEngine() : null;
                if (eng != null) {
                    eng.getPosition().main().onSuccess(pos -> {
                        if (pos != null && pos >= 0L) {
                            synchronized (GlassMediaPlayerController.this) {
                                GlassMediaPlayerController.this.positionMs = pos;
                                GlassMediaPlayerController.this.lastPositionUpdateTime = SystemClock.elapsedRealtime();
                            }
                            notifyListener();
                        }
                    });
                }
            }
            updateTicker();
        }
        notifyListener();
    }

    private synchronized void updateTicker() {
        mainHandler.removeCallbacks(progressTicker);
        if (playing) {
            mainHandler.postDelayed(progressTicker, 1000);
        }
    }

    private synchronized void stopTicker() {
        mainHandler.removeCallbacks(progressTicker);
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

    public synchronized long getPositionMs() {
        if (!playing || lastPositionUpdateTime <= 0L) {
            return positionMs;
        }
        long delta = SystemClock.elapsedRealtime() - lastPositionUpdateTime;
        long current = positionMs + (long) (delta * playbackSpeed);
        if (durationMs > 0L) {
            current = Math.min(current, durationMs);
        }
        return Math.max(0L, current);
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

    public PresentationState getPresentationState() {
        return presentationState;
    }

    public me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode getTimelineMode() {
        return timelineMode;
    }

    public boolean isSeekable() {
        return timelineMode == me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE && durationMs > 0L;
    }

    public long getPresentationGeneration() {
        return presentationGeneration;
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
