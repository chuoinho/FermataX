package me.aap.fermata.addon.web.yt;

import java.util.ArrayList;
import java.util.List;

/** Small deterministic fixture shared by the controller tests; it has no Android or network dependency. */
final class YoutubeAdControllerFixture {
	final YoutubeAdController controller;
	final List<YoutubeAdController.Effect> effects = new ArrayList<>();
	long generation;

	YoutubeAdControllerFixture() {
		controller = new YoutubeAdController(new YoutubeAdController.RetryPolicy(2, 100L));
	}

	void begin(String playbackId) {
		YoutubeAdController.Transition transition = controller.beginPlayback(playbackId);
		generation = transition.generation();
		effects.addAll(transition.effects());
	}

	void record(YoutubeAdController.Transition transition) {
		effects.addAll(transition.effects());
	}

	long count(YoutubeAdController.EffectType type) {
		return effects.stream().filter(effect -> effect.type() == type).count();
	}
}
