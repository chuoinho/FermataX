package me.aap.fermata.addon.stremio.ui.presentation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.presentation.StremioUiModel;
import me.aap.fermata.addon.stremio.ui.StremioLayoutPolicy;

interface StremioHorizontalPositionHolder {
	void savePosition();
	void restorePosition();
}

abstract class StremioModelHolder<T extends StremioUiModel>
		extends StremioPresentationViewHolder {
	final StremioPresentationBindings bindings;
	StremioModelHolder(View view, StremioPresentationBindings bindings) {
		super(view);
		this.bindings = bindings;
	}
	void select(T model) {
		itemView.setTag(R.id.stremio_presentation_item_key, model.stableKey());
		itemView.setOnClickListener(view -> bindings.listener.onModelSelected(model));
	}
}

final class StremioActionHolder extends StremioModelHolder<StremioUiModel.Action> {
	private final ImageView icon;
	private final TextView title;
	StremioActionHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings);
		icon = view.findViewById(R.id.stremio_presentation_action_icon);
		title = view.findViewById(R.id.stremio_presentation_action_title);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.Action model = (StremioUiModel.Action) raw;
		title.setText(model.title());
		icon.setImageResource(StremioPresentationAdapter.actionIcon(model.kind()));
		itemView.setContentDescription(model.title());
		select(model);
	}
}

final class StremioActionBarHolder extends StremioModelHolder<StremioUiModel.ActionBar>
		implements StremioHorizontalPositionHolder {
	private final RecyclerView actions;
	private final StremioActionBarAdapter adapter;
	private String key;
	StremioActionBarHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings);
		actions = (RecyclerView) view;
		actions.setLayoutManager(new LinearLayoutManager(view.getContext(), RecyclerView.HORIZONTAL, false));
		adapter = new StremioActionBarAdapter(bindings);
		actions.setAdapter(adapter);
	}
	@Override void bind(StremioUiModel raw) {
		savePosition();
		StremioUiModel.ActionBar model = (StremioUiModel.ActionBar) raw;
		key = model.stableKey();
		adapter.submit(model.actions());
		restorePosition();
	}
	@Override void recycle() { savePosition(); key = null; super.recycle(); adapter.submit(List.of()); }
	@Override public void savePosition() { bindings.save(key, actions); }
	@Override public void restorePosition() { bindings.restore(key, actions); }
}

final class StremioSectionHolder extends StremioModelHolder<StremioUiModel.Section>
		implements StremioHorizontalPositionHolder {
	private final TextView title;
	private final TextView seeAll;
	private final RecyclerView shelf;
	private final StremioPosterShelfAdapter adapter;
	private String key;
	StremioSectionHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings);
		title = view.findViewById(R.id.stremio_presentation_section_title);
		seeAll = view.findViewById(R.id.stremio_presentation_section_see_all);
		shelf = view.findViewById(R.id.stremio_presentation_section_shelf);
		shelf.setLayoutManager(new LinearLayoutManager(view.getContext(), RecyclerView.HORIZONTAL, false));
		shelf.setRecycledViewPool(bindings.posterPool);
		adapter = new StremioPosterShelfAdapter(bindings);
		shelf.setAdapter(adapter);
		shelf.addOnLayoutChangeListener((changed, left, top, right, bottom, oldLeft, oldTop,
				oldRight, oldBottom) -> {
			if ((right - left) != (oldRight - oldLeft)) {
				int width = StremioPresentationAdapter.shelfPosterWidth(shelf);
				adapter.setPosterWidth(width);
				updateShelfHeight(width);
			}
		});
	}
	@Override void bind(StremioUiModel raw) {
		savePosition();
		StremioUiModel.Section model = (StremioUiModel.Section) raw;
		key = model.stableKey(); title.setText(model.title()); bindSeeAll(model.seeAll());
		int width = StremioPresentationAdapter.shelfPosterWidth(shelf);
		adapter.setPosterWidth(width); updateShelfHeight(width); adapter.submit(model.posters());
		restorePosition();
	}
	@Override void recycle() {
		savePosition(); key = null; seeAll.setOnClickListener(null); super.recycle();
		adapter.submit(List.of());
	}
	@Override public void savePosition() { bindings.save(key, shelf); }
	@Override public void restorePosition() { bindings.restore(key, shelf); }
	private void bindSeeAll(@Nullable StremioUiModel.Action action) {
		if (action == null) {
			seeAll.setVisibility(View.GONE); seeAll.setOnClickListener(null);
			seeAll.setTag(R.id.stremio_presentation_item_key, null); return;
		}
		seeAll.setText(action.title()); seeAll.setContentDescription(action.title());
		seeAll.setTag(R.id.stremio_presentation_item_key, action.stableKey());
		seeAll.setOnClickListener(clicked -> bindings.listener.onModelSelected(action));
		seeAll.setVisibility(View.VISIBLE);
	}
	private void updateShelfHeight(int widthPx) {
		if (widthPx <= 0) return;
		float density = shelf.getResources().getDisplayMetrics().density;
		int widthDp = Math.round(widthPx / density);
		shelf.setMinimumHeight(Math.round(StremioLayoutPolicy.shelfHeightDp(widthDp) * density));
	}
}

