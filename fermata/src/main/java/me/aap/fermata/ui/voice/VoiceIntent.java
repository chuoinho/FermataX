package me.aap.fermata.ui.voice;

import androidx.annotation.Nullable;

/** Deterministic, UI-independent result of parsing one voice utterance. */
public final class VoiceIntent {
	public enum Kind { PLAYBACK, ADDON_SEARCH, SELECTION }

	public enum PlaybackAction {
		PLAY, PAUSE, STOP, OPEN_CURRENT, PLAY_FAVORITES
	}

	public enum SearchAction { PLAY, FIND, OPEN }

	private final Kind kind;
	private final PlaybackAction playbackAction;
	private final SearchAction searchAction;
	private final String addon;
	private final String query;
	private final int selectionIndex;

	private VoiceIntent(Kind kind, PlaybackAction playbackAction, SearchAction searchAction,
													 String addon, String query, int selectionIndex) {
		this.kind = kind;
		this.playbackAction = playbackAction;
		this.searchAction = searchAction;
		this.addon = addon;
		this.query = query;
		this.selectionIndex = selectionIndex;
	}

	public static VoiceIntent playback(PlaybackAction action) {
		return new VoiceIntent(Kind.PLAYBACK, action, null, null, null, -1);
	}

	public static VoiceIntent search(SearchAction action, @Nullable String addon, String query) {
		return new VoiceIntent(Kind.ADDON_SEARCH, null, action, addon, query, -1);
	}

	public static VoiceIntent selection(int index) {
		return new VoiceIntent(Kind.SELECTION, null, null, null, null, index);
	}

	public Kind getKind() {
		return kind;
	}

	@Nullable
	public PlaybackAction getPlaybackAction() {
		return playbackAction;
	}

	@Nullable
	public SearchAction getSearchAction() {
		return searchAction;
	}

	@Nullable
	public String getAddon() {
		return addon;
	}

	@Nullable
	public String getQuery() {
		return query;
	}

	public int getSelectionIndex() {
		return selectionIndex;
	}
}
