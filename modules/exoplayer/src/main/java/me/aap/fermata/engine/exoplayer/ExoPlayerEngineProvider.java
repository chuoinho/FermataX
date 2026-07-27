package me.aap.fermata.engine.exoplayer;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class ExoPlayerEngineProvider implements MediaEngineProvider {
	private static final Set<EngineCapability> PLAYBACK_CAPABILITIES =
			Collections.unmodifiableSet(EnumSet.allOf(EngineCapability.class));

	static Set<EngineCapability> playbackCapabilities() {
		return PLAYBACK_CAPABILITIES;
	}
	private Context ctx;

	@Override
	public void init(Context ctx) {
		this.ctx = ctx;
	}

	@OptIn(markerClass = UnstableApi.class)
	@Override
	public MediaEngine createEngine(Listener listener) {
		return new ExoPlayerEngine(ctx, listener);
	}

	@Override
	public Set<EngineCapability> getPlaybackCapabilities() {
		return PLAYBACK_CAPABILITIES;
	}
}
