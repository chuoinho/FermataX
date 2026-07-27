package me.aap.fermata.addon.stremio.ui.presentation;

import me.aap.fermata.addon.stremio.presentation.StremioUiModel;

/** Pure mapping shared by the adapter and presentation contract tests. */
public final class StremioPresentationContract {
	public static final int ACTION = 1;
	public static final int SECTION = 2;
	public static final int POSTER = 3;
	public static final int FILTER = 4;
	public static final int DETAILS_HEADER = 5;
	public static final int EPISODE = 6;
	public static final int STREAM_GROUP = 7;
	public static final int STREAM_CHOICE = 8;
	public static final int STATE_ROW = 9;
	public static final int ACTION_BAR = 10;

	private StremioPresentationContract() {
	}

	public static int viewType(StremioUiModel model) {
		if (model instanceof StremioUiModel.Action) return ACTION;
		if (model instanceof StremioUiModel.ActionBar) return ACTION_BAR;
		if (model instanceof StremioUiModel.Section) return SECTION;
		if (model instanceof StremioUiModel.Poster) return POSTER;
		if (model instanceof StremioUiModel.Filter) return FILTER;
		if (model instanceof StremioUiModel.DetailsHeader) return DETAILS_HEADER;
		if (model instanceof StremioUiModel.Episode) return EPISODE;
		if (model instanceof StremioUiModel.StreamGroup) return STREAM_GROUP;
		if (model instanceof StremioUiModel.StreamChoice) return STREAM_CHOICE;
		if (model instanceof StremioUiModel.StateRow) return STATE_ROW;
		throw new IllegalArgumentException("Unknown Stremio UI model " + model.getClass());
	}

	/** Posters occupy one grid cell; every other model is a full-width row. */
	public static int spanSize(StremioUiModel model, int spanCount) {
		if (spanCount < 1) throw new IllegalArgumentException("spanCount must be positive");
		return (model instanceof StremioUiModel.Poster) ? 1 : spanCount;
	}

	/** Stable, deterministic 64-bit FNV-1a hash. RecyclerView IDs must not depend on hashCode(). */
	public static long stableId(StremioUiModel model) {
		long hash = 0xcbf29ce484222325L;
		String value = viewType(model) + ":" + model.stableKey();
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash;
	}
}
