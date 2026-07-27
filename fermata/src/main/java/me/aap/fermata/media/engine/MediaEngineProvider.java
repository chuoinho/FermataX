package me.aap.fermata.media.engine;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.Collections;
import java.util.Set;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;
import me.aap.fermata.media.net.RemotePlaybackItem;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngineProvider {

	void init(Context ctx);

	MediaEngine createEngine(Listener listener);

	default Set<EngineCapability> getPlaybackCapabilities() {
		return Collections.emptySet();
	}

	default boolean supportsPlayback(PlayableItem item) {
		return !(item instanceof RemotePlaybackItem remote) || getPlaybackCapabilities()
				.containsAll(remote.getPlaybackRequestProfile().getRequiredEngineCapabilities());
	}

	default boolean getMediaMetadata(MetadataBuilder meta, PlayableItem item) {
		return false;
	}

	default boolean isValidBitmap(Bitmap bm) {
		if (bm == null) return false;

		int prev = 0;

		for (int x = 0, w = bm.getWidth(), h = bm.getHeight(); x < w; x++) {
			for (int y = 0; y < h; y++) {
				int px = bm.getPixel(x, y);
				if ((px != prev) && (x != 0) && (y != 0)) return true;
				prev = px;
			}
		}

		return false;
	}
}