final class StremioPosterHolder extends StremioModelHolder<StremioUiModel.Poster> {
	private final ImageView image;
	private final TextView title;
	private final LinearProgressIndicator progress;
	StremioPosterHolder(View view, StremioPresentationBindings bindings, int width) {
		super(view, bindings); setCardWidth(width);
		image = view.findViewById(R.id.stremio_presentation_poster_image);
		title = view.findViewById(R.id.stremio_presentation_poster_title);
		progress = view.findViewById(R.id.stremio_presentation_poster_progress);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.Poster model = (StremioUiModel.Poster) raw;
		title.setText(model.title()); StremioPresentationAdapter.bindProgress(progress, model.progress());
		bindings.artwork.bind(image, model.artwork(), model.fallbackArtwork(), R.drawable.stremio_poster_placeholder_mark);
		itemView.setContentDescription(StremioPresentationAdapter.join(model.title(), model.subtitle()));
		select(model);
		itemView.setOnLongClickListener(model.progressDismissible() ? clicked -> {
			bindings.listener.onPosterProgressDismissRequested(model); return true;
		} : null);
		itemView.setLongClickable(model.progressDismissible());
	}
	@Override void recycle() {
		super.recycle(); itemView.setOnLongClickListener(null); itemView.setLongClickable(false);
		bindings.artwork.clear(image);
	}
	void setCardWidth(int width) {
		if (width <= 0) return;
		ViewGroup.LayoutParams params = itemView.getLayoutParams();
		if ((params != null) && (params.width != width)) { params.width = width; itemView.setLayoutParams(params); }
	}
}

final class StremioFilterHolder extends StremioModelHolder<StremioUiModel.Filter>
		implements StremioHorizontalPositionHolder {
	private final TextView label;
	private final RecyclerView options;
	private final StremioFilterOptionAdapter adapter;
	private String key;
	StremioFilterHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings);
		label = view.findViewById(R.id.stremio_presentation_filter_label);
		options = view.findViewById(R.id.stremio_presentation_filter_options);
		options.setLayoutManager(new LinearLayoutManager(view.getContext(), RecyclerView.HORIZONTAL, false));
		adapter = new StremioFilterOptionAdapter(bindings); options.setAdapter(adapter);
	}
	@Override void bind(StremioUiModel raw) {
		savePosition(); StremioUiModel.Filter model = (StremioUiModel.Filter) raw;
		key = model.stableKey(); label.setText(model.label()); adapter.submit(model); restorePosition();
	}
	@Override void recycle() { savePosition(); key = null; super.recycle(); }
	@Override public void savePosition() { bindings.save(key, options); }
	@Override public void restorePosition() { bindings.restore(key, options); }
}

