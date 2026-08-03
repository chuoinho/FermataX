package me.aap.fermata.addon.stremio.ui.presentation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.presentation.StremioUiModel;
import me.aap.fermata.addon.stremio.presentation.StremioViewportState;
import me.aap.fermata.addon.stremio.ui.StremioLayoutPolicy;

/** Native, transport-free renderer for immutable {@link StremioUiModel} pages. */
public final class StremioPresentationAdapter
		extends RecyclerView.Adapter<StremioPresentationViewHolder> {
	public enum DetailsAction { WATCH_OR_RESUME, FAVORITE, SUBTITLES }

	public interface Listener {
		void onModelSelected(StremioUiModel model);

		default void onFilterOptionSelected(StremioUiModel.Filter filter,
				StremioUiModel.Option option) {
		}

		default void onDetailsAction(StremioUiModel.DetailsHeader details,
				DetailsAction action) {
		}

		default void onPosterProgressDismissRequested(StremioUiModel.Poster poster) {
		}
	}

	private final List<StremioUiModel> models = new ArrayList<>();
	private final StremioPresentationBindings bindings;

	public StremioPresentationAdapter(@NonNull Listener listener) {
		this(listener, new StremioArtworkBinder());
	}

	public StremioPresentationAdapter(@NonNull Listener listener,
			@NonNull StremioArtworkBinder artwork) {
		bindings = new StremioPresentationBindings(listener, artwork);
		setHasStableIds(true);
	}

	public void submitModels(@NonNull List<? extends StremioUiModel> replacement) {
		List<StremioUiModel> next = List.copyOf(replacement);
		List<StremioUiModel> previous = List.copyOf(models);
		DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
			@Override public int getOldListSize() { return previous.size(); }
			@Override public int getNewListSize() { return next.size(); }

			@Override
			public boolean areItemsTheSame(int oldPosition, int newPosition) {
				StremioUiModel old = previous.get(oldPosition);
				StremioUiModel fresh = next.get(newPosition);
				return (StremioPresentationContract.viewType(old) ==
						StremioPresentationContract.viewType(fresh)) &&
						old.stableKey().equals(fresh.stableKey());
			}

			@Override
			public boolean areContentsTheSame(int oldPosition, int newPosition) {
				return previous.get(oldPosition).equals(next.get(newPosition));
			}
		}, false);
		models.clear();
		models.addAll(next);
		diff.dispatchUpdatesTo(this);
	}

	@NonNull
	public List<StremioUiModel> getModels() {
		return List.copyOf(models);
	}

	public int getSpanSize(int position, int spanCount) {
		return StremioPresentationContract.spanSize(models.get(position), spanCount);
	}

	public StremioViewportState captureViewport(@NonNull RecyclerView list,
			@NonNull LinearLayoutManager layout) {
		bindings.captureVisibleHorizontalPositions(list);
		String focusedKey = itemKey(list.findFocus());
		int position = Math.max(layout.findFirstVisibleItemPosition(), 0);
		View first = layout.findViewByPosition(position);
		int offset = (first == null) ? 0 : first.getTop() - list.getPaddingTop();
		return new StremioViewportState((focusedKey == null) ? "" : focusedKey,
				position, offset, bindings.horizontalPositions());
	}

	public void restoreViewport(@NonNull RecyclerView list,
			@NonNull LinearLayoutManager layout, @NonNull StremioViewportState viewport) {
		bindings.restoreHorizontalPositions(viewport.horizontalPositions());
		layout.scrollToPositionWithOffset(viewport.verticalPosition(), viewport.verticalOffset());
		list.post(() -> {
			bindings.restoreVisibleHorizontalPositions(list);
			String key = viewport.focusedKey();
			if (!key.isEmpty()) list.post(() -> requestFocusByKey(list, key));
		});
	}

	@Override
	public long getItemId(int position) {
		return StremioPresentationContract.stableId(models.get(position));
	}

	@Override
	public int getItemViewType(int position) {
		return StremioPresentationContract.viewType(models.get(position));
	}

	@Override
	@NonNull
	public StremioPresentationViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		return switch (viewType) {
			case StremioPresentationContract.ACTION -> new StremioActionHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_action), bindings);
			case StremioPresentationContract.ACTION_BAR -> new StremioActionBarHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_action_bar), bindings);
			case StremioPresentationContract.SECTION -> new StremioSectionHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_section), bindings);
			case StremioPresentationContract.POSTER -> new StremioPosterHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_poster), bindings, 0);
			case StremioPresentationContract.FILTER -> new StremioFilterHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_filter), bindings);
			case StremioPresentationContract.DETAILS_HEADER -> new StremioDetailsHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_details_header), bindings);
			case StremioPresentationContract.EPISODE -> new StremioEpisodeHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_episode), bindings);
			case StremioPresentationContract.STREAM_GROUP -> new StremioStreamGroupHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_stream_group), bindings);
			case StremioPresentationContract.STREAM_CHOICE -> new StremioStreamChoiceHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_stream_choice), bindings);
			case StremioPresentationContract.STATE_ROW -> new StremioStateHolder(
					inflate(inflater, parent, R.layout.stremio_presentation_state_row), bindings);
			default -> throw new IllegalArgumentException("Unknown Stremio view type " + viewType);
		};
	}

	@Override
	public void onBindViewHolder(@NonNull StremioPresentationViewHolder holder, int position) {
		holder.bind(models.get(position));
	}

	@Override
	public void onViewRecycled(@NonNull StremioPresentationViewHolder holder) {
		holder.recycle();
	}

	@Override
	public int getItemCount() {
		return models.size();
	}

	private static View inflate(LayoutInflater inflater, ViewGroup parent, int layout) {
		return inflater.inflate(layout, parent, false);
	}

	@Nullable
	private static String itemKey(@Nullable View view) {
		View current = view;
		while (current != null) {
			Object key = current.getTag(R.id.stremio_presentation_item_key);
			if (key instanceof String value) return value;
			if (!(current.getParent() instanceof View parent)) break;
			current = parent;
		}
		return null;
	}

	private static boolean requestFocusByKey(View view, String key) {
		if (key.equals(view.getTag(R.id.stremio_presentation_item_key)) && view.requestFocus()) {
			return true;
		}
		if (!(view instanceof ViewGroup group)) return false;
		for (int i = 0; i < group.getChildCount(); i++) {
			if (requestFocusByKey(group.getChildAt(i), key)) return true;
		}
		return false;
	}

	static void setOptionalText(TextView view, String value) {
		view.setText(value);
		view.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
	}

	static void bindProgress(LinearProgressIndicator view, float progress) {
		if (progress <= 0f) {
			view.setVisibility(View.GONE);
			view.setProgress(0);
		} else {
			view.setVisibility(View.VISIBLE);
			view.setProgress(Math.round(progress * 1000f));
		}
	}

	static int shelfPosterWidth(RecyclerView shelf) {
		float density = shelf.getResources().getDisplayMetrics().density;
		int availablePx = shelf.getWidth() - shelf.getPaddingStart() - shelf.getPaddingEnd();
		int availableDp = (availablePx > 0) ? Math.round(availablePx / density) :
				shelf.getResources().getConfiguration().screenWidthDp;
		return Math.round(StremioLayoutPolicy.shelfPosterWidthDp(availableDp) * density);
	}

	static String join(String... values) {
		StringBuilder result = new StringBuilder();
		for (String value : values) {
			if ((value == null) || value.isEmpty()) continue;
			if (result.length() != 0) result.append(", ");
			result.append(value);
		}
		return result.toString();
	}

	static long stableOptionId(String key) {
		long hash = 1125899906842597L;
		for (int i = 0; i < key.length(); i++) hash = (31L * hash) + key.charAt(i);
		return hash;
	}

	static int actionIcon(StremioUiModel.ActionKind kind) {
		return switch (kind) {
			case SEARCH -> me.aap.fermata.R.drawable.search;
			case DISCOVER -> me.aap.fermata.R.drawable.video;
			case LIBRARY -> me.aap.fermata.R.drawable.playlist;
			case ADDONS -> me.aap.fermata.R.drawable.settings;
			case WATCH -> me.aap.fermata.R.drawable.play;
			case FAVORITE -> me.aap.fermata.R.drawable.favorite;
			case SUBTITLES -> me.aap.fermata.R.drawable.subtitles;
			case RETRY -> me.aap.fermata.R.drawable.refresh;
			case NEXT_PAGE -> me.aap.fermata.R.drawable.next;
		};
	}

	static int stateIcon(StremioUiModel.StateKind kind) {
		return switch (kind) {
			case LOADING -> me.aap.fermata.R.drawable.loading;
			case EMPTY -> me.aap.fermata.R.drawable.video;
			case WARNING, ERROR -> me.aap.fermata.R.drawable.notification;
		};
	}

	static int providerStateLabel(StremioUiModel.ProviderState state) {
		return switch (state) {
			case READY -> R.string.stremio_presentation_provider_ready;
			case LOADING -> R.string.stremio_presentation_provider_loading;
			case FAILED -> R.string.stremio_presentation_provider_failed;
			case TIMED_OUT -> R.string.stremio_presentation_provider_timed_out;
		};
	}
}
