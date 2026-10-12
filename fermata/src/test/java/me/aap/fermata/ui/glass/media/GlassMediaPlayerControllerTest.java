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
}