final class StremioDetailsHolder extends StremioModelHolder<StremioUiModel.DetailsHeader> {
	private final ImageView backdrop;
	private final ImageView poster;
	private final TextView title;
	private final TextView metadata;
	private final TextView overview;
	private final ImageButton watch;
	private final ImageButton favorite;
	private final Button subtitles;
	StremioDetailsHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings);
		backdrop = view.findViewById(R.id.stremio_presentation_details_backdrop);
		poster = view.findViewById(R.id.stremio_presentation_details_poster);
		title = view.findViewById(R.id.stremio_presentation_details_title);
		metadata = view.findViewById(R.id.stremio_presentation_details_metadata);
		overview = view.findViewById(R.id.stremio_presentation_details_overview);
		watch = view.findViewById(R.id.stremio_presentation_details_watch);
		favorite = view.findViewById(R.id.stremio_presentation_details_favorite);
		subtitles = view.findViewById(R.id.stremio_presentation_details_subtitles);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.DetailsHeader model = (StremioUiModel.DetailsHeader) raw;
		applyResponsiveSize(); title.setText(model.title());
		StremioPresentationAdapter.setOptionalText(metadata, model.metadata());
		StremioPresentationAdapter.setOptionalText(overview, model.overview());
		bindings.artwork.bind(backdrop, model.backdrop(), model.poster(), R.drawable.stremio_poster_placeholder_mark);
		bindings.artwork.bind(poster, model.poster(), model.backdrop(), R.drawable.stremio_poster_placeholder_mark);
		watch.setVisibility(model.watchable() ? View.VISIBLE : View.GONE); watch.setFocusable(model.watchable());
		watch.setImageResource(me.aap.fermata.R.drawable.play);
		watch.setContentDescription(itemView.getContext().getString(model.resumable() ?
				R.string.stremio_presentation_resume : R.string.stremio_presentation_watch));
		favorite.setImageResource(model.favorite() ? me.aap.fermata.R.drawable.favorite_filled : me.aap.fermata.R.drawable.favorite);
		favorite.setVisibility(model.favoriteSupported() ? View.VISIBLE : View.GONE);
		favorite.setFocusable(model.favoriteSupported());
		favorite.setContentDescription(itemView.getContext().getString(model.favorite() ?
				R.string.stremio_presentation_remove_favorite : R.string.stremio_presentation_add_favorite));
		subtitles.setVisibility(model.subtitlesSupported() ? View.VISIBLE : View.GONE);
		subtitles.setFocusable(model.subtitlesSupported());
		subtitles.setCompoundDrawablesWithIntrinsicBounds(me.aap.fermata.R.drawable.subtitles, 0, 0, 0);
		subtitles.setCompoundDrawablePadding(Math.round(6 * itemView.getResources().getDisplayMetrics().density));
		subtitles.setText(R.string.stremio_presentation_subtitles);
		subtitles.setContentDescription(itemView.getContext().getString(R.string.stremio_presentation_subtitles));
		itemView.setTag(R.id.stremio_presentation_item_key, model.stableKey());
		watch.setTag(R.id.stremio_presentation_item_key, model.stableKey());
		watch.setOnClickListener(model.watchable() ? view -> bindings.listener.onDetailsAction(model,
				StremioPresentationAdapter.DetailsAction.WATCH_OR_RESUME) : null);
		favorite.setOnClickListener(model.favoriteSupported() ? view -> bindings.listener.onDetailsAction(model,
				StremioPresentationAdapter.DetailsAction.FAVORITE) : null);
		subtitles.setOnClickListener(model.subtitlesSupported() ? view -> bindings.listener.onDetailsAction(model,
				StremioPresentationAdapter.DetailsAction.SUBTITLES) : null);
	}
	private void applyResponsiveSize() {
		float density = itemView.getResources().getDisplayMetrics().density;
		int width = itemView.getResources().getConfiguration().screenWidthDp;
		ViewGroup.LayoutParams posterParams = poster.getLayoutParams();
		posterParams.width = Math.round(StremioLayoutPolicy.detailsPosterWidthDp(width) * density); poster.setLayoutParams(posterParams);
		ViewGroup.LayoutParams backdropParams = backdrop.getLayoutParams();
		backdropParams.height = Math.round(StremioLayoutPolicy.detailsBackdropHeightDp(width) * density); backdrop.setLayoutParams(backdropParams);
	}
	@Override void recycle() {
		super.recycle(); watch.setOnClickListener(null); favorite.setOnClickListener(null); subtitles.setOnClickListener(null);
		bindings.artwork.clear(backdrop); bindings.artwork.clear(poster);
	}
}

