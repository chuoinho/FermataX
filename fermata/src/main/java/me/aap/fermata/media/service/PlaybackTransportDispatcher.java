package me.aap.fermata.media.service;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

final class PlaybackTransportDispatcher {
	private PlaybackTransportDispatcher() {
	}

	static boolean dispatch(PlayableItem command, MediaEngine owner, MediaEngine current) {
		if (!command.isPlaybackTransportCommand()) return false;
		if (owner == current) owner.prepare(command);
		return true;
	}
}
