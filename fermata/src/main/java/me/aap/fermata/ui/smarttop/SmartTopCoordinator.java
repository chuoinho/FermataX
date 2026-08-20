package me.aap.fermata.ui.smarttop;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.SmartTopCandidate;
import me.aap.fermata.addon.SmartTopProviderCoordinator;
import me.aap.fermata.addon.SmartTopProviderResult;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;
import me.aap.utils.async.FutureSupplier;

/**
 * Owns SmartTop refresh generations and state derivation. DashboardAdapter only renders states.
 * Provider-backed Resume candidates may join the display flow. Recommendation provider contracts
 * remain available for compatibility but are not selected or published by this coordinator.
 */
public final class SmartTopCoordinator implements AutoCloseable {
	public interface Listener {
		void onSmartTopState(SmartTopViewState state);

		default void onSmartTopTimeline(SmartTopViewState state) {
			onSmartTopState(state);
		}
	}

	private final MainActivityDelegate activity;
	private final Context context;
	private final Listener listener;
	private final SmartTopProviderCoordinator providers;
	private int refreshGeneration;
	private SmartTopLayoutMode layout;
	@Nullable
	private SmartTopViewState state;
	private boolean closed;

	public SmartTopCoordinator(MainActivityDelegate activity, Context context, Listener listener) {
		this.activity = Objects.requireNonNull(activity, "activity");
		this.context = Objects.requireNonNull(context, "context");
		this.listener = Objects.requireNonNull(listener, "listener");
		layout = SmartTopLayoutPolicy.resolve(estimateContentWidthDp(context, activity),
				context.getResources().getConfiguration().fontScale);
		providers = new SmartTopProviderCoordinator(AddonManager.get());
	}

	/** Estimates Dashboard content before RecyclerView's first measured layout. */
	static float estimateContentWidthDp(int screenWidthDp, int navRailWidthDp,
			int horizontalPaddingDp) {
		return Math.max(0F, screenWidthDp - navRailWidthDp - horizontalPaddingDp);
	}

	private static float estimateContentWidthDp(Context context, MainActivityDelegate activity) {
		float density = Math.max(0.1F, context.getResources().getDisplayMetrics().density);
		View nav = activity.getNavBar();
		int railWidthPx = nav.getWidth();
		if (railWidthPx <= 0) {
			ViewGroup.LayoutParams params = nav.getLayoutParams();
			if ((params != null) && (params.width > 0)) railWidthPx = params.width;
		}
		int railWidthDp = Math.round(Math.max(0, railWidthPx) / density);
		int paddingPx = context.getResources().getDimensionPixelSize(
				R.dimen.dashboard_content_padding) * 2;
		int horizontalPaddingDp = Math.round(paddingPx / density);
		return estimateContentWidthDp(
				context.getResources().getConfiguration().screenWidthDp,
				railWidthDp, horizontalPaddingDp);
	}

	public void setLayout(SmartTopLayoutMode nextLayout) {
		if (closed || (layout == nextLayout)) return;
		layout = nextLayout;
		SmartTopViewState current = state;
		if (current != null) publish(current.withLayout(nextLayout));
	}

	public SmartTopViewState initialState() {
		return empty(refreshGeneration, layout);
	}

	public void refresh() {
		if (closed) return;
		PlayableItem active = activity.getCurrentPlayable();
		if ((active != null) && refreshCurrentInPlace(active)) {
			// Playback state may be unchanged while the shared Recent collection has changed
			// underneath the same canonical item (for example YouTube navigation/autoplay).
			// Refresh the complete three-row snapshot, but publish only when its identities differ.
			loadQuickRecent(refreshGeneration, active);
			return;
		}

		int generation = ++refreshGeneration;
		if (active != null) {
			publishCurrent(generation, active);
			loadQuickRecent(generation, active);
			return;
		}
		loadProviderCandidates(generation);
	}

	/**
	 * Reuses the current generation while the canonical media item is unchanged. A pure
	 * play/pause/timeline change is published through the timeline payload so RecyclerView never
	 * rebinds geometry or temporarily drops Quick Recent.
	 */
	private boolean refreshCurrentInPlace(PlayableItem active) {
		SmartTopViewState current = state;
		if ((current == null) || (current.mode() != SmartTopMode.CURRENT) ||
				(current.canonicalItem() == null) || !samePlayable(current.canonicalItem(), active)) {
			return false;
		}

		PlaybackSnapshot snapshot = activity.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem snapshotItem = snapshot.getItem();
		CharSequence title = ((snapshotItem != null) && samePlayable(snapshotItem, active)) ?
				snapshot.getDisplayTitle() : active.getName();
		CharSequence subtitle = subtitle(active);
		boolean favoriteSupported = !active.isExternal();
		boolean favorite = favoriteSupported && active.isFavoriteItem();
		SmartTopCapabilities capabilities = SmartTopCapabilities.current(favoriteSupported, true);
		List<SmartTopAction> actions =
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, layout, capabilities);
		SmartTopTimeline timeline = activeTimeline(active);