final class StremioEpisodeHolder extends StremioModelHolder<StremioUiModel.Episode> {
	private final ImageView thumbnail; private final TextView number; private final TextView title;
	private final TextView metadata; private final LinearProgressIndicator progress;
	StremioEpisodeHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings); thumbnail = view.findViewById(R.id.stremio_presentation_episode_thumbnail);
		number = view.findViewById(R.id.stremio_presentation_episode_number); title = view.findViewById(R.id.stremio_presentation_episode_title);
		metadata = view.findViewById(R.id.stremio_presentation_episode_metadata); progress = view.findViewById(R.id.stremio_presentation_episode_progress);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.Episode model = (StremioUiModel.Episode) raw;
		number.setText(model.number()); title.setText(model.title()); StremioPresentationAdapter.setOptionalText(metadata, model.metadata());
		StremioPresentationAdapter.bindProgress(progress, model.progress()); bindings.artwork.bind(thumbnail, model.thumbnail(), R.drawable.stremio_poster_placeholder_mark);
		itemView.setContentDescription(StremioPresentationAdapter.join(model.number(), model.title(), model.metadata())); select(model);
	}
	@Override void recycle() { super.recycle(); bindings.artwork.clear(thumbnail); }
}

final class StremioStreamGroupHolder extends StremioModelHolder<StremioUiModel.StreamGroup> {
	private final TextView provider; private final TextView state; private final ProgressBar progress;
	StremioStreamGroupHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings); provider = view.findViewById(R.id.stremio_presentation_stream_provider);
		state = view.findViewById(R.id.stremio_presentation_stream_state); progress = view.findViewById(R.id.stremio_presentation_stream_state_progress);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.StreamGroup model = (StremioUiModel.StreamGroup) raw; provider.setText(model.providerName());
		boolean loading = model.state() == StremioUiModel.ProviderState.LOADING;
		progress.setVisibility(loading ? View.VISIBLE : View.GONE); state.setVisibility(loading ? View.GONE : View.VISIBLE);
		state.setText(StremioPresentationAdapter.providerStateLabel(model.state()));
		itemView.setContentDescription(StremioPresentationAdapter.join(model.providerName(), itemView.getContext().getString(StremioPresentationAdapter.providerStateLabel(model.state()))));
	}
}

final class StremioStreamChoiceHolder extends StremioModelHolder<StremioUiModel.StreamChoice> {
	private final TextView title; private final TextView details; private final TextView recommended;
	StremioStreamChoiceHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings); title = view.findViewById(R.id.stremio_presentation_stream_title);
		details = view.findViewById(R.id.stremio_presentation_stream_details); recommended = view.findViewById(R.id.stremio_presentation_stream_recommended);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.StreamChoice model = (StremioUiModel.StreamChoice) raw; title.setText(model.title());
		StremioPresentationAdapter.setOptionalText(details, model.details()); recommended.setVisibility(model.recommended() ? View.VISIBLE : View.GONE);
		itemView.setContentDescription(StremioPresentationAdapter.join(model.title(), model.details())); select(model);
	}
}

final class StremioStateHolder extends StremioModelHolder<StremioUiModel.StateRow> {
	private final ImageView icon; private final ProgressBar progress; private final TextView message;
	StremioStateHolder(View view, StremioPresentationBindings bindings) {
		super(view, bindings); icon = view.findViewById(R.id.stremio_presentation_state_icon);
		progress = view.findViewById(R.id.stremio_presentation_state_progress); message = view.findViewById(R.id.stremio_presentation_state_message);
	}
	@Override void bind(StremioUiModel raw) {
		StremioUiModel.StateRow model = (StremioUiModel.StateRow) raw; boolean loading = model.kind() == StremioUiModel.StateKind.LOADING;
		progress.setVisibility(loading ? View.VISIBLE : View.GONE); icon.setVisibility(loading ? View.GONE : View.VISIBLE);
		if (!loading) icon.setImageResource(StremioPresentationAdapter.stateIcon(model.kind()));
		message.setText(model.message()); itemView.setContentDescription(model.message()); select(model);
	}
}

