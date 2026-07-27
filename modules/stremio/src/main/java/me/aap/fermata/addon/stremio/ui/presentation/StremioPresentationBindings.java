package me.aap.fermata.addon.stremio.ui.presentation;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedHashMap;
import java.util.Map;

final class StremioPresentationBindings {
	final StremioPresentationAdapter.Listener listener;
	final StremioArtworkBinder artwork;
	final RecyclerView.RecycledViewPool posterPool = new RecyclerView.RecycledViewPool();
	private final Map<String, Integer> positions = new LinkedHashMap<>();

	StremioPresentationBindings(StremioPresentationAdapter.Listener listener,
			StremioArtworkBinder artwork) {
		this.listener = listener;
		this.artwork = artwork;
	}

	void save(@Nullable String key, RecyclerView view) {
		if (key == null) return;
		if (view.getLayoutManager() instanceof LinearLayoutManager layout) {
			positions.put(key, Math.max(layout.findFirstVisibleItemPosition(), 0));
		}
	}

	void restore(@Nullable String key, RecyclerView view) {
		Integer position = (key == null) ? null : positions.get(key);
		view.scrollToPosition((position == null) ? 0 : position);
	}

	void captureVisibleHorizontalPositions(RecyclerView list) {
		for (int i = 0; i < list.getChildCount(); i++) {
			RecyclerView.ViewHolder holder = list.getChildViewHolder(list.getChildAt(i));
			if (holder instanceof StremioHorizontalPositionHolder horizontal) horizontal.savePosition();
		}
	}

	void restoreVisibleHorizontalPositions(RecyclerView list) {
		for (int i = 0; i < list.getChildCount(); i++) {
			RecyclerView.ViewHolder holder = list.getChildViewHolder(list.getChildAt(i));
			if (holder instanceof StremioHorizontalPositionHolder horizontal) {
				horizontal.restorePosition();
			}
		}
	}

	Map<String, Integer> horizontalPositions() {
		return Map.copyOf(positions);
	}

	void restoreHorizontalPositions(Map<String, Integer> replacement) {
		positions.clear();
		positions.putAll(replacement);
	}
}
