package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_DOWN;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.voice.VoiceUiPolicy;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.MovableRecyclerViewAdapter;
import me.aap.utils.ui.view.ToolBarView;

public class DashboardFragment extends MainActivityFragment
		implements AddonManager.Listener, PreferenceStore.Listener, FermataServiceUiBinder.Listener {
	private DashboardAdapter adapter;
	private RecyclerView list;
	private GridLayoutManager layoutManager;
	private OneShotPreDrawListener viewportPreDraw;
	private final DashboardViewportState viewportState = new DashboardViewportState();
	private PreferenceStore prefs;
	private FermataServiceUiBinder binder;
	private boolean editMode;

	@Override
	public int getFragmentId() {
		return R.id.dashboard_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getResources().getString(R.string.dashboard);
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return DashboardToolBarMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return DashboardFloatingButtonMediator.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dashboard_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Context ctx = requireContext();
		MainActivityDelegate activity = MainActivityDelegate.get(ctx);
		Context localizedCtx = activity.getLocalizedContext(ctx);
		prefs = activity.getPrefs();
		RecyclerView list = view.findViewById(R.id.dashboard_list);
		this.list = list;
		DashboardAdapter dashboardAdapter = new DashboardAdapter(activity, localizedCtx, prefs);
		adapter = dashboardAdapter;
		int spanCount = getSpanCountForWidthDp(getFullWidthDp(ctx));
		GridLayoutManager layoutManager = new GridLayoutManager(ctx, spanCount);
		this.layoutManager = layoutManager;
		layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return dashboardAdapter.isWide(position) ? layoutManager.getSpanCount() : 1;
			}
		});
		list.setHasFixedSize(true);
		list.setLayoutManager(layoutManager);
		list.setAdapter(dashboardAdapter);
		list.addOnLayoutChangeListener((v, left, top, right, bottom,
				oldLeft, oldTop, oldRight, oldBottom) -> {
			if (viewportState.canApplyLayout(isStableDashboardViewport(true))) {
				updateSpanCount(right - left);
			}
		});
		new ItemTouchHelper(dashboardAdapter.getItemTouchCallback()).attachToRecyclerView(list);

		FermataApplication.get().getAddonManager().addBroadcastListener(this);
		prefs.addBroadcastListener(this);
		binder = activity.getMediaServiceBinder();
		binder.addBroadcastListener(this);
		list.post(dashboardAdapter::reload);
		list.postDelayed(dashboardAdapter::reload, 1200);
		requestStableViewport(false);
	}

	@Override
	public void onResume() {
		super.onResume();
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.refreshSmartTopCard();
	}

	@Override
	public void onPause() {
		setEditMode(false);
		super.onPause();
	}

	@Override
	public boolean onBackPressed() {
		if (editMode) {
			setEditMode(false);
			return true;
		}
		return super.onBackPressed();
	}

	@Override
	public void onDestroyView() {
		viewportState.invalidate();
		if (viewportPreDraw != null) {
			viewportPreDraw.removeListener();
			viewportPreDraw = null;
		}
		FermataApplication.get().getAddonManager().removeBroadcastListener(this);
		if (prefs != null) {
			prefs.removeBroadcastListener(this);
			prefs = null;
		}
		if (binder != null) {
			binder.removeBroadcastListener(this);
			binder = null;
		}
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.close();
		this.adapter = null;
		this.list = null;
		this.layoutManager = null;
		super.onDestroyView();
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		reload();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		DashboardAdapter adapter = this.adapter;
		if (adapter == null) return;
		if (prefs.contains(DashboardItems.PREF) && !adapter.isReordering()) {
			adapter.reload();
		} else {
			adapter.refreshDashboardSummaries();
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		refreshSmartTopCard();
	}

	@Override
	public void onPlaybackStateChanged(PlaybackStateCompat state) {
		refreshSmartTopCard();
	}

	@Override
	public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
		refreshSmartTopCard();
	}

	@Override
	public void onPlaybackStopped() {
		refreshSmartTopCard();
	}

	private void refreshSmartTopCard() {
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.refreshSmartTopCard();
	}

	public void reload() {
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.reload();
	}

	public void showHome() {
		refreshSmartTopCard();
		requestStableViewport(true);
	}

	private void toggleEditMode() {
		setEditMode(!editMode);
	}

	private void setEditMode(boolean editMode) {
		if (this.editMode == editMode) return;
		this.editMode = editMode;
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.setEditMode(editMode);
		RecyclerView list = this.list;
		if (list != null) updateSpanCount(list.getWidth());

		View view = getView();
		if (view == null) return;
		ImageButton edit = MainActivityDelegate.get(view.getContext()).getToolBar()
				.findViewById(R.id.dashboard_edit);
		if (edit == null) return;
		edit.setImageResource(editMode ? R.drawable.done : R.drawable.edit);
		edit.setContentDescription(getString(editMode ? R.string.done : R.string.edit_dashboard));
	}

	private void requestStableViewport(boolean scrollTop) {
		RecyclerView list = this.list;
		if (list == null) return;
		int generation = viewportState.beginTransition();
		if (viewportPreDraw != null) viewportPreDraw.removeListener();
		list.requestLayout();
		viewportPreDraw = OneShotPreDrawListener.add(list, () -> {
			viewportPreDraw = null;
			if (!viewportState.finishTransition(generation, isStableDashboardViewport(false))) return;
			updateSpanCount(list.getWidth());
			if (scrollTop && (layoutManager != null)) {
				layoutManager.scrollToPositionWithOffset(0, 0);
			}
		});
	}

	private boolean isStableDashboardViewport(boolean requireActive) {
		RecyclerView list = this.list;
		if (list == null) return false;
		MainActivityDelegate activity = MainActivityDelegate.get(list.getContext());
		return isStableDashboardViewport(list.isAttachedToWindow(),
				activity.getBody().isFrameMode(), requireActive,
				activity.getActiveFragment() == this);
	}

	static boolean isStableDashboardViewport(boolean attached, boolean frameMode,
			boolean requireActive, boolean active) {
		return attached && frameMode && (!requireActive || active);
	}

	private void updateSpanCount(int width) {
		RecyclerView list = this.list;
		GridLayoutManager layoutManager = this.layoutManager;
		if ((list == null) || (layoutManager == null) || (width <= 0)) return;
		int spans = getSpanCount(width, list.getRootView().getWidth(),
				getFullWidthDp(list.getContext()),
				list.getResources().getDisplayMetrics().density);
		if (editMode) spans = getEditSpanCount(spans);
		if (spans != layoutManager.getSpanCount()) {
			layoutManager.setSpanCount(spans);
			layoutManager.getSpanSizeLookup().invalidateSpanIndexCache();
			list.requestLayout();
		}
	}

	static final class DashboardViewportState {
		private int generation;
		private boolean transitionPending;

		int beginTransition() {
			transitionPending = true;
			return ++generation;
		}

		boolean canApplyLayout(boolean ready) {
			return ready && !transitionPending;
		}

		boolean finishTransition(int token, boolean ready) {
			if ((token != generation) || !transitionPending) return false;
			transitionPending = false;
			return ready;
		}

		void invalidate() {
			generation++;
			transitionPending = false;
		}
	}

	private static int getFullWidthDp(Context ctx) {
		var resources = ctx.getResources();
		var dm = resources.getDisplayMetrics();
		return getFullWidthDp(resources.getConfiguration().screenWidthDp,
				dm.widthPixels, dm.density);
	}

	static int getFullWidthDp(int configurationWidthDp, int displayWidthPx, float density) {
		int displayWidthDp = Math.round(displayWidthPx / Math.max(0.1F, density));
		return Math.max(configurationWidthDp, displayWidthDp);
	}

	static int getSpanCount(int widthPx, int rootWidthPx, int screenWidthDp, float density) {
		float widthDp = ((rootWidthPx > 0) && (screenWidthDp > 0)) ?
				(widthPx * (float) screenWidthDp / rootWidthPx) :
				(widthPx / Math.max(0.1F, density));
		return getSpanCountForWidthDp(widthDp);
	}

	static int getSpanCountForWidthDp(float widthDp) {
		if (widthDp < 460F) return 1;
		int spans = (int) (widthDp / 220F);
		return Math.max(2, Math.min(4, spans));
	}

	static int getEditSpanCount(int normalSpanCount) {
		return Math.max(1, Math.min(2, normalSpanCount));
	}

	static int getMoveTarget(int from, int direction, int itemCount, IntPredicate fixed) {
		if ((from < 0) || (from >= itemCount) || (direction == 0) || fixed.test(from)) return -1;
		int target = from + Integer.signum(direction);
		if ((target < 0) || (target >= itemCount) || fixed.test(target)) return -1;
		return target;
	}

	private static final class DashboardAdapter extends MovableRecyclerViewAdapter<ItemHolder> {
		private static final int VIEW_TYPE_CARD = 0;
		private static final int VIEW_TYPE_SMART_TOP = 1;
		private final Context ctx;
		private final MainActivityDelegate activity;
		private final PreferenceStore store;
		private final DashboardModelBuilder modelBuilder;
		private final List<DashboardCard> cards = new ArrayList<>();
		private long ignoreClicksUntil;
		private int smartRefreshGeneration;
		private boolean closed;
		private boolean editMode;
		private boolean manualReorder;

		private DashboardAdapter(MainActivityDelegate activity, Context ctx, PreferenceStore store) {
			this.activity = activity;
			this.ctx = ctx;
			this.store = store;
			modelBuilder = new DashboardModelBuilder(ctx, store);
			reload();
		}

		private void reload() {
			if (closed) return;
			int pos = findSmartTopCardPosition();
			DashboardCard smartTopCard = (pos == -1) ? null : cards.get(pos);
			rebuildCards(smartTopCard);
			notifyDataSetChanged();
			refreshDashboardSummaries();
			refreshSmartTopCard();
		}

		private void rebuildCards(@Nullable DashboardCard smartTopCard) {
			modelBuilder.rebuild(cards, smartTopCard);
		}

		private void close() {
			closed = true;
		}

		private void setEditMode(boolean editMode) {
			if (this.editMode == editMode) return;
			this.editMode = editMode;
			notifyDataSetChanged();
		}

		private boolean isReordering() {
			return isCallbackCall() || manualReorder;
		}

		@NonNull
		@Override
		public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			int layout = (viewType == VIEW_TYPE_SMART_TOP) ?
					R.layout.dashboard_smart_top_item : R.layout.dashboard_item;
			View v = LayoutInflater.from(parent.getContext())
					.inflate(layout, parent, false);
			return new ItemHolder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
			DashboardCard card = cards.get(position);
			boolean smartTop = card.fixed && card.wide;
			boolean editable = editMode && !card.fixed;
			holder.icon.setImageResource(card.icon);
			if (holder.eyebrow != null) {
				boolean showEyebrow = smartTop && (card.playable != null);
				holder.eyebrow.setVisibility(showEyebrow ? View.VISIBLE : View.GONE);
				if (showEyebrow) {
					holder.eyebrow.setText(card.playing ?
							R.string.dashboard_now_playing : R.string.dashboard_continue);
				}
			}
			holder.title.setText(card.title);
			holder.subtitle.setText(card.subtitle);
			holder.subtitle.setVisibility(TextUtils.isEmpty(card.subtitle) ? View.GONE : View.VISIBLE);
			boolean favoriteSupported = (card.playable != null) && !card.playable.isExternal();
			boolean favorite = favoriteSupported && card.playable.isFavoriteItem();
			holder.actions.setVisibility(!editMode && (card.playable != null) && card.wide ?
					View.VISIBLE : View.GONE);
			holder.playPause.setImageResource(card.playing ? R.drawable.pause : R.drawable.play);
			holder.playPause.setContentDescription(ctx.getString(card.playing ? R.string.pause : R.string.play));
			holder.favorite.setVisibility(favoriteSupported ? View.VISIBLE : View.GONE);
			holder.favorite.setImageResource(favorite ? R.drawable.favorite_filled : R.drawable.favorite);
			holder.favorite.setContentDescription(ctx.getString(favorite ?
					R.string.favorites_remove : R.string.favorites_add));
			bindSmartTop(holder, card, smartTop);
			bindEditActions(holder, card, editable);
			holder.itemView.setOnClickListener(v -> {
				if (editMode) {
					focusEditAction(holder);
					return;
				}
				if (!acceptClick()) return;
				MainActivityDelegate a = activity;
				if (card.playable != null) {
					DashboardPlayableNavigator.openSmartTop(a, card.playable);
					return;
				}

				if (card.targetId == ID_NULL) return;
				a.setActiveNavItemId(R.id.dashboard_fragment);
				a.showFragmentWhenReady(card.targetId);
			});
			holder.playPause.setOnClickListener(v -> {
				if (editMode || !acceptClick() || (card.playable == null)) return;
				DashboardPlayableNavigator.togglePlayback(activity, card.playable);
				refreshSmartTopCard();
			});
			if (holder.prev != null) {
				holder.prev.setOnClickListener(v -> {
					if (editMode || !acceptClick() || (card.playable == null)) return;
					activity.getMediaSessionCallback().onSkipToPrevious();
					refreshSmartTopCard();
				});
			}
			if (holder.next != null) {
				holder.next.setOnClickListener(v -> {
					if (editMode || !acceptClick() || (card.playable == null)) return;
					activity.getMediaSessionCallback().onSkipToNext();
					refreshSmartTopCard();
				});
			}
			holder.favorite.setOnClickListener(v -> {
				if (editMode || !acceptClick() || (card.playable == null) || card.playable.isExternal()) return;
				if (card.playable.isFavoriteItem()) {
					card.playable.getLib().getFavorites().removeItem(card.playable)
							.main().onSuccess(done -> refreshSmartTopCard());
				} else {
					card.playable.getLib().getFavorites().addItem(card.playable)
							.main().onSuccess(done -> refreshSmartTopCard());
				}
			});
			holder.backToList.setOnClickListener(v -> {
				if (editMode || !acceptClick() || (card.playable == null)) return;
				DashboardPlayableNavigator.goToPlayable(activity, card.playable);
			});
		}

		private void bindEditActions(ItemHolder holder, DashboardCard card, boolean editable) {
			if (holder.editActions == null) return;
			holder.editActions.setVisibility(editable ? View.VISIBLE : View.GONE);
			if (!editable) return;

			int from = cards.indexOf(card);
			int earlier = getMoveTarget(from, -1, cards.size(), i -> cards.get(i).fixed);
			int later = getMoveTarget(from, 1, cards.size(), i -> cards.get(i).fixed);
			setMoveActionEnabled(holder.moveEarlier, earlier != -1);
			setMoveActionEnabled(holder.moveLater, later != -1);
			holder.moveEarlier.setOnClickListener(v -> moveCard(card, -1));
			holder.moveLater.setOnClickListener(v -> moveCard(card, 1));
		}

		private static void setMoveActionEnabled(View action, boolean enabled) {
			action.setEnabled(enabled);
			action.setAlpha(enabled ? 1F : 0.32F);
		}

		private static void focusEditAction(ItemHolder holder) {
			if ((holder.moveEarlier != null) && holder.moveEarlier.isEnabled()) {
				holder.moveEarlier.requestFocus();
			} else if ((holder.moveLater != null) && holder.moveLater.isEnabled()) {
				holder.moveLater.requestFocus();
			}
		}

		private void moveCard(DashboardCard card, int direction) {
			int from = cards.indexOf(card);
			int target = getMoveTarget(from, direction, cards.size(), i -> cards.get(i).fixed);
			if (target == -1) return;

			manualReorder = true;
			boolean moved;
			try {
				moved = onItemMove(from, target);
			} finally {
				manualReorder = false;
			}
			if (!moved) return;
			notifyItemMoved(from, target);
			notifyItemRangeChanged(Math.min(from, target), Math.abs(from - target) + 1);
		}

		@Override
		public int getItemViewType(int position) {
			DashboardCard card = cards.get(position);
			return card.fixed && card.wide ? VIEW_TYPE_SMART_TOP : VIEW_TYPE_CARD;
		}

		@Override
		public int getItemCount() {
			return cards.size();
		}

		private boolean isWide(int position) {
			return (position >= 0) && (position < cards.size()) && cards.get(position).wide;
		}

		private boolean acceptClick() {
			long now = SystemClock.uptimeMillis();
			if (now < ignoreClicksUntil) return false;
			ignoreClicksUntil = now + 350;
			return true;
		}

		private void refreshSmartTopCard() {
			if (closed) return;
			int generation = ++smartRefreshGeneration;

			MainActivityDelegate a = activity;
			a.getLib().getRecent().getChildren().main().onSuccess(items -> {
				if (!isSmartRefreshActive(generation)) return;
				PlayableItem active = activity.getCurrentPlayable();
				if (active != null) {
					PlaybackSnapshot snapshot = a.getMediaSessionCallback().getPlaybackSnapshot();
					PlayableItem snapshotItem = snapshot.getItem();
					CharSequence title = (snapshotItem != null) &&
							DashboardPlayableNavigator.isSamePlayable(snapshotItem, active)
							? snapshot.getDisplayTitle() : active.getName();
					setSmartTopCard(DashboardCard.playable(active, title,
							a.getMediaServiceBinder().isPlaying(), getRecentPlayables(items, active)), generation);
				} else {
					PlayableItem recent = getFirstPlayable(items);
					if (recent != null) {
						setPlayableTopCard(recent, false, getRecentPlayables(items, recent), generation);
					} else {
						refreshLastPlayedTopCard(generation);
					}
				}
			}).onFailure(err -> refreshLastPlayedTopCard(generation));
		}

		private void refreshLastPlayedTopCard(int generation) {
			if (!isSmartRefreshActive(generation)) return;
			activity.getLib().getLastPlayedItem().main().onSuccess(item -> {
				if (!isSmartRefreshActive(generation)) return;
				if (activity.getCurrentPlayable() != null) {
					refreshSmartTopCard();
					return;
				}
				if (item != null) setPlayableTopCard(item, false, null, generation);
				else refreshRecentTopCard(generation);
			}).onFailure(err -> refreshRecentTopCard(generation));
		}

		@Nullable
		private PlayableItem getFirstPlayable(List<Item> items) {
			for (Item item : items) {
				if (item instanceof PlayableItem) return (PlayableItem) item;
			}
			return null;
		}

		private void refreshRecentTopCard(int generation) {
			if (!isSmartRefreshActive(generation)) return;
			activity.getLib().getRecent().getChildren().main().onSuccess(items -> {
				if (!isSmartRefreshActive(generation)) return;
				if (activity.getCurrentPlayable() != null) {
					refreshSmartTopCard();
					return;
				}
				setSmartTopCard(DashboardCard.recent(ctx, items), generation);
			}).onFailure(err -> setSmartTopCard(null, generation));
		}

		private boolean isSmartRefreshActive(int generation) {
			return !closed && (generation == smartRefreshGeneration);
		}

		private void setPlayableTopCard(PlayableItem item, boolean playing,
				@Nullable List<PlayableItem> recentItems, int generation) {
			item.getMediaData().main().onSuccess(metadata -> {
				if (!isSmartRefreshActive(generation)) return;
				setSmartTopCard(DashboardCard.playable(item,
						PlaybackSnapshot.resolveDisplayTitle(item, metadata), playing, recentItems), generation);
			}).onFailure(err -> setSmartTopCard(
					DashboardCard.playable(item, playing, recentItems), generation));
		}

		private int findSmartTopCardPosition() {
			for (int i = 0; i < cards.size(); i++) {
				if (cards.get(i).fixed) return i;
			}
			return -1;
		}

		private void setSmartTopCard(DashboardCard card, int generation) {
			if (!isSmartRefreshActive(generation)) return;
			rebuildCards(card);
			notifyDataSetChanged();
			refreshDashboardSummaries();
		}

		private void refreshDashboardSummaries() {
			if (closed) return;
			activity.getLib().getFavorites().getChildren().main().onSuccess(items ->
					updateCardSubtitle(R.id.favorites_fragment, DashboardCard.itemSummary(items,
							ctx.getString(R.string.dashboard_favorites_sub))));
			activity.getLib().getRecent().getChildren().main().onSuccess(items ->
					updateCardSubtitle(R.id.recent_fragment, DashboardCard.itemSummary(items,
							ctx.getString(R.string.dashboard_recent_sub))));
		}

		private void updateCardSubtitle(int targetId, CharSequence subtitle) {
			if (closed) return;
			for (int i = 0; i < cards.size(); i++) {
				DashboardCard card = cards.get(i);
				if (card.fixed || (card.targetId != targetId) ||
						TextUtils.equals(card.subtitle, subtitle)) continue;
				cards.set(i, card.withSubtitle(subtitle));
				notifyItemChanged(i);
				return;
			}
		}

		private List<PlayableItem> getRecentPlayables(List<Item> items, @Nullable PlayableItem exclude) {
			List<PlayableItem> recent = new ArrayList<>(3);
			for (Item item : items) {
				if (!(item instanceof PlayableItem playable)) continue;
				if ((exclude != null) && DashboardPlayableNavigator.isSamePlayable(exclude, playable)) continue;
				recent.add(playable);
				if (recent.size() == 3) break;
			}
			return recent;
		}

		private void bindSmartTop(ItemHolder holder, DashboardCard card, boolean smartTop) {
			if (holder.recentPanel == null) return;
			holder.recentPanel.setVisibility(smartTop ? View.VISIBLE : View.GONE);
			holder.recentPanel.setOnClickListener(v -> {
				if (editMode || !acceptClick()) return;
				activity.setActiveNavItemId(R.id.dashboard_fragment);
				activity.showFragment(R.id.recent_fragment);
			});
			TextView[] views = holder.recentItems;
			List<PlayableItem> items = card.recentItems;

			for (int i = 0; i < views.length; i++) {
				TextView view = views[i];
				if (view == null) continue;
				if ((items == null) || (i >= items.size())) {
					view.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
					view.setText(i == 0 ? ctx.getString(R.string.dashboard_recent_sub) : "");
					view.setOnClickListener(null);
					view.setClickable(false);
					view.setFocusable(false);
					continue;
				}

				PlayableItem item = items.get(i);
				view.setVisibility(View.VISIBLE);
				view.setText(item.getName());
				view.setTag(item);
				item.getMediaData().main().onSuccess(metadata -> {
					if (!closed && (view.getTag() == item)) {
						view.setText(PlaybackSnapshot.resolveDisplayTitle(item, metadata));
					}
				});
				view.setClickable(true);
				view.setFocusable(true);
				view.setOnClickListener(v -> {
					if (editMode || !acceptClick()) return;
					DashboardPlayableNavigator.playAndGoToPlayable(activity, item);
				});
			}
		}

		@Override
		protected void onItemDismiss(int position) {
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			if ((fromPosition < 0) || (toPosition < 0) ||
					(fromPosition >= cards.size()) || (toPosition >= cards.size())) {
				return false;
			}

			if (cards.get(fromPosition).fixed || cards.get(toPosition).fixed) {
				return false;
			}

			move(cards, fromPosition, toPosition);
			ignoreClicksUntil = SystemClock.uptimeMillis() + 500;
			DashboardItems.setDashboardOrder(store, getDashboardItems());
			return true;
		}

		@Override
		protected boolean isItemViewSwipeEnabled() {
			return false;
		}

		@Override
		protected boolean isLongPressDragEnabled() {
			return !editMode;
		}

		private List<DashboardItems.Item> getDashboardItems() {
			return modelBuilder.getDashboardItems(cards);
		}
	}

	private static final class ItemHolder extends RecyclerView.ViewHolder {
		final ImageView icon;
		final TextView eyebrow;
		final TextView title;
		final TextView subtitle;
		final View actions;
		final ImageButton playPause;
		final ImageButton favorite;
		final ImageButton backToList;
		final ImageButton prev;
		final ImageButton next;
		final View recentPanel;
		final TextView[] recentItems;
		final View editActions;
		final ImageButton moveEarlier;
		final ImageButton moveLater;

		private ItemHolder(@NonNull View itemView) {
			super(itemView);
			icon = itemView.findViewById(R.id.dashboard_item_icon);
			eyebrow = itemView.findViewById(R.id.dashboard_item_eyebrow);
			title = itemView.findViewById(R.id.dashboard_item_title);
			subtitle = itemView.findViewById(R.id.dashboard_item_subtitle);
			actions = itemView.findViewById(R.id.dashboard_item_actions);
			playPause = itemView.findViewById(R.id.dashboard_action_play_pause);
			favorite = itemView.findViewById(R.id.dashboard_action_favorite);
			backToList = itemView.findViewById(R.id.dashboard_action_back_to_list);
			prev = itemView.findViewById(R.id.dashboard_action_prev);
			next = itemView.findViewById(R.id.dashboard_action_next);
			recentPanel = itemView.findViewById(R.id.dashboard_recent_panel);
			recentItems = new TextView[]{
					itemView.findViewById(R.id.dashboard_recent_item_1),
					itemView.findViewById(R.id.dashboard_recent_item_2),
					itemView.findViewById(R.id.dashboard_recent_item_3)
			};
			editActions = itemView.findViewById(R.id.dashboard_item_edit_actions);
			moveEarlier = itemView.findViewById(R.id.dashboard_action_move_earlier);
			moveLater = itemView.findViewById(R.id.dashboard_action_move_later);
		}
	}

	private static final class DashboardToolBarMediator implements ToolBarView.Mediator.BackTitle {
		static final DashboardToolBarMediator instance = new DashboardToolBarMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			ToolBarView.Mediator.BackTitle.super.enable(tb, f);
			DashboardFragment dashboard = (DashboardFragment) f;
			ImageButton voice = addButton(tb, R.drawable.voice_microphone,
					DashboardToolBarMediator::onVoiceClick, R.id.tool_voice);
			voice.setContentDescription(tb.getContext().getString(R.string.action_activate_voice_ctrl));
			ImageButton edit = addButton(tb, dashboard.editMode ? R.drawable.done : R.drawable.edit,
					DashboardToolBarMediator::onEditClick, R.id.dashboard_edit);
			edit.setContentDescription(tb.getContext().getString(dashboard.editMode ?
					R.string.done : R.string.edit_dashboard));
			updateVoiceVisibility(tb);
			ImageButton settings = addButton(tb, R.drawable.settings_gear, DashboardToolBarMediator::onSettingsClick,
					R.id.dashboard_settings);
			settings.setBackgroundResource(R.drawable.aa_toolbar_primary_button_bg);
			settings.setContentDescription(tb.getContext().getString(R.string.settings));
		}

		@Override
		public void onActivityEvent(ToolBarView tb, ActivityDelegate activity, long event) {
			ToolBarView.Mediator.BackTitle.super.onActivityEvent(tb, activity, event);
			if (event == FRAGMENT_CONTENT_CHANGED) updateVoiceVisibility(tb);
		}

		private static void onEditClick(View v) {
			ActivityFragment fragment = ActivityDelegate.get(v.getContext()).getActiveFragment();
			if (fragment instanceof DashboardFragment dashboard) dashboard.toggleEditMode();
		}

		private static void onSettingsClick(View v) {
			MainActivityDelegate activity = MainActivityDelegate.get(v.getContext());
			ActivityFragment fragment = activity.getActiveFragment();
			if (fragment instanceof DashboardFragment dashboard) dashboard.setEditMode(false);
			activity.showFragment(R.id.settings_fragment);
		}

		private static void onVoiceClick(View v) {
			MainActivityDelegate.get(v.getContext()).startVoiceAssistant();
		}

		private static void updateVoiceVisibility(ToolBarView tb) {
			View voice = tb.findViewById(R.id.tool_voice);
			if (voice != null) voice.setVisibility(VoiceUiPolicy.showToolbarButton(
					MainActivityDelegate.get(tb.getContext())) ? View.VISIBLE : View.GONE);
		}

		@Override
		public boolean onBackPressed(ToolBarView tb) {
			ActivityFragment fragment = tb.getActiveFragment();
			if (!(fragment instanceof DashboardFragment dashboard) || !dashboard.editMode) return false;
			dashboard.setEditMode(false);
			return true;
		}

		@Nullable
		@Override
		public View focusSearch(ToolBarView tb, View focused, int direction) {
			View v = ToolBarView.Mediator.BackTitle.super.focusSearch(tb, focused, direction);
			if ((v != null) || (direction != FOCUS_DOWN)) return v;
			ActivityDelegate a = ActivityDelegate.get(tb.getContext());
			View root = a.getActiveFragment() == null ? null : a.getActiveFragment().getView();
			return root instanceof RecyclerView ? ((RecyclerView) root).getChildAt(0) : null;
		}

		@Override
		public int getBackButtonVisibility(ActivityFragment f) {
			return View.GONE;
		}

		@Override
		public int getBackButtonId() {
			return me.aap.utils.R.id.tool_bar_back_button;
		}
	}

	private static final class DashboardFloatingButtonMediator implements FloatingButton.Mediator {
		static final DashboardFloatingButtonMediator instance = new DashboardFloatingButtonMediator();

		@Override
		public void enable(FloatingButton fb, ActivityFragment f) {
			fb.setVisibility(View.GONE);
			FloatingButton.Mediator.super.disable(fb);
		}

		@Override
		public void disable(FloatingButton fb) {
			FloatingButton.Mediator.super.disable(fb);
			fb.setVisibility(View.VISIBLE);
		}
	}
}
