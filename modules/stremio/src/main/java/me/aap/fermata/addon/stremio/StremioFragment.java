package me.aap.fermata.addon.stremio;

import static android.view.View.GONE;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.presentation.StremioPresentationGateway;
import me.aap.fermata.addon.stremio.presentation.StremioPresentationText;
import me.aap.fermata.addon.stremio.presentation.StremioSelectionGate;
import me.aap.fermata.addon.stremio.presentation.StremioPresenter;
import me.aap.fermata.addon.stremio.presentation.StremioRoute;
import me.aap.fermata.addon.stremio.presentation.StremioScreenState;
import me.aap.fermata.addon.stremio.presentation.StremioSelection;
import me.aap.fermata.addon.stremio.presentation.StremioUiModel;
import me.aap.fermata.addon.stremio.presentation.StremioViewportState;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeGraph;
import me.aap.fermata.addon.stremio.ui.StremioFavoriteController;
import me.aap.fermata.addon.stremio.ui.StremioNavigationController;
import me.aap.fermata.addon.stremio.ui.StremioPlaybackHandoffController;
import me.aap.fermata.addon.stremio.ui.StremioSearchController;
import me.aap.fermata.addon.stremio.ui.StremioSourceController;
import me.aap.fermata.addon.stremio.ui.StremioSubtitleController;
import me.aap.fermata.addon.stremio.ui.StremioViewportController;
import me.aap.fermata.addon.stremio.ui.presentation.StremioPresentationAdapter;
import me.aap.fermata.addon.stremio.ui.source.SourceUiResult;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaItemNavigationTarget;
import me.aap.fermata.ui.policy.BackNavigationPolicy;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/** Native film-first Stremio surface; protocol/playback ownership stays in the existing runtime. */
public final class StremioFragment extends MainActivityFragment
		implements MediaItemNavigationTarget, StremioPresentationAdapter.Listener {
	private static final long PLAY_DEBOUNCE_MS = 700L;

	private final StremioNavigationController navigation = new StremioNavigationController();
	private final StremioSearchController search = new StremioSearchController();
	private final StremioSubtitleController subtitles = new StremioSubtitleController();
	private final StremioFavoriteController favorites = new StremioFavoriteController();
	private final StremioSourceController sources = new StremioSourceController();
	private final StremioPlaybackHandoffController playback =
			new StremioPlaybackHandoffController();
	private final StremioViewportController viewport = new StremioViewportController();
	private StremioPresentationGateway presentationGateway;
	private StremioPresenter presenter;
	private StremioRuntimeGraph graph;
	private StremioScreenState state;
	private BooleanConsumer refreshCallback;
	private String continueDismissal;
	private AutoCloseable sourceObserver;
	private AutoCloseable progressObserver;
	private boolean progressRefreshArmed;
	private boolean progressRefreshPending;
	private final StremioSelectionGate selectionGate =
			new StremioSelectionGate(PLAY_DEBOUNCE_MS);
	private final StremioFavoriteController.Host favoriteHost =
			new StremioFavoriteController.Host() {
				@Override public StremioPresenter presenter() { return presenter; }
				@Override public StremioPresentationGateway gateway() {
					return presentationGateway;
				}
				@Override
				public FutureSupplier<Void> setFavorite(DefaultMediaLib lib,
						me.aap.fermata.media.lib.MediaLib.PlayableItem item, boolean favorite) {
					return getAddon().setFavorite(lib, item, favorite);
				}
			};
	private final StremioPlaybackHandoffController.Host playbackHost =
			new StremioPlaybackHandoffController.Host() {
				@Override public StremioRuntimeGraph graph() { return graph; }
				@Override public StremioPresentationGateway gateway() {
					return presentationGateway;
				}
				@Override public StremioPresenter presenter() { return presenter; }
				@Override public MainActivityDelegate activity() { return getActivityDelegate(); }
				@Override public StremioAddon addon() { return getAddon(); }
				@Override
				public void openCanonicalPath(BrowseMedia media, BrowseEpisode episode,
						BrowseSeason season, boolean streams, String title, StremioRoute origin) {
					StremioFragment.this.openCanonicalPath(media, episode, season, streams,
							title, origin);
				}
				@Override public void releaseSelection(String key) { selectionGate.release(key); }
			};

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.stremio_fragment;
	}

	@Override
	public CharSequence getTitle() {
		StremioScreenState current = state;
		if (current == null) return getString(R.string.stremio_title);
		String dynamic = navigation.title(current);
		if ((dynamic != null) && !dynamic.isBlank()) return dynamic;
		if (current.route() instanceof StremioRoute.Discover) {
			return getString(R.string.stremio_discover);
		}
		if (current.route() instanceof StremioRoute.Search) {
			return getString(R.string.stremio_search);
		}
		if (current.route() instanceof StremioRoute.Library) {
			return getString(R.string.stremio_library);
		}
		String details = StremioNavigationController.detailsTitle(current.models());
		if (details != null) return details;
		return getString(R.string.stremio_title);
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return HiddenFloatingButtonMediator.INSTANCE;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.stremio_presentation_screen, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		RecyclerView list = view.findViewById(R.id.stremio_presentation_list);
		StremioPresentationAdapter adapter = new StremioPresentationAdapter(this);
		GridLayoutManager layout = new GridLayoutManager(requireContext(),
				StremioViewportController.posterColumns(list, getResources()));
		viewport.attach(list, layout, adapter);
		layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return (position < adapter.getItemCount()) ?
						adapter.getSpanSize(position, layout.getSpanCount()) : layout.getSpanCount();
			}
		});
		list.setLayoutManager(layout);
		list.setAdapter(adapter);
		list.addOnLayoutChangeListener((changed, left, top, right, bottom,
				oldLeft, oldTop, oldRight, oldBottom) -> viewport.updateColumns(getResources()));

		if (presenter != null) {
			renderState(presenter.getState());
			return;
		}
		MainActivityDelegate activity = getActivityDelegate();
		getAddon().getGraph((DefaultMediaLib) activity.getLib())
				.timeout(30_000L).main(activity.getHandler()).onSuccess(runtime -> {
					if (getView() != view) return;
					initialize(runtime);
				}).onFailure(error -> {
					if (getView() == view) renderBootstrapFailure();
				});
	}

	private void initialize(StremioRuntimeGraph runtime) {
		graph = runtime;
		presentationGateway = new StremioPresentationGateway(runtime.items(), runtime.sessions(),
				new AndroidPresentationText(requireContext()));
		MainActivityDelegate activity = getActivityDelegate();
		presenter = new StremioPresenter(presentationGateway, activity::post, this::renderState);
		closeSourceObserver();
		sourceObserver = runtime.observeSourceChanges(() -> activity.post(() -> {
			if ((graph == runtime) && (presenter != null)) presenter.refresh();
		}));
		closeProgressObserver();
		progressObserver = runtime.sessions().observeProgressChanges(() -> activity.post(() -> {
			if ((graph != runtime) || (presenter == null) || !progressRefreshArmed) return;
			progressRefreshArmed = false;
			progressRefreshPending = true;
			refreshHomeProgress();
		}));
		presenter.start();
		String query = search.takePendingQuery();
		if ((query != null) && !query.isBlank()) showSearchResults(query);
		Item item = playback.takePendingItem();
		if (item != null) showMediaItem(item);
	}

	@Override
	public void onDestroyView() {
		search.clearView();
		playback.clearView();
		if (refreshCallback != null) refreshCallback.accept(false);
		refreshCallback = null;
		viewport.clearView();
		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		closeSourceObserver();
		closeProgressObserver();
		StremioPresenter presenter = this.presenter;
		this.presenter = null;
		if (presenter != null) presenter.close();
		StremioPresentationGateway gateway = presentationGateway;
		presentationGateway = null;
		if (gateway != null) gateway.close();
		graph = null;
		state = null;
		favorites.clear();
		playback.clear();
		continueDismissal = null;
		progressRefreshArmed = false;
		progressRefreshPending = false;
		navigation.clear();
		super.onDestroy();
	}

	private void closeSourceObserver() {
		AutoCloseable observer = sourceObserver;
		sourceObserver = null;
		if (observer == null) return;
		try {
			observer.close();
		} catch (Exception ignored) {
		}
	}

	private void closeProgressObserver() {
		AutoCloseable observer = progressObserver;
		progressObserver = null;
		if (observer == null) return;
		try {
			observer.close();
		} catch (Exception ignored) {
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration configuration) {
		super.onConfigurationChanged(configuration);
		viewport.updateColumns(getResources());
	}

	@Override
	public boolean isRootPage() {
		return (state == null) || (state.route() instanceof StremioRoute.Home);
	}

	@Override
	public boolean onBackPressed() {
		MainActivityDelegate activity = getActivityDelegate();
		if (BackNavigationPolicy.leaveVideoMode(activity)) {
			progressRefreshArmed = true;
			FutureSupplier<Void> save = activity.getMediaSessionCallback()
					.saveCurrentPlaybackProgress().main(activity.getHandler());
			save.onCompletion((ignored, error) -> activity.post(() -> {
				// A no-op/stale flush emits no session event, but Home still needs fresh data.
				if (!progressRefreshArmed) return;
				progressRefreshArmed = false;
				progressRefreshPending = true;
				refreshHomeProgress();
			}));
			return true;
		}
		saveViewport();
		return ((presenter != null) && presenter.back()) || super.onBackPressed();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && isAdded()) getActivityDelegate().post(this::refreshHomeProgress);
	}

	@Override
	public void navBarItemReselected(int itemId) {
		if (presenter != null) {
			progressRefreshPending = false;
			presenter.start();
		}
		viewport.scrollToTop();
	}

	@Override
	public void onRefresh(BooleanConsumer refreshing) {
		if (refreshCallback != null) refreshCallback.accept(false);
		refreshCallback = refreshing;
		if (presenter == null) refreshing.accept(false);
		else presenter.refresh();
	}

	@Override
	public boolean canScrollUp() {
		return viewport.canScrollUp();
	}

	@Override
	public boolean isVideoModeSupported() {
		return true;
	}

	@Override
	public boolean isSplitViewSupported() {
		return false;
	}

	@Override
	public boolean isVoiceCommandsSupported() {
		return true;
	}

	@Override
	public void voiceCommand(VoiceCommand command) {
		getAddon().handleVoiceSearch(getActivityDelegate(), command.getQuery(), command.isPlay());
	}

	void showSearchResults(String query) {
		search.showResults(query, presenter != null, normalized -> navigate(
				new StremioRoute.Search(normalized), getString(R.string.stremio_search)));
	}

	@Override
	public void onModelSelected(StremioUiModel model) {
		select(model.stableKey(), model instanceof StremioUiModel.StateRow stateRow &&
				stateRow.kind() == StremioUiModel.StateKind.ERROR);
	}

	@Override
	public void onFilterOptionSelected(StremioUiModel.Filter filter,
			StremioUiModel.Option option) {
		select(option.stableKey(), false);
	}

	@Override
	public void onDetailsAction(StremioUiModel.DetailsHeader details,
			StremioPresentationAdapter.DetailsAction action) {
		if (action == StremioPresentationAdapter.DetailsAction.WATCH_OR_RESUME) {
			select(details.stableKey(), false);
		} else if (action == StremioPresentationAdapter.DetailsAction.FAVORITE) {
			favorites.toggle(favoriteHost, getActivityDelegate(), details);
		} else if (action == StremioPresentationAdapter.DetailsAction.SUBTITLES) {
			subtitles.show(requireContext(), getActivityDelegate(), presentationGateway,
					details.stableKey());
		}
	}

	@Override
	public void onPosterProgressDismissRequested(StremioUiModel.Poster poster) {
		StremioScreenState current = state;
		StremioRuntimeGraph graph = this.graph;
		StremioPresentationGateway gateway = presentationGateway;
		StremioPresenter presenter = this.presenter;
		if ((current == null) || (graph == null) || (gateway == null) || (presenter == null)) return;
		MainActivityDelegate activity = getActivityDelegate();
		StremioSelection selection = current.selections().get(poster.stableKey());
		if (!(selection instanceof StremioSelection.Restore restore)) return;
		if (continueDismissal != null) return;
		continueDismissal = restore.stableId();
		saveViewport();
		StremioRoute route = current.route();
		graph.sessions().dismissContinue(restore.stableId()).whenComplete((ignored, error) ->
				activity.post(() -> {
					if (restore.stableId().equals(continueDismissal)) continueDismissal = null;
					if ((this.presenter != presenter) ||
							(this.presentationGateway != gateway)) return;
					if (error != null) {
						UiUtils.showAlert(activity.getContext(),
								R.string.stremio_continue_dismiss_failed);
						return;
					}
					gateway.forgetResume(restore.stableId());
					StremioScreenState latest = state;
					if ((latest != null) && latest.route().equals(route)) presenter.refresh();
				}));
	}

	private void select(String stableKey, boolean retryFallback) {
		StremioScreenState current = state;
		if ((current == null) || (presenter == null)) return;
		if (!selectionGate.accept(stableKey, SystemClock.uptimeMillis())) return;
		StremioSelection selection = current.selections().get(stableKey);
		if (selection instanceof StremioSelection.Navigate navigate) {
			String title = StremioNavigationController.modelTitle(stableKey, current.models());
			if (navigate.replace()) replace(navigate.route(), title,
					stableKey.startsWith("filter:"));
			else navigate(navigate.route(), title);
		} else if (selection instanceof StremioSelection.Restore restore) {
			playback.resolvePersistentTarget(playbackHost, restore.stableId(), restore.streams(),
					stableKey, current.route());
		} else if (selection instanceof StremioSelection.Command command) {
			handleCommand(command.action());
		} else if (selection instanceof StremioSelection.Play play) {
			playback.play(playbackHost, play.stableKey());
		} else if (selection instanceof StremioSelection.Subtitles subtitles) {
			this.subtitles.show(requireContext(), getActivityDelegate(), presentationGateway,
					subtitles.stableKey());
		} else if (retryFallback) {
			presenter.refresh();
		}
	}

	private void handleCommand(StremioUiModel.ActionKind action) {
		switch (action) {
			case SEARCH -> search();
			case LIBRARY -> navigate(new StremioRoute.Library(),
					getString(R.string.stremio_library));
			case ADDONS -> showSources();
			case DISCOVER -> presenter.refresh();
			case RETRY -> presenter.refresh();
			default -> {
			}
		}
	}

	private void navigate(StremioRoute route, String title) {
		navigation.navigate(presenter, route, title, this::saveViewport);
	}

	private void replace(StremioRoute route, String title, boolean preserveViewport) {
		navigation.replace(presenter, state, route, title, preserveViewport, this::saveViewport);
	}

	@Override
	public void showMediaItem(Item item) {
		playback.showMediaItem(playbackHost, item);
	}

	private void openCanonicalPath(BrowseMedia media, BrowseEpisode episode, BrowseSeason season,
			boolean streams, String title, StremioRoute origin) {
		navigation.openCanonicalPath(presenter, presentationGateway, media, episode, season,
				streams, title, origin);
	}

	private void search() {
		search.request(requireContext(), this::showSearchResults);
	}

	private void renderState(StremioScreenState state) {
		if (state == null) return;
		this.state = state;
		StremioPresentationAdapter adapter = viewport.adapter();
		if (adapter != null) {
			List<StremioUiModel> models = new ArrayList<>(state.models());
			if ((state.phase() == StremioScreenState.Phase.LOADING) && models.isEmpty()) {
				models.add(new StremioUiModel.StateRow("state:loading",
						getString(R.string.stremio_loading), StremioUiModel.StateKind.LOADING));
			} else if (state.phase() == StremioScreenState.Phase.ERROR) {
				models.add(new StremioUiModel.StateRow("state:error",
						getString(R.string.stremio_load_error), StremioUiModel.StateKind.ERROR));
			}
			adapter.submitModels(models);
			restoreViewport(state);
		}
		if ((state.phase() != StremioScreenState.Phase.LOADING) && (refreshCallback != null)) {
			refreshCallback.accept(false);
			refreshCallback = null;
		}
		getActivityDelegate().fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
		if (progressRefreshPending && (state.route() instanceof StremioRoute.Home)) {
			getActivityDelegate().post(this::refreshHomeProgress);
		}
	}

	private void refreshHomeProgress() {
		StremioPresenter presenter = this.presenter;
		StremioScreenState state = this.state;
		if (!progressRefreshPending || (presenter == null) || (state == null) ||
				!(state.route() instanceof StremioRoute.Home) || (getView() == null) || isHidden()) {
			return;
		}
		progressRefreshPending = false;
		presenter.refresh();
	}

	private void renderBootstrapFailure() {
		StremioPresentationAdapter adapter = viewport.adapter();
		if (adapter != null) adapter.submitModels(List.of(new StremioUiModel.StateRow(
				"state:bootstrap-error", getString(R.string.stremio_load_error),
				StremioUiModel.StateKind.ERROR)));
	}

	private void saveViewport() {
		viewport.save(presenter);
	}

	private void restoreViewport(StremioScreenState renderedState) {
		viewport.restore(renderedState, () -> state);
	}

	private StremioAddon getAddon() {
		return requireNonNull(AddonManager.get().getAddon(StremioAddon.class));
	}

	private void showSources() {
		sources.show(graph, getActivityDelegate(), getFragmentId(),
				getString(R.string.stremio_sources_title), this::refreshPresenter,
				this::sourceOperationFinished);
	}

	void addSource() {
		sources.add(graph, getActivityDelegate(), this::sourceOperationFinished);
	}

	private void sourceOperationFinished(SourceUiResult result, Throwable error) {
		sources.finish(getActivityDelegate(), getFragmentId(), this::refreshPresenter,
				result, error);
	}

	private void refreshPresenter() {
		if (presenter != null) presenter.refresh();
	}

	private static final class HiddenFloatingButtonMediator implements FloatingButton.Mediator {
		private static final HiddenFloatingButtonMediator INSTANCE =
				new HiddenFloatingButtonMediator();

		@Override
		public void enable(FloatingButton button, ActivityFragment fragment) {
			button.setVisibility(GONE);
			FloatingButton.Mediator.super.disable(button);
		}

		@Override
		public void disable(FloatingButton button) {
			FloatingButton.Mediator.super.disable(button);
			button.setVisibility(View.VISIBLE);
		}
	}

	private static final class AndroidPresentationText implements StremioPresentationText {
		private final Context context;

		private AndroidPresentationText(Context context) {
			this.context = context;
		}

		@Override
		public String action(StremioUiModel.ActionKind kind) {
			return context.getString(switch (kind) {
				case SEARCH -> R.string.stremio_presentation_search;
				case DISCOVER -> R.string.stremio_discover;
				case LIBRARY -> R.string.stremio_library;
				case ADDONS -> R.string.stremio_addons;
				case WATCH -> R.string.stremio_presentation_watch;
				case FAVORITE -> R.string.stremio_presentation_add_favorite;
				case SUBTITLES -> R.string.stremio_presentation_subtitles;
				case RETRY -> R.string.stremio_retry;
				case NEXT_PAGE -> R.string.stremio_next_page;
			});
		}

		@Override
		public String label(Label kind) {
			return context.getString(switch (kind) {
				case CONTINUE_WATCHING -> R.string.stremio_continue_watching;
				case POPULAR_MOVIES -> R.string.stremio_popular_movies;
				case POPULAR_SERIES -> R.string.stremio_popular_series;
				case NEW_MOVIES -> R.string.stremio_new_movies;
				case NEW_SERIES -> R.string.stremio_new_series;
				case FEATURED_MOVIES -> R.string.stremio_featured_movies;
				case FEATURED_SERIES -> R.string.stremio_featured_series;
				case NO_SOURCES -> R.string.stremio_no_sources;
				case NO_CONTENT -> R.string.stremio_no_content;
				case TYPE -> R.string.stremio_filter_type;
				case CATALOG -> R.string.stremio_filter_catalog;
				case GENRE -> R.string.stremio_filter_genre;
				case MOVIES -> R.string.stremio_movies;
				case SERIES -> R.string.stremio_series;
				case ALL_GENRES -> R.string.stremio_all_genres;
				case ALL -> R.string.stremio_all;
				case SEASON -> R.string.stremio_season;
				case NO_DIRECT_STREAMS -> R.string.stremio_no_direct_streams;
				case PROVIDER -> R.string.stremio_filter_provider;
				case ALL_PROVIDERS -> R.string.stremio_all_providers;
				case SEE_ALL -> R.string.stremio_see_all;
				case SORT -> R.string.stremio_sort;
				case RATING -> R.string.stremio_rating;
				case RECENT -> R.string.stremio_sort_recent;
				case TITLE -> R.string.stremio_sort_title;
				case SAVED_MOVIES -> R.string.stremio_saved_movies;
				case SAVED_SERIES -> R.string.stremio_saved_series;
				case LIBRARY_EMPTY -> R.string.stremio_library_empty;
				case FAVORITE_UNAVAILABLE -> R.string.stremio_favorite_unavailable;
				case CATALOG_REQUIRES_INPUT -> R.string.stremio_catalog_requires_input;
			});
		}
	}
}
