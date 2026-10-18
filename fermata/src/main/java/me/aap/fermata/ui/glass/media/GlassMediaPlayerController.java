package me.aap.fermata.ui.glass.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.ControlOnlyPresentation;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.fragment.DashboardPlayableNavigator;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;
import me.aap.fermata.ui.smarttop.SmartTopArtworkResolver;

/**
 * Controller connecting the Glass Media Player card with Fermata's media service and binder.
 * Unifies playback presentation across engine sessions, ControlOnly sessions, and idle/recent states.
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
    @Nullable
    private MediaLib.PlayableItem currentItem = null;
    @Nullable
    private MediaLib.PlayableItem recentItem = null;
    @Nullable
    private ControlOnlyPresentation controlOnly = null;
    private PlaybackTimelinePolicy.Mode timelineMode = PlaybackTimelinePolicy.Mode.HIDDEN;

    private String displayedTitle = "No Media Playing";
    private String displayedArtist = "FermataX";
    @Nullable
    private Bitmap albumArt = null;
    private long positionMs = 0L;
    private long durationMs = 0L;
    private long lastPositionUpdateTime = 0L;
    private float playbackSpeed = 1.0f;
    private boolean playing = false;
    private boolean favorite = false;
    private boolean canSkipNext = false;
    private boolean canSkipPrevious = false;

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
        if (this.binder == binder) {
            refresh();
            return;
        }
        if (this.binder != null) {
            this.binder.removeBroadcastListener(this);
        }
        this.binder = binder;
        binder.addBroadcastListener(this);
        refresh();
    }

    public synchronized void unbind() {
        stopTicker();
        if (binder != null) {
            binder.removeBroadcastListener(this);
            binder = null;
        }
    }

    public synchronized void refresh() {
        if (binder == null) {
            resetToIdle();
            return;
        }
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb == null) {
            resetToIdle();
            return;
        }
        ControlOnlyPresentation web = cb.getControlOnlyPresentation();
        if (web != null) {
            applyControlOnly(web);
            return;
        }
        PlaybackSnapshot snapshot = binder.getPlaybackSnapshot();
        applySnapshot(snapshot);
    }

    public synchronized void applyControlOnly(@NonNull ControlOnlyPresentation web) {
        this.presentationGeneration++;
        this.controlOnly = web;
        this.currentItem = null;

        MediaMetadataCompat metadata = web.metadata();
        CharSequence metaTitle = (metadata != null) ? metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE) : null;
        if (metaTitle == null || metaTitle.length() == 0) {
            if (metadata != null) metaTitle = metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE);
        }
        FermataApplication app = FermataApplication.get();
        AddonManager am = (app != null) ? app.getAddonManager() : null;
        AddonInfo info = (am != null) ? am.getAddonInfo(web.addonClass()) : null;
        Context ctx = (binder != null && binder.getLib() != null) ? binder.getLib().getContext() : null;
        CharSequence addonName = (info != null && info.addonName != 0 && ctx != null) ?
                ctx.getString(info.addonName) : "Web Media";
        String title = (metaTitle != null && metaTitle.length() > 0) ? metaTitle.toString().trim() : addonName.toString();

        CharSequence metaSub = (metadata != null) ? metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE) : null;
        if (metaSub == null || metaSub.length() == 0) {
            if (metadata != null) metaSub = metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST);
        }
        String subtitle = (metaSub != null && metaSub.length() > 0) ? metaSub.toString().trim() : addonName.toString();

        int stateInt = web.state();
        boolean isPlay = (stateInt == PlaybackStateCompat.STATE_PLAYING);
        if (stateInt == PlaybackStateCompat.STATE_ERROR) {
            this.presentationState = PresentationState.ERROR;
        } else if (stateInt == PlaybackStateCompat.STATE_BUFFERING || stateInt == PlaybackStateCompat.STATE_CONNECTING) {
            this.presentationState = PresentationState.BUFFERING;
        } else if (isPlay) {
            this.presentationState = PresentationState.PLAYING;
        } else {
            this.presentationState = PresentationState.PAUSED;
        }

        this.timelineMode = PlaybackTimelinePolicy.Mode.HIDDEN;
        this.positionMs = 0L;
        this.durationMs = 0L;
        this.playing = isPlay;
        this.favorite = false;
        this.canSkipNext = (web.actions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0;
        this.canSkipPrevious = (web.actions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0;
        this.albumArt = (metadata != null) ? SmartTopArtworkResolver.directArtworkBitmap(metadata) : null;
        this.displayedTitle = title;
        this.displayedArtist = subtitle;
        this.lastPositionUpdateTime = SystemClock.elapsedRealtime();

        updateTicker();
        notifyListener();
    }

    public synchronized void applySnapshot(@Nullable PlaybackSnapshot snapshot) {
        if (snapshot == null) {
            resetToIdle();
            return;
        }

        MediaSessionCallback cb = (binder != null) ? binder.getMediaSessionCallback() : null;
        if (cb != null) {
            ControlOnlyPresentation web = cb.getControlOnlyPresentation();
            if (web != null) {
                applyControlOnly(web);
                return;
            }
        }

        MediaLib.PlayableItem item = snapshot.getItem();
        PlaybackStateCompat state = snapshot.getState();
        int stateInt = (state != null) ? state.getState() : PlaybackStateCompat.STATE_NONE;

        if (item == null && (stateInt == PlaybackStateCompat.STATE_NONE || stateInt == PlaybackStateCompat.STATE_STOPPED)) {
            resetToIdle();
            return;
        }

        this.presentationGeneration++;
        long currentGen = this.presentationGeneration;
        this.currentItem = item;
        this.controlOnly = null;

        MediaMetadataCompat metadata = snapshot.getMetadata();
        String title = snapshot.getDisplayTitle().toString();
        String artist = "FermataX";
        long duration = 0L;
        Bitmap art = null;

        if (metadata != null) {
            String metaArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            if (metaArtist != null && !metaArtist.trim().isEmpty()) {
                artist = metaArtist.trim();
            }
            art = SmartTopArtworkResolver.directArtworkBitmap(metadata);
            duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }

        boolean isPlay = (stateInt == PlaybackStateCompat.STATE_PLAYING);
        long pos = (state != null) ? state.getPosition() : 0L;
        boolean isLive = (item != null && item.isLiveStream());
        boolean itemSeekable = (item != null && item.isSeekable());
        long actions = (state != null) ? state.getActions() : 0L;
        boolean engineSeekable = (actions & PlaybackStateCompat.ACTION_SEEK_TO) != 0;

        if (state != null) {
            float sp = state.getPlaybackSpeed();
            this.playbackSpeed = sp > 0 ? sp : 1.0f;
        }

        if (stateInt == PlaybackStateCompat.STATE_ERROR) {
            this.presentationState = PresentationState.ERROR;
            this.timelineMode = PlaybackTimelinePolicy.Mode.HIDDEN;
        } else {
            if (stateInt == PlaybackStateCompat.STATE_BUFFERING || stateInt == PlaybackStateCompat.STATE_CONNECTING) {
                this.presentationState = PresentationState.BUFFERING;
            } else if (isLive) {
                this.presentationState = PresentationState.LIVE;
            } else if (isPlay) {
                this.presentationState = PresentationState.PLAYING;
            } else {
                this.presentationState = PresentationState.PAUSED;
            }
            this.timelineMode = PlaybackTimelinePolicy.resolve(isLive, itemSeekable, engineSeekable, duration);
        }

        this.favorite = (item != null) && !item.isExternal() && item.isFavoriteItem();
        this.canSkipNext = (actions & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0;
        this.canSkipPrevious = (actions & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0;
        this.displayedTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Unknown";
        this.displayedArtist = (artist != null && !artist.trim().isEmpty()) ? artist.trim() : "FermataX";
        this.positionMs = Math.max(0L, pos);
        this.durationMs = Math.max(0L, duration);
        this.playing = isPlay;
        this.albumArt = art;
        this.lastPositionUpdateTime = SystemClock.elapsedRealtime();

        if (this.albumArt == null && item != null) {
            var mediaDataSupplier = item.getMediaData();
            if (mediaDataSupplier != null) {
                mediaDataSupplier.main().onSuccess(data -> {
                    if (data == null) return;
                    synchronized (GlassMediaPlayerController.this) {
                        if (GlassMediaPlayerController.this.presentationGeneration != currentGen) return;
                        Bitmap itemArt = SmartTopArtworkResolver.directArtworkBitmap(data);
                        if (itemArt != null) {
                            GlassMediaPlayerController.this.albumArt = itemArt;
                            notifyListener();
                        }
                    }
                });
            }
        }

        updateTicker();
        notifyListener();
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
            art = SmartTopArtworkResolver.directArtworkBitmap(metadata);
            duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }

        int stateInt = state != null ? state.getState() : PlaybackStateCompat.STATE_NONE;
        boolean isPlay = stateInt == PlaybackStateCompat.STATE_PLAYING;
        long pos = state != null ? state.getPosition() : 0L;
        long actions = state != null ? state.getActions() : 0L;
        boolean engineSeekable = (actions & PlaybackStateCompat.ACTION_SEEK_TO) != 0;
        if (state != null) {
            float sp = state.getPlaybackSpeed();
            this.playbackSpeed = sp > 0 ? sp : 1.0f;
        }

        if (stateInt == PlaybackStateCompat.STATE_ERROR) {
            this.presentationState = PresentationState.ERROR;
            this.timelineMode = PlaybackTimelinePolicy.Mode.HIDDEN;
        } else {
            if (stateInt == PlaybackStateCompat.STATE_BUFFERING || stateInt == PlaybackStateCompat.STATE_CONNECTING) {
                this.presentationState = PresentationState.BUFFERING;
            } else if (isPlay) {
                this.presentationState = PresentationState.PLAYING;
            } else {
                this.presentationState = PresentationState.PAUSED;
            }
            this.timelineMode = PlaybackTimelinePolicy.resolve(false, true, engineSeekable, duration);
        }

        this.canSkipNext = (actions & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0;
        this.canSkipPrevious = (actions & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0;
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
        this.presentationGeneration++;
        long reqGen = this.presentationGeneration;
        this.currentItem = null;
        this.controlOnly = null;
        this.albumArt = null;
        this.positionMs = 0L;
        this.durationMs = 0L;
        this.lastPositionUpdateTime = 0L;
        this.playing = false;
        this.favorite = false;
        this.canSkipNext = false;
        this.canSkipPrevious = false;
        this.timelineMode = PlaybackTimelinePolicy.Mode.HIDDEN;

        String recentTitle = null;
        String recentSubtitle = null;
        try {
            MainActivityPrefs prefs = MainActivityPrefs.get();
            if (prefs != null) {
                recentTitle = prefs.getStringPref(MainActivityPrefs.SMART_TOP_RECENT_TITLE);
                recentSubtitle = prefs.getStringPref(MainActivityPrefs.SMART_TOP_RECENT_SUBTITLE);
            }
        } catch (Throwable ignored) {}

        Context ctx = (binder != null && binder.getLib() != null) ? binder.getLib().getContext() : null;
        if (ctx == null) {
            FermataApplication app = FermataApplication.get();
            if (app != null) ctx = app.getApplicationContext();
        }

        if (recentTitle != null && !recentTitle.trim().isEmpty()) {
            this.displayedTitle = recentTitle.trim();
            this.displayedArtist = (recentSubtitle != null && !recentSubtitle.trim().isEmpty()) ? recentSubtitle.trim() : safeGetString(ctx, R.string.glass_media_recent_media, "Recent Media");
            this.presentationState = PresentationState.IDLE_WITH_RECENT;
        } else {
            this.displayedTitle = safeGetString(ctx, R.string.glass_media_no_playing, "No Media Playing");
            this.displayedArtist = "FermataX";
            this.presentationState = PresentationState.IDLE_NO_RECENT;
        }

        if (binder != null && binder.getLib() != null) {
            binder.getLib().getLastPlayedItem().main().onCompletion((item, err) -> {
                if (err != null || item == null) return;
                synchronized (GlassMediaPlayerController.this) {
                    if (GlassMediaPlayerController.this.presentationGeneration != reqGen) return;
                    if (GlassMediaPlayerController.this.presentationState != PresentationState.IDLE_NO_RECENT &&
                            GlassMediaPlayerController.this.presentationState != PresentationState.IDLE_WITH_RECENT) return;
                    GlassMediaPlayerController.this.recentItem = item;
                    GlassMediaPlayerController.this.displayedTitle = item.getName();
                    CharSequence sub = (item.getParent() != null) ? item.getParent().getName() : safeGetString(binder.getLib().getContext(), R.string.glass_media_recent_media, "Recent Media");
                    GlassMediaPlayerController.this.displayedArtist = sub.toString();
                    GlassMediaPlayerController.this.presentationState = PresentationState.IDLE_WITH_RECENT;
                }
                notifyListener();
            });
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
        if (controlOnly != null) {
            if (!cb.isControlOnlyCurrent(controlOnly.leaseId())) {
                refresh();
                return;
            }
            cb.dispatchControlOnly(controlOnly.leaseId(),
                    playing ? MediaSessionCallback.ControlOnlyAction.PAUSE : MediaSessionCallback.ControlOnlyAction.PLAY);
            refresh();
            return;
        }
        if (playing) {
            cb.onPause();
        } else {
            cb.onPlay();
        }
    }

    public void skipNext() {
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb == null) return;
        if (controlOnly != null) {
            if (!cb.isControlOnlyCurrent(controlOnly.leaseId())) {
                refresh();
                return;
            }
            cb.dispatchControlOnly(controlOnly.leaseId(), MediaSessionCallback.ControlOnlyAction.NEXT_TRACK);
            return;
        }
        cb.onSkipToNext();
    }

    public void skipPrevious() {
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb == null) return;
        if (controlOnly != null) {
            if (!cb.isControlOnlyCurrent(controlOnly.leaseId())) {
                refresh();
                return;
            }
            cb.dispatchControlOnly(controlOnly.leaseId(), MediaSessionCallback.ControlOnlyAction.PREVIOUS_TRACK);
            return;
        }
        cb.onSkipToPrevious();
    }

    public void seekTo(long pos) {
        if (controlOnly != null) return;
        if (binder == null) return;
        MediaSessionCallback cb = binder.getMediaSessionCallback();
        if (cb != null) {
            cb.onSeekTo(pos);
        }
    }

    public void toggleFavorite() {
        if (controlOnly != null) return;
        if (binder == null) return;
        MediaLib.PlayableItem item = (currentItem != null) ? currentItem : binder.getCurrentItem();
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

    public void resumeRecent(@Nullable MainActivityDelegate activity) {
        if (activity == null) return;
        if (recentItem != null) {
            DashboardPlayableNavigator.playAndGoToPlayable(activity, recentItem);
        } else {
            activity.showFragment(me.aap.fermata.R.id.recent_fragment);
        }
    }

    @Override
    public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
        refresh();
    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        refresh();
    }

    @Override
    public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
        if (binder != null) {
            MediaSessionCallback cb = binder.getMediaSessionCallback();
            if (cb != null && cb.getControlOnlyPresentation() != null) {
                applyControlOnly(cb.getControlOnlyPresentation());
                return;
            }
        }
        applySnapshot(snapshot);
    }

    @Override
    public void onPlaybackStopped() {
        refresh();
    }

    @Override
    public void onPlaybackError(String message) {
        refresh();
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
            } else if (snapshot.mode() == PlaybackTimelinePolicy.Mode.LIVE) {
                this.durationMs = 0L;
            }
            this.playing = snapshot.playing();
            if (snapshot.mode() == PlaybackTimelinePolicy.Mode.LIVE) {
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
            if (!playing || controlOnly != null) return;
            if (binder != null) {
                MediaEngine eng = (binder.getMediaSessionCallback() != null) ?
                        binder.getMediaSessionCallback().getEngine() : null;
                if (eng != null) {
                    long reqGen = this.presentationGeneration;
                    eng.getPosition().main().onSuccess(pos -> {
                        if (pos != null && pos >= 0L) {
                            synchronized (GlassMediaPlayerController.this) {
                                if (GlassMediaPlayerController.this.presentationGeneration != reqGen) return;
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
        if (playing && controlOnly == null) {
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
        if (!playing || lastPositionUpdateTime <= 0L || controlOnly != null) {
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

    public boolean canSkipNext() {
        return canSkipNext;
    }

    public boolean canSkipPrevious() {
        return canSkipPrevious;
    }

    @Nullable
    public ControlOnlyPresentation getControlOnly() {
        return controlOnly;
    }

    @Nullable
    public MediaLib.PlayableItem getCurrentItem() {
        return currentItem;
    }

    @Nullable
    public MediaLib.PlayableItem getRecentItem() {
        return recentItem;
    }

    public PresentationState getPresentationState() {
        return presentationState;
    }

    public PlaybackTimelinePolicy.Mode getTimelineMode() {
        return timelineMode;
    }

    public boolean isSeekable() {
        return timelineMode == PlaybackTimelinePolicy.Mode.SEEKABLE && durationMs > 0L;
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

    private static String safeGetString(@Nullable Context context, int resId, String fallback) {
        if (context == null) return fallback;
        try {
            return context.getString(resId);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
