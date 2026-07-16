package me.aap.fermata.ui.voice;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lifecycle state for one push-to-talk interaction. */
public final class VoiceSession {
	public enum Mode { COMMAND, TEXT_INPUT, SELECTION }

	public static final long SELECTION_TIMEOUT_MS = 15_000L;

	public static final class Option {
		private final String stableId;
		private final String title;
		private final String subtitle;
		private final String voiceTarget;

		public Option(String stableId, String title, @Nullable String subtitle) {
			this(stableId, title, subtitle, null);
		}

		public Option(String stableId, String title, @Nullable String subtitle,
									 @Nullable String voiceTarget) {
			this.stableId = stableId;
			this.title = title;
			this.subtitle = subtitle;
			this.voiceTarget = voiceTarget;
		}

		public String getStableId() {
			return stableId;
		}

		public String getTitle() {
			return title;
		}

		@Nullable
		public String getSubtitle() {
			return subtitle;
		}

		@Nullable
		public String getVoiceTarget() {
			return voiceTarget;
		}
	}

	private Mode mode = Mode.COMMAND;
	private List<Option> options = Collections.emptyList();
	private long selectionExpiresAt;

	public Mode getMode() {
		return mode;
	}

	public void beginCommand() {
		clear(Mode.COMMAND);
	}

	public void beginTextInput() {
		clear(Mode.TEXT_INPUT);
	}

	public void beginSelection(List<Option> candidates, long now) {
		if ((candidates == null) || candidates.isEmpty()) {
			clear(Mode.COMMAND);
			return;
		}
		int size = Math.min(3, candidates.size());
		options = Collections.unmodifiableList(new ArrayList<>(candidates.subList(0, size)));
		mode = Mode.SELECTION;
		selectionExpiresAt = now + SELECTION_TIMEOUT_MS;
	}

	public boolean isSelectionActive(long now) {
		return (mode == Mode.SELECTION) && (now < selectionExpiresAt) && !options.isEmpty();
	}

	@Nullable
	public Option resolveSelection(@Nullable String phrase, Locale locale, long now) {
		if (!isSelectionActive(now)) {
			clear(Mode.COMMAND);
			return null;
		}
		VoiceIntent intent = VoiceIntentParser.parse(phrase, locale);
		if ((intent == null) || (intent.getKind() != VoiceIntent.Kind.SELECTION)) return null;
		int index = intent.getSelectionIndex();
		if ((index < 0) || (index >= options.size())) return null;
		Option option = options.get(index);
		clear(Mode.COMMAND);
		return option;
	}

	public void clear() {
		clear(Mode.COMMAND);
	}

	public List<Option> getOptions() {
		return options;
	}

	private void clear(Mode next) {
		mode = next;
		options = Collections.emptyList();
		selectionExpiresAt = 0;
	}
}
