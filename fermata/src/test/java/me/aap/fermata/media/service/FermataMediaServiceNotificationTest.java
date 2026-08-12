package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_FAST_FORWARDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_REWINDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static me.aap.fermata.media.service.FermataMediaService.requiresForegroundPlayback;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FermataMediaServiceNotificationTest {
	@Test
	public void activePlaybackStatesRequireForegroundBeforeAudioFocus() {
		for (int state : new int[]{STATE_CONNECTING, STATE_BUFFERING, STATE_PLAYING,
				STATE_FAST_FORWARDING, STATE_REWINDING, STATE_SKIPPING_TO_NEXT,
				STATE_SKIPPING_TO_PREVIOUS, STATE_SKIPPING_TO_QUEUE_ITEM}) {
			assertTrue("state=" + state, requiresForegroundPlayback(state));
		}
	}

	@Test
	public void inactivePlaybackStatesDoNotKeepForeground() {
		for (int state : new int[]{STATE_NONE, STATE_STOPPED, STATE_ERROR, STATE_PAUSED}) {
			assertFalse("state=" + state, requiresForegroundPlayback(state));
		}
	}
}