		SmartTopViewState next = new SmartTopViewState(current.generation(), SmartTopMode.CURRENT,
				layout, active, PlayableItemResolver.unwrap(active), active.getIcon(),
				context.getString(R.string.dashboard_now_playing), title, subtitle, timeline,
				capabilities, actions, favorite, current.quickRecent(), null);
		boolean metadataStable = (current.icon() == next.icon()) &&
				TextUtils.equals(current.eyebrow(), next.eyebrow()) &&
				TextUtils.equals(current.title(), next.title()) &&
				TextUtils.equals(current.subtitle(), next.subtitle()) &&
				(current.favorite() == next.favorite()) && current.actions().equals(next.actions());
		boolean timelineChanged = !SmartTopTimelinePresentation.of(current.timeline()).equals(
				SmartTopTimelinePresentation.of(next.timeline()));

		if (metadataStable) {
			if (timelineChanged) {
				state = next;
				listener.onSmartTopTimeline(next);
			}
			return true;
		}

		publish(next);
		return true;
	}

	private void publishCurrent(int generation, PlayableItem active) {
		PlaybackSnapshot snapshot = activity.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem snapshotItem = snapshot.getItem();
		CharSequence title = ((snapshotItem != null) && samePlayable(snapshotItem, active)) ?
				snapshot.getDisplayTitle() : active.getName();
		boolean favoriteSupported = !active.isExternal();
		SmartTopCapabilities capabilities =
				SmartTopCapabilities.current(favoriteSupported, true);
		SmartTopTimeline timeline = activeTimeline(active);
		publish(new SmartTopViewState(generation, SmartTopMode.CURRENT, layout,
				active, PlayableItemResolver.unwrap(active), active.getIcon(),
				context.getString(R.string.dashboard_now_playing), title, subtitle(active), timeline,
				capabilities, SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, layout, capabilities),
				favoriteSupported && active.isFavoriteItem(), List.of(), null));
	}

	private void loadQuickRecent(int generation, PlayableItem active) {
		activity.getLib().getRecent().getChildren().main().onSuccess(items -> {
			if (!owns(generation) || !isCurrent(active)) return;
			SmartTopViewState current = state;
			if ((current == null) || (current.generation() != generation) ||
					(current.mode() != SmartTopMode.CURRENT)) return;
			List<PlayableItem> nextRecent = recent(items, active, SmartTopViewState.MAX_QUICK_RECENT);
			if (sameRecent(current.quickRecent(), nextRecent)) return;
			publish(current.withQuickRecent(nextRecent));
		});
	}

	private void loadProviderCandidates(int generation) {
		// Recent is local fallback data and is usually available before addon providers finish.
		// Publish it as soon as it resolves so a cold AA launch does not show an empty card while
		// providers are still inside their bounded load window. A later Resume candidate retains
		// its higher selection priority and replaces this provisional state.
		loadRecentPreview(generation);
		providers.loadCandidates().main().onCompletion((candidates, failure) -> {
			if (!owns(generation)) return;
			if (activity.getCurrentPlayable() != null) {
				refresh();
				return;
			}
			List<SmartTopProviderResult> available =
					(failure == null) && (candidates != null) ? candidates : List.of();
			SmartTopProviderResult resume = firstProvider(available, SmartTopCandidate.Kind.RESUME);
			if (resume != null) {
				publishResumeProvider(generation, resume);
				return;
			}
			loadRecent(generation);
		});
	}

	private void loadRecentPreview(int generation) {
		activity.getLib().getRecent().getUnsortedChildren().main().onSuccess(items -> {
			if (!owns(generation) || (activity.getCurrentPlayable() != null)) return;
			PlayableItem recent = firstPlayable(items);
			if (recent == null) return;
			SmartTopViewState current = state;
			if ((current != null) && (current.generation() == generation) &&
					(current.mode() != SmartTopMode.EMPTY)) return;
			publishItem(generation, SmartTopMode.RECENT, recent, SmartTopTimeline.HIDDEN);
		});
	}

	private void loadRecent(int generation) {
		activity.getLib().getRecent().getChildren().main().onCompletion((items, failure) -> {
			if (!owns(generation)) return;
			if (activity.getCurrentPlayable() != null) {
				refresh();
				return;
			}
			PlayableItem recent = (failure == null) ? firstPlayable(items) : null;
			if (recent != null) {
				publishItem(generation, SmartTopMode.RECENT, recent, SmartTopTimeline.HIDDEN);
				return;
			}
			loadLastPlayed(generation);
		});
	}

	private void loadLastPlayed(int generation) {
		activity.getLib().getLastPlayedItem().main().onCompletion((item, failure) -> {
			if (!owns(generation)) return;
			if ((failure != null) || (item == null)) {
				publish(empty(generation, layout));
				return;
			}
			long position = activity.getLib().getLastPlayedPosition(item);
			item.getDuration().main().onCompletion((duration, durationFailure) -> {
				if (!owns(generation)) return;
				long resolvedDuration = ((durationFailure == null) && (duration != null)) ? duration : 0L;
				boolean resume = SmartTopResumePolicy.isMeaningful(item.isLiveStream(),
						item.isSeekable(), position, resolvedDuration);
				SmartTopTimeline timeline = resume ? new SmartTopTimeline(
						PlaybackTimelinePolicy.Mode.SEEKABLE, position, resolvedDuration, false) :
						SmartTopTimeline.HIDDEN;
				publishItem(generation, resume ? SmartTopMode.RESUME : SmartTopMode.RECENT,
						item, timeline);
			});
		});
	}

	private void publishItem(int generation, SmartTopMode mode, PlayableItem item,
			SmartTopTimeline timeline) {
		boolean favoriteSupported = !item.isExternal();
		SmartTopCapabilities capabilities =
				SmartTopCapabilities.suggestion(favoriteSupported, true);
		CharSequence subtitle = subtitle(item);
		SmartTopViewState viewState = new SmartTopViewState(generation, mode, layout,
				item, PlayableItemResolver.unwrap(item), item.getIcon(), eyebrow(mode), item.getName(),
				subtitle, timeline, capabilities,
				SmartTopActionPolicy.resolve(mode, layout, capabilities),
				favoriteSupported && item.isFavoriteItem(), List.of(), null);
		publish(viewState);
		item.getMediaData().main().onSuccess(metadata -> {
			if (!owns(generation)) return;
			SmartTopViewState current = state;
			if ((current == null) || (current.generation() != generation) ||
					(current.presentedItem() != item)) return;
			CharSequence title = PlaybackSnapshot.resolveDisplayTitle(item, metadata);
			publish(current.withTitle(title));
		});
	}

	private SmartTopViewState empty(int generation, SmartTopLayoutMode layout) {
		SmartTopCapabilities capabilities = SmartTopCapabilities.NONE;
		return new SmartTopViewState(generation, SmartTopMode.EMPTY, layout,
				null, null, R.drawable.view_grid,
				context.getString(R.string.dashboard_smart_discover),
				context.getString(R.string.dashboard_smart_empty_title),
				context.getString(R.string.dashboard_smart_empty_subtitle),
				SmartTopTimeline.HIDDEN, capabilities,
				SmartTopActionPolicy.resolve(SmartTopMode.EMPTY, layout, capabilities),
				false, List.of(), null);
	}

	private void publishResumeProvider(int generation, SmartTopProviderResult providerResult) {
		SmartTopCandidate candidate = providerResult.candidate();
		if (candidate.kind() != SmartTopCandidate.Kind.RESUME) return;
		SmartTopCapabilities capabilities = SmartTopCapabilities.suggestion(false, true);
		SmartTopTimeline timeline = new SmartTopTimeline(
				PlaybackTimelinePolicy.Mode.SEEKABLE, candidate.positionMillis(),
				candidate.durationMillis(), false);
		publish(new SmartTopViewState(generation, SmartTopMode.RESUME, layout, null, null,
				candidate.video() ? R.drawable.video : R.drawable.audiotrack,
				eyebrow(SmartTopMode.RESUME), candidate.title(), candidate.subtitle(), timeline, capabilities,
				SmartTopActionPolicy.resolve(SmartTopMode.RESUME, layout, capabilities), false, List.of(),
				providerResult));
	}

	@Nullable
	private static SmartTopProviderResult firstProvider(List<SmartTopProviderResult> candidates,
			SmartTopCandidate.Kind kind) {
		for (SmartTopProviderResult candidate : candidates) {
			if (candidate.candidate().kind() == kind) return candidate;
		}
		return null;
	}

	public FutureSupplier<PlayableItem> resolveCandidate(SmartTopViewState requested) {
		if (!isCurrentState(requested) || (requested.providerResult() == null)) {
			return me.aap.utils.async.Completed.completedNull();
		}
		if (!(activity.getLib() instanceof DefaultMediaLib lib)) {
			return me.aap.utils.async.Completed.completedNull();
		}
		return providers.resolve(lib, requested.providerResult());
	}

	public boolean isCurrentState(SmartTopViewState requested) {
		return !closed && (state == requested) && (requested.generation() == refreshGeneration);
	}

	public void showRecovery(SmartTopViewState failed) {
		if (!isCurrentState(failed)) return;
		SmartTopCapabilities capabilities = SmartTopCapabilities.suggestion(false, true);
		publish(new SmartTopViewState(failed.generation(), SmartTopMode.RECOVERY, layout,
				null, null, R.drawable.refresh,
				context.getString(R.string.dashboard_smart_recovery),
				context.getString(R.string.dashboard_smart_recovery_title),
				context.getString(R.string.dashboard_smart_recovery_subtitle),
				SmartTopTimeline.HIDDEN, capabilities,
				SmartTopActionPolicy.resolve(SmartTopMode.RECOVERY, layout, capabilities),
				false, List.of(), failed.providerResult()));
	}

	public void onTimeline(PlaybackTimelineSnapshot timeline) {
		SmartTopViewState current = state;
		if (closed || (current == null) || (current.mode() != SmartTopMode.CURRENT) ||
				(current.canonicalItem() == null) ||
				!samePlayable(current.canonicalItem(), timeline.item())) return;
		SmartTopTimeline next = new SmartTopTimeline(timeline.mode(), timeline.positionMillis(),
				timeline.durationMillis(), timeline.playing());
		if (SmartTopTimelinePresentation.of(current.timeline()).equals(
				SmartTopTimelinePresentation.of(next))) return;
		state = current.withTimeline(next);
		listener.onSmartTopTimeline(state);
	}

	private SmartTopTimeline activeTimeline(PlayableItem active) {
		PlaybackTimelineSnapshot timeline =
				activity.getMediaServiceBinder().getPlaybackTimelineSnapshot();
		if ((timeline != null) && samePlayable(active, timeline.item())) {
			return new SmartTopTimeline(timeline.mode(), timeline.positionMillis(),
					timeline.durationMillis(), timeline.playing());
		}
		return new SmartTopTimeline(PlaybackTimelinePolicy.Mode.HIDDEN,
				0L, 0L, activity.getMediaServiceBinder().isPlaying());
	}

	private CharSequence eyebrow(SmartTopMode mode) {
		return switch (mode) {
			case CURRENT -> context.getString(R.string.dashboard_now_playing);
			case RESUME -> context.getString(R.string.dashboard_continue);
			case RECENT -> context.getString(R.string.recent);
			case EMPTY -> context.getString(R.string.dashboard_smart_discover);
			case RECOVERY -> context.getString(R.string.dashboard_smart_recovery);
			default -> throw new IllegalArgumentException("Compatibility-only SmartTop mode: " + mode);
		};
	}

	private void publish(SmartTopViewState next) {
		if (closed) return;
		state = next;
		listener.onSmartTopState(next);
	}

	private boolean owns(int generation) {
		return !closed && (generation == refreshGeneration);
	}

	private boolean isCurrent(PlayableItem item) {
		PlayableItem current = activity.getCurrentPlayable();
		return (current != null) && samePlayable(current, item);
	}

	private static boolean samePlayable(PlayableItem first, PlayableItem second) {
		first = PlayableItemResolver.unwrap(first);
		second = PlayableItemResolver.unwrap(second);
		return TextUtils.equals(first.getOrigId(), second.getOrigId()) ||
				TextUtils.equals(first.getId(), second.getId());
	}

	static boolean sameRecent(List<PlayableItem> first, List<PlayableItem> second) {
		if (first.size() != second.size()) return false;
		for (int i = 0; i < first.size(); i++) {
			if (!samePlayable(first.get(i), second.get(i))) return false;
		}
		return true;
	}

	@Nullable
	private static PlayableItem firstPlayable(@Nullable List<Item> items) {
		if (items == null) return null;
		for (Item item : items) if (item instanceof PlayableItem playable) return playable;
		return null;
	}

	private static List<PlayableItem> recent(@Nullable List<Item> items,
			@Nullable PlayableItem exclude, int limit) {
		if ((items == null) || (limit <= 0)) return List.of();
		List<PlayableItem> result = new ArrayList<>(limit);
		for (Item item : items) {
			if (!(item instanceof PlayableItem playable)) continue;
			if ((exclude != null) && samePlayable(exclude, playable)) continue;
			result.add(playable);
			if (result.size() == limit) break;
		}
		return List.copyOf(result);
	}

	private static CharSequence subtitle(PlayableItem item) {
		if (item.getParent() == null) return "";
		CharSequence subtitle = item.getParent().getName();
		return TextUtils.isEmpty(subtitle) ? "" : subtitle;
	}

	@Override
	public void close() {
		closed = true;
		refreshGeneration++;
		state = null;
	}
}
