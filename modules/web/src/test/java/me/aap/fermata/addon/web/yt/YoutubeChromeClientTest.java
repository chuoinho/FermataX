package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeFullscreenGate.NO_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.webkit.WebChromeClient.CustomViewCallback;

import org.junit.Test;

public class YoutubeChromeClientTest {
	@Test
	public void untaggedCustomViewCallbackCannotBypassFullscreenGate() {
		long[] request = {Long.MIN_VALUE};
		YoutubeChromeClient chrome = new YoutubeChromeClient(null, null, value -> {
			request[0] = value;
			return false;
		});
		TrackingCallback callback = new TrackingCallback();

		chrome.onShowCustomView(null, callback);

		assertEquals(NO_REQUEST, request[0]);
		assertTrue(callback.hidden);
		assertFalse(chrome.isFullScreen());
	}

	private static final class TrackingCallback implements CustomViewCallback {
		private boolean hidden;

		@Override
		public void onCustomViewHidden() {
			hidden = true;
		}
	}
}
