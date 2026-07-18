package me.aap.fermata.media.engine;

import static org.junit.Assert.assertNull;

import android.content.Context;

import org.junit.Test;

public class MediaEngineManagerTest {
	@Test
	public void providerFailureDoesNotEscapeEngineFactory() {
		MediaEngineProvider failing = new MediaEngineProvider() {
			@Override
			public void init(Context context) {
			}

			@Override
			public MediaEngine createEngine(MediaEngine.Listener listener) {
				throw new UnsupportedOperationException("provider failed");
			}
		};

		assertNull(MediaEngineManager.createSafely(failing, null, false));
		assertNull(MediaEngineManager.createSafely(failing, null, true));
	}
}
