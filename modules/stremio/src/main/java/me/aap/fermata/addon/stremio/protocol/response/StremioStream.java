package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;
import java.util.List;

public record StremioStream(
		String name,
		String title,
		String description,
		StreamTarget target,
		List<StremioSubtitle> subtitles,
		StreamBehaviorHints behaviorHints) {
	public StremioStream(String name, String title, String description,
			StreamTarget target, StreamBehaviorHints behaviorHints) {
		this(name, title, description, target, List.of(), behaviorHints);
	}

	public StremioStream {
		Objects.requireNonNull(target, "target");
		subtitles = List.copyOf(Objects.requireNonNull(subtitles, "subtitles"));
		Objects.requireNonNull(behaviorHints, "behaviorHints");
	}

	@Override
	public String toString() {
		return "StremioStream[name=<redacted>, title=<redacted>, description=<redacted>, target=" +
				target.getClass().getSimpleName() + ", behaviorHints=<redacted>]";
	}
}
