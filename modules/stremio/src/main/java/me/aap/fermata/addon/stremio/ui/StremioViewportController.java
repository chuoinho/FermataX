package me.aap.fermata.addon.stremio.ui;

import android.content.res.Resources;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.addon.stremio.presentation.StremioPresenter;
import me.aap.fermata.addon.stremio.presentation.StremioScreenState;
import me.aap.fermata.addon.stremio.ui.presentation.StremioPresentationAdapter;

/** Maintains presentation view references only between onViewCreated and onDestroyView. */
public final class StremioViewportController {
	private RecyclerView list;
	private GridLayoutManager layout;
	private StremioPresentationAdapter adapter;

	public void attach(RecyclerView list, GridLayoutManager layout,
			StremioPresentationAdapter adapter) {
		this.list = list;
		this.layout = layout;
		this.adapter = adapter;
	}

	public void clearView() {
		adapter = null;
		layout = null;
		list = null;
	}

	public RecyclerView list() {
		return list;
	}

	public GridLayoutManager layout() {
		return layout;
	}

	public StremioPresentationAdapter adapter() {
		return adapter;
	}

	public boolean canScrollUp() {
		return (list != null) && list.canScrollVertically(-1);
	}

	public void scrollToTop() {
		if (list != null) list.scrollToPosition(0);
	}

	public void save(StremioPresenter presenter) {
		if ((presenter == null) || (layout == null) || (adapter == null) || (list == null)) return;
		presenter.saveViewport(adapter.captureViewport(list, layout));
	}

	public void restore(StremioScreenState renderedState,
			java.util.function.Supplier<StremioScreenState> currentState) {
		GridLayoutManager expectedLayout = layout;
		RecyclerView expectedList = list;
		if ((expectedLayout == null) || (expectedList == null)) return;
		expectedList.post(() -> {
			StremioScreenState current = currentState.get();
			if ((layout == expectedLayout) && (adapter != null) && (current != null) &&
					(current.generation() == renderedState.generation()) &&
					current.route().equals(renderedState.route())) {
				adapter.restoreViewport(expectedList, expectedLayout, renderedState.viewport());
			}
		});
	}

	public void updateColumns(Resources resources) {
		GridLayoutManager currentLayout = layout;
		RecyclerView currentList = list;
		if ((currentLayout == null) || (currentList == null)) return;
		int columns = posterColumns(currentList, resources);
		if (currentLayout.getSpanCount() != columns) currentLayout.setSpanCount(columns);
	}

	public static int posterColumns(RecyclerView list, Resources resources) {
		float density = resources.getDisplayMetrics().density;
		int width = list.getWidth();
		int widthDp = (width > 0) ? Math.round(width / density) :
				resources.getConfiguration().screenWidthDp;
		return StremioLayoutPolicy.posterColumns(widthDp);
	}
}
