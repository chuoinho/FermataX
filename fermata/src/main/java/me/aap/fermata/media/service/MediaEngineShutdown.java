package me.aap.fermata.media.service;

import android.media.AudioManager;

import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.log.Log;

/** Exception-isolated terminal operations for a media engine. */
final class MediaEngineShutdown {
	private MediaEngineShutdown() {
	}

	@Nullable
	static PlayableItem source(@Nullable MediaEngine engine) {
		if (engine == null) return null;
		try {
			return engine.getSource();
		} catch (Throwable error) {
			Log.d(error, "Failed to read stopping engine source");
			return null;
		}
	}

	static void release(@Nullable MediaEngine engine, @Nullable AudioManager audioManager,
			AudioFocusRequestCompat audioFocusRequest) {
		if (engine == null) return;
		run("engine_stop", engine::stop);
		run("audio_focus", () -> engine.releaseAudioFocus(audioManager, audioFocusRequest));
		run("engine_close", engine::close);
	}

	static void run(String name, Runnable action) {
		try {
			action.run();
		} catch (Throwable error) {
			Log.e(error, "Playback cleanup failed: ", name);
		}
	}
}
