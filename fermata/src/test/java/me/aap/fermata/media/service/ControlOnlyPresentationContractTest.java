package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.support.v4.media.MediaMetadataCompat;

import org.junit.Test;

public class ControlOnlyPresentationContractTest {
	@Test
	public void descriptorRetainsLeaseIdentityAndMediaSnapshot() {
		MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Clip").build();
		ControlOnlyPresentation presentation = new ControlOnlyPresentation(7L,
				"example.Addon", STATE_PLAYING, ACTION_PAUSE,
				metadata);

		assertEquals(7L, presentation.leaseId());
		assertEquals("example.Addon", presentation.addonClass());
		assertEquals(STATE_PLAYING, presentation.state());
		assertEquals(ACTION_PAUSE, presentation.actions());
		assertEquals(metadata, presentation.metadata());
	}

	@Test
	public void descriptorRejectsMissingLeaseOrAddon() {
		assertThrows(IllegalArgumentException.class,
				() -> new ControlOnlyPresentation(0L, "addon", STATE_PLAYING, 0L, null));
		assertThrows(NullPointerException.class,
				() -> new ControlOnlyPresentation(1L, null, STATE_PLAYING, 0L, null));
		assertThrows(IllegalArgumentException.class,
				() -> new ControlOnlyPresentation(1L, " ", STATE_PLAYING, 0L, null));
	}
}