final class StremioPosterShelfAdapter extends RecyclerView.Adapter<StremioPosterHolder> {
	private final List<StremioUiModel.Poster> posters = new ArrayList<>();
	private final StremioPresentationBindings bindings;
	private int width;
	StremioPosterShelfAdapter(StremioPresentationBindings bindings) { this.bindings = bindings; setHasStableIds(true); }
	void submit(List<StremioUiModel.Poster> replacement) { posters.clear(); posters.addAll(replacement); notifyDataSetChanged(); }
	void setPosterWidth(int replacement) {
		if ((replacement <= 0) || (width == replacement)) return; width = replacement; notifyItemRangeChanged(0, posters.size());
	}
	@Override public long getItemId(int position) { return StremioPresentationContract.stableId(posters.get(position)); }
	@Override @NonNull public StremioPosterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		int cardWidth = (width > 0) ? width : parent.getResources().getDimensionPixelSize(R.dimen.stremio_presentation_shelf_poster_width);
		return new StremioPosterHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.stremio_presentation_poster, parent, false), bindings, cardWidth);
	}
	@Override public void onBindViewHolder(@NonNull StremioPosterHolder holder, int position) { holder.setCardWidth(width); holder.bind(posters.get(position)); }
	@Override public void onViewRecycled(@NonNull StremioPosterHolder holder) { holder.recycle(); }
	@Override public int getItemCount() { return posters.size(); }
}

final class StremioFilterOptionAdapter extends RecyclerView.Adapter<StremioFilterOptionHolder> {
	private final StremioPresentationBindings bindings;
	private StremioUiModel.Filter filter;
	private List<StremioUiModel.Option> options = List.of();
	StremioFilterOptionAdapter(StremioPresentationBindings bindings) { this.bindings = bindings; setHasStableIds(true); }
	void submit(StremioUiModel.Filter model) { filter = model; options = model.options(); notifyDataSetChanged(); }
	@Override public long getItemId(int position) { return StremioPresentationAdapter.stableOptionId(options.get(position).stableKey()); }
	@Override @NonNull public StremioFilterOptionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new StremioFilterOptionHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.stremio_presentation_filter_option, parent, false), bindings);
	}
	@Override public void onBindViewHolder(@NonNull StremioFilterOptionHolder holder, int position) { holder.bind(filter, options.get(position)); }
	@Override public int getItemCount() { return options.size(); }
}

final class StremioActionBarAdapter extends RecyclerView.Adapter<StremioCompactActionHolder> {
	private final StremioPresentationBindings bindings;
	private List<StremioUiModel.Action> actions = List.of();
	StremioActionBarAdapter(StremioPresentationBindings bindings) { this.bindings = bindings; setHasStableIds(true); }
	void submit(List<StremioUiModel.Action> replacement) { actions = List.copyOf(replacement); notifyDataSetChanged(); }
	@Override public long getItemId(int position) { return StremioPresentationContract.stableId(actions.get(position)); }
	@Override @NonNull public StremioCompactActionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new StremioCompactActionHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.stremio_presentation_action_bar_item, parent, false), bindings);
	}
	@Override public void onBindViewHolder(@NonNull StremioCompactActionHolder holder, int position) { holder.bind(actions.get(position)); }
	@Override public int getItemCount() { return actions.size(); }
}

final class StremioCompactActionHolder extends RecyclerView.ViewHolder {
	private final StremioPresentationBindings bindings; private final ImageView icon; private final TextView title;
	StremioCompactActionHolder(View view, StremioPresentationBindings bindings) {
		super(view); this.bindings = bindings; icon = view.findViewById(R.id.stremio_presentation_compact_action_icon);
		title = view.findViewById(R.id.stremio_presentation_compact_action_title);
	}
	void bind(StremioUiModel.Action action) {
		icon.setImageResource(StremioPresentationAdapter.actionIcon(action.kind())); title.setText(action.title());
		itemView.setContentDescription(action.title()); itemView.setTag(R.id.stremio_presentation_item_key, action.stableKey());
		itemView.setOnClickListener(view -> bindings.listener.onModelSelected(action));
	}
}

final class StremioFilterOptionHolder extends RecyclerView.ViewHolder {
	private final StremioPresentationBindings bindings; private final TextView label;
	StremioFilterOptionHolder(View view, StremioPresentationBindings bindings) { super(view); this.bindings = bindings; label = (TextView) view; }
	void bind(StremioUiModel.Filter filter, StremioUiModel.Option option) {
		label.setText(option.label()); label.setSelected(option.stableKey().equals(filter.selectedKey()));
		label.setContentDescription(option.label()); label.setTag(R.id.stremio_presentation_item_key, option.stableKey());
		label.setOnClickListener(view -> bindings.listener.onFilterOptionSelected(filter, option));
	}
}
