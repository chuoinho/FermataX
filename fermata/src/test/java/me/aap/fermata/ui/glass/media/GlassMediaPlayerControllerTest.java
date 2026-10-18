package me.aap.fermata.ui.glass.media;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.utils.function.ProgressiveResultConsumer;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.ControlOnlyPresentation;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

@RunWith(RobolectricTestRunner.class)
public class GlassMediaPlayerControllerTest {

    @Test
    public void testMediaCardPropertiesAndFormatting() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        controller.updatePlayback("Midnight Drive", "Synthwave City", 120000L, 250000L, true, false);

        assertEquals("Midnight Drive", controller.getDisplayedTitle());
        assertEquals("Synthwave City", controller.getDisplayedArtist());
        assertTrue(controller.isPlaying());
        assertFalse(controller.isFavorite());
        assertEquals(120000L, controller.getPositionMs());
        assertEquals(250000L, controller.getDurationMs());

        assertEquals("02:00", GlassMediaPlayerController.formatTime(120000L));
        assertEquals("04:10", GlassMediaPlayerController.formatTime(250000L));
    }

    @Test
    public void testMetadataAndStateBinding() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Starboy")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "The Weeknd")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 230000L)
                .build();

        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 45000L, 1.0f, SystemClock.elapsedRealtime())
                .build();

        controller.applyMetadataAndState(metadata, state);

        assertEquals("Starboy", controller.getDisplayedTitle());
        assertEquals("The Weeknd", controller.getDisplayedArtist());
        assertTrue(controller.isPlaying());
        assertEquals(45000L, controller.getPositionMs());
        assertEquals(230000L, controller.getDurationMs());
    }

    @Test
    public void testIdleState() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        controller.resetToIdle();

        assertEquals("No Media Playing", controller.getDisplayedTitle());
        assertEquals("FermataX", controller.getDisplayedArtist());
        assertFalse(controller.isPlaying());
        assertEquals(0L, controller.getPositionMs());
        assertEquals(0L, controller.getDurationMs());
        assertEquals(GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT, controller.getPresentationState());
    }

    @Test
    public void testGlassMediaPlayerViewBinding() {
        GlassMediaPlayerView view = new GlassMediaPlayerView(RuntimeEnvironment.getApplication());
        assertNotNull(view);

        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        view.setController(controller);

        controller.updatePlayback("Blinding Lights", "The Weeknd", 60000L, 200000L, true, true);
        view.updateFromController();
    }

    @Test
    public void testPlaybackTimelineInterpolation() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        MediaLib.PlayableItem mockItem = createMockPlayableItem("Test Track", false);
        PlaybackTimelineSnapshot timeline = new PlaybackTimelineSnapshot(
                mockItem, 1L, PlaybackTimelinePolicy.Mode.SEEKABLE,
                10000L, 60000L, true);

        controller.onPlaybackTimelineChanged(timeline);
        assertTrue(controller.isPlaying());
        assertTrue(controller.getPositionMs() >= 10000L);
        assertEquals(60000L, controller.getDurationMs());
        assertEquals(GlassMediaPlayerController.PresentationState.PLAYING, controller.getPresentationState());
        assertTrue(controller.isSeekable());
    }

    @Test
    public void testAtomicGenerationDiscardsStaleCallback() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();

        MediaMetadataCompat metaA = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Track A")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 100000L)
                .build();
        PlaybackStateCompat stateA = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 1000L, 1.0f, SystemClock.elapsedRealtime())
                .build();
        controller.applyMetadataAndState(metaA, stateA);
        long genA = controller.getPresentationGeneration();
        assertEquals("Track A", controller.getDisplayedTitle());

        // Now Track B arrives
        MediaMetadataCompat metaB = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Track B")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 200000L)
                .build();
        PlaybackStateCompat stateB = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 2000L, 1.0f, SystemClock.elapsedRealtime())
                .build();
        controller.applyMetadataAndState(metaB, stateB);
        long genB = controller.getPresentationGeneration();
        assertTrue(genB > genA);
        assertEquals("Track B", controller.getDisplayedTitle());

        // Stale timeline snapshot from Track A arrives with genA
        MediaLib.PlayableItem mockItemA = createMockPlayableItem("Item A", false);
        PlaybackTimelineSnapshot staleTimeline = new PlaybackTimelineSnapshot(
                mockItemA, genA, PlaybackTimelinePolicy.Mode.SEEKABLE,
                50000L, 100000L, true);

        controller.onPlaybackTimelineChanged(staleTimeline);

        // Track B's duration and generation should be preserved, not overwritten by stale callback A
        assertEquals(200000L, controller.getDurationMs());
        assertEquals(genB, controller.getPresentationGeneration());
    }

    @Test
    public void testControlOnlyPresentation() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Web Video Stream")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "YouTube Addon")
                .build();

        ControlOnlyPresentation controlOnly = new ControlOnlyPresentation(
                42L, "me.aap.fermata.addon.web.yt.YoutubeAddon",
                PlaybackStateCompat.STATE_PLAYING,
                PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                metadata);

        controller.applyControlOnly(controlOnly);

        assertNotNull(controller.getControlOnly());
        assertEquals(42L, controller.getControlOnly().leaseId());
        assertEquals("Web Video Stream", controller.getDisplayedTitle());
        assertEquals("YouTube Addon", controller.getDisplayedArtist());
        assertEquals(GlassMediaPlayerController.PresentationState.PLAYING, controller.getPresentationState());
        assertEquals(PlaybackTimelinePolicy.Mode.HIDDEN, controller.getTimelineMode());
        assertFalse(controller.isSeekable());
        assertFalse(controller.isFavorite());
        assertTrue(controller.canSkipNext());
        assertFalse(controller.canSkipPrevious());
    }

    @Test
    public void testBufferingStateDoesNotDropToIdle() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();

        MediaMetadataCompat meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Buffering Stream")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 180000L)
                .build();

        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_BUFFERING, 0L, 1.0f)
                .build();

        PlaybackSnapshot snapshot = new PlaybackSnapshot(
                1L, createMockPlayableItem("Buffering Stream", false), state, meta, true);

        controller.applySnapshot(snapshot);

        assertEquals(GlassMediaPlayerController.PresentationState.BUFFERING, controller.getPresentationState());
        assertEquals("Buffering Stream", controller.getDisplayedTitle());
        assertNotEquals(GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT, controller.getPresentationState());
    }

    @Test
    public void testStrictLiveStreamDetection() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();

        // 1. Duration = 0 on non-livestream -> HIDDEN timeline, PLAYING state (NOT LIVE!)
        MediaLib.PlayableItem normalTrack = createMockPlayableItem("Podcast Intro", false);
        MediaMetadataCompat metaZeroDur = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Podcast Intro")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)
                .build();
        PlaybackStateCompat statePlay = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .build();
        PlaybackSnapshot snapZero = new PlaybackSnapshot(1L, normalTrack, statePlay, metaZeroDur, true);

        controller.applySnapshot(snapZero);
        assertEquals(GlassMediaPlayerController.PresentationState.PLAYING, controller.getPresentationState());
        assertEquals(PlaybackTimelinePolicy.Mode.HIDDEN, controller.getTimelineMode());
        assertFalse(controller.isSeekable());

        // 2. Real livestream -> LIVE state and LIVE timeline
        MediaLib.PlayableItem liveStream = createMockPlayableItem("BBC Radio 1 Live", true);
        MediaMetadataCompat metaLive = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "BBC Radio 1 Live")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)
                .build();
        PlaybackSnapshot snapLive = new PlaybackSnapshot(2L, liveStream, statePlay, metaLive, true);

        controller.applySnapshot(snapLive);
        assertEquals(GlassMediaPlayerController.PresentationState.LIVE, controller.getPresentationState());
        assertEquals(PlaybackTimelinePolicy.Mode.LIVE, controller.getTimelineMode());
        assertFalse(controller.isSeekable());
    }

    @Test
    public void testBinderDeduplication() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        AtomicInteger addListenerCount = new AtomicInteger();
        AtomicInteger removeListenerCount = new AtomicInteger();

        FermataServiceUiBinder binder = new FermataServiceUiBinder(null, null) {
            @Override
            public void addBroadcastListener(Listener l) {
                addListenerCount.incrementAndGet();
                super.addBroadcastListener(l);
            }

            @Override
            public void removeBroadcastListener(Listener l) {
                removeListenerCount.incrementAndGet();
                super.removeBroadcastListener(l);
            }
        };

        // First bind
        controller.bind(binder);
        assertEquals(1, addListenerCount.get());

        // Duplicate bind of the SAME binder must be deduplicated
        controller.bind(binder);
        assertEquals(1, addListenerCount.get());

        // Unbind should detach
        controller.unbind();
        assertEquals(1, removeListenerCount.get());
    }

    @Test
    public void testGlassMediaPlayerViewCardClickRouting() {
        GlassMediaPlayerView view = new GlassMediaPlayerView(RuntimeEnvironment.getApplication());
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        view.setController(controller);

        AtomicBoolean browseMediaCalled = new AtomicBoolean();
        AtomicBoolean openItemCalled = new AtomicBoolean();
        AtomicBoolean openControlOnlyCalled = new AtomicBoolean();

        view.setOnMediaCardClickListener(new GlassMediaPlayerView.OnMediaCardClickListener() {
            @Override
            public void onBrowseMedia() {
                browseMediaCalled.set(true);
            }

            @Override
            public void onResumeRecent() {
            }

            @Override
            public void onOpenPlayingItem(MediaLib.PlayableItem item) {
                openItemCalled.set(true);
            }

            @Override
            public void onOpenControlOnly(ControlOnlyPresentation controlOnly) {
                openControlOnlyCalled.set(true);
            }
        });

        // 1. In IDLE_NO_RECENT, click center layout invokes onBrowseMedia
        controller.resetToIdle();
        view.updateFromController();
        view.findViewById(android.R.id.content); // trigger layout find
        // Simulate clicking center layout
        android.widget.LinearLayout contentLayout = (android.widget.LinearLayout) view.getChildAt(0);
        android.widget.LinearLayout centerLayout = (android.widget.LinearLayout) contentLayout.getChildAt(1);
        centerLayout.performClick();
        assertTrue(browseMediaCalled.get());

        // 2. In ControlOnly session, click center layout invokes onOpenControlOnly
        ControlOnlyPresentation controlOnly = new ControlOnlyPresentation(
                99L, "me.aap.fermata.addon.web.yt.YoutubeAddon",
                PlaybackStateCompat.STATE_PLAYING, 0L, null);
        controller.applyControlOnly(controlOnly);
        view.updateFromController();
        centerLayout.performClick();
        assertTrue(openControlOnlyCalled.get());

        // 3. In Active Engine session, click center layout invokes onOpenPlayingItem
        MediaLib.PlayableItem item = createMockPlayableItem("Active Track", false);
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 1000L, 1.0f).build();
        controller.applySnapshot(new PlaybackSnapshot(10L, item, state, null, true));
        view.updateFromController();
        centerLayout.performClick();
        assertTrue(openItemCalled.get());
    }

    @Test
    public void testSynchronousArtworkResolutionNotOverwritten() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        Bitmap testArt = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        MediaMetadataCompat mediaData = new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, testArt)
                .build();
        @SuppressWarnings("unchecked")
        FutureSupplier<MediaMetadataCompat> syncSupplier = (FutureSupplier<MediaMetadataCompat>) Proxy.newProxyInstance(
                FutureSupplier.class.getClassLoader(),
                new Class<?>[]{FutureSupplier.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "main" -> proxy;
                    case "onSuccess" -> {
                        ((ProgressiveResultConsumer.Success<MediaMetadataCompat>) args[0]).accept(mediaData);
                        yield proxy;
                    }
                    default -> null;
                });
        MediaLib.PlayableItem item = createMockPlayableItem("Sync Art Track", false, true, syncSupplier);
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO)
                .build();

        // Snapshot has null metadata art
        PlaybackSnapshot snapshot = new PlaybackSnapshot(1L, item, state, null, true);
        controller.applySnapshot(snapshot);

        // Synchronous item.getMediaData().main() must NOT be wiped by baseline assignment
        assertSame("Preloaded synchronous artwork must be preserved", testArt, controller.getAlbumArt());
    }

    @Test
    public void testAsynchronousArtworkResolutionUpdatesAlbumArt() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        Bitmap testArt = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        MediaMetadataCompat mediaData = new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, testArt)
                .build();
        AtomicReference<ProgressiveResultConsumer.Success<? super MediaMetadataCompat>> callbackRef = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        FutureSupplier<MediaMetadataCompat> asyncSupplier = (FutureSupplier<MediaMetadataCompat>) Proxy.newProxyInstance(
                FutureSupplier.class.getClassLoader(),
                new Class<?>[]{FutureSupplier.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "main" -> proxy;
                    case "onSuccess" -> {
                        callbackRef.set((ProgressiveResultConsumer.Success<? super MediaMetadataCompat>) args[0]);
                        yield proxy;
                    }
                    default -> null;
                });

        MediaLib.PlayableItem item = createMockPlayableItem("Async Art Track", false, true, asyncSupplier);
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO)
                .build();

        PlaybackSnapshot snapshot = new PlaybackSnapshot(1L, item, state, null, true);
        controller.applySnapshot(snapshot);

        assertNull("Album art should initially be null before async resolution", controller.getAlbumArt());
        assertNotNull("Async callback must be registered", callbackRef.get());

        // Now trigger the callback asynchronously
        callbackRef.get().accept(mediaData);

        assertSame("Album art must update when delayed future completes", testArt, controller.getAlbumArt());
    }

    @Test
    public void testUnseekableAndUnskippableSources() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        // Item is marked unseekable
        MediaLib.PlayableItem item = createMockPlayableItem("Radio Stream", false, false, null);
        // State has no skip actions and no seek action
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 1000L, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .build();
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 120_000L)
                .build();

        PlaybackSnapshot snapshot = new PlaybackSnapshot(1L, item, state, metadata, true);
        controller.applySnapshot(snapshot);

        assertFalse("Unskippable source must have canSkipNext == false", controller.canSkipNext());
        assertFalse("Unskippable source must have canSkipPrevious == false", controller.canSkipPrevious());
        assertEquals("Unseekable source must have timelineMode == HIDDEN",
                PlaybackTimelinePolicy.Mode.HIDDEN, controller.getTimelineMode());
        assertFalse(controller.isSeekable());
    }

    @Test
    public void testSeekableAndSkippableSources() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        // Item is seekable
        MediaLib.PlayableItem item = createMockPlayableItem("Standard Track", false, true, null);
        // State has skip next, skip previous, and seek
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 1000L, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .build();
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 120_000L)
                .build();

        PlaybackSnapshot snapshot = new PlaybackSnapshot(1L, item, state, metadata, true);
        controller.applySnapshot(snapshot);

        assertTrue("Skippable source must have canSkipNext == true", controller.canSkipNext());
        assertTrue("Skippable source must have canSkipPrevious == true", controller.canSkipPrevious());
        assertEquals("Seekable source with duration must have timelineMode == SEEKABLE",
                PlaybackTimelinePolicy.Mode.SEEKABLE, controller.getTimelineMode());
        assertTrue(controller.isSeekable());
    }

    private static MediaLib.PlayableItem createMockPlayableItem(String name, boolean isLiveStream) {
        return createMockPlayableItem(name, isLiveStream, !isLiveStream, null);
    }

    private static MediaLib.PlayableItem createMockPlayableItem(
            String name, boolean isLiveStream, boolean isSeekable,
            FutureSupplier<MediaMetadataCompat> mediaData) {
        return (MediaLib.PlayableItem) Proxy.newProxyInstance(
                MediaLib.PlayableItem.class.getClassLoader(),
                new Class<?>[]{MediaLib.PlayableItem.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "getName" -> name;
                    case "getDisplayTitle" -> name;
                    case "isLiveStream" -> isLiveStream;
                    case "isSeekable" -> isSeekable;
                    case "isExternal" -> false;
                    case "isFavoriteItem" -> false;
                    case "getMediaData" -> mediaData;
                    default -> null;
                });
    }
}
