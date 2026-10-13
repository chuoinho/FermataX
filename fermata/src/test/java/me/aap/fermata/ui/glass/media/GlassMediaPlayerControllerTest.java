package me.aap.fermata.ui.glass.media;

import static org.junit.Assert.*;

import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

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
        me.aap.fermata.media.lib.MediaLib.PlayableItem mockItem =
                (me.aap.fermata.media.lib.MediaLib.PlayableItem) java.lang.reflect.Proxy.newProxyInstance(
                        me.aap.fermata.media.lib.MediaLib.PlayableItem.class.getClassLoader(),
                        new Class<?>[]{me.aap.fermata.media.lib.MediaLib.PlayableItem.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "Test Track";
                            case "getName" -> "Test Track";
                            default -> null;
                        });
        me.aap.fermata.media.service.PlaybackTimelineSnapshot timeline =
                new me.aap.fermata.media.service.PlaybackTimelineSnapshot(
                        mockItem, 1L, me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE,
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
        me.aap.fermata.media.lib.MediaLib.PlayableItem mockItemA =
                (me.aap.fermata.media.lib.MediaLib.PlayableItem) java.lang.reflect.Proxy.newProxyInstance(
                        me.aap.fermata.media.lib.MediaLib.PlayableItem.class.getClassLoader(),
                        new Class<?>[]{me.aap.fermata.media.lib.MediaLib.PlayableItem.class},
                        (proxy, method, args) -> "Item A");
        me.aap.fermata.media.service.PlaybackTimelineSnapshot staleTimeline =
                new me.aap.fermata.media.service.PlaybackTimelineSnapshot(
                        mockItemA, genA, me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE,
                        50000L, 100000L, true);

        controller.onPlaybackTimelineChanged(staleTimeline);

        // Track B's duration and generation should be preserved, not overwritten by stale callback A
        assertEquals(200000L, controller.getDurationMs());
        assertEquals(genB, controller.getPresentationGeneration());
    }

    @Test
    public void testLiveStreamPresentationState() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        MediaMetadataCompat metaLive = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "BBC Radio 1")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)
                .build();
        PlaybackStateCompat stateLive = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f, SystemClock.elapsedRealtime())
                .build();
        controller.applyMetadataAndState(metaLive, stateLive);

        assertEquals(GlassMediaPlayerController.PresentationState.LIVE, controller.getPresentationState());
        assertFalse(controller.isSeekable());
        assertEquals(0L, controller.getDurationMs());
    }
}
