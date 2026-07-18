package me.aap.fermata.addon.tv;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.addon.tv.xtream.XtreamFileSystemProvider;
import me.aap.fermata.addon.tv.xtream.XtreamSourceItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.RefreshCoordinator.Result;
import me.aap.fermata.media.lib.RefreshCoordinator.Status;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public class TvFragment extends MediaLibFragment {
	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new TvAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_tv);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.tv_fragment;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return TvFloatingButtonMediator.instance;
	}

	@Override
	public boolean isAddSourceSupported() {
		return isRootItem();
	}

	@Override
	public int getAddSourceIcon() {
		return R.drawable.tv_add;
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) return;

		TvAdapter a = getAdapter();
		if (a != null) {
			a.animateAddButton(a.getParent());
			autoReloadSources(a);
		}
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	public void addSource() {
		getMainActivity().getContextMenu().show(b -> {
			b.setTitle(R.string.add_tv_source);
			b.setSelectionHandler(this::sourceTypeSelected);
			b.addItem(me.aap.fermata.R.id.m3u_playlist, me.aap.fermata.R.drawable.m3u,
					me.aap.fermata.R.string.m3u_playlist);
			b.addItem(me.aap.fermata.R.id.xtream_source, me.aap.fermata.R.drawable.tv,
					R.string.xtream_codes);
		});
	}

	private boolean sourceTypeSelected(OverlayMenuItem item) {
		int id = item.getItemId();
		if (id == me.aap.fermata.R.id.m3u_playlist) {
			addM3uSource();
		} else if (id == me.aap.fermata.R.id.xtream_source) {
			addXtreamSource();
		}
		return true;
	}

	private void addM3uSource() {
		TvM3uFileSystemProvider prov = new TvM3uFileSystemProvider();
		prov.select(getMainActivity(), Collections.singletonList(TvM3uFileSystem.getInstance())).main()
				.onFailure(this::failedToAddSource).onSuccess(this::addM3uSource);
	}

	private void addXtreamSource() {
		new XtreamFileSystemProvider().select(getMainActivity()).main()
				.onFailure(this::failedToAddSource).onSuccess(this::addXtreamSource);
	}

	public TvRootItem getRootItem() {
		return getAddon().getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	private TvAddon getAddon() {
		return requireNonNull(AddonManager.get().getAddon(TvAddon.class));
	}

	private TvSourceRefreshCoordinator getRefreshCoordinator() {
		return getAddon().getRefreshCoordinator();
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, MediaItemMenuHandler h) {
		if (!(h.getItem() instanceof TvSourceItem)) return;
		b.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
						me.aap.fermata.R.string.refresh).setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		b.addItem(me.aap.fermata.R.id.edit, me.aap.fermata.R.drawable.edit,
						me.aap.fermata.R.string.edit).setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		b.addItem(me.aap.fermata.R.id.delete, me.aap.fermata.R.drawable.delete,
						me.aap.fermata.R.string.delete).setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		super.contributeToContextMenu(b, h);
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		int id = item.getItemId();
		if (id == me.aap.fermata.R.id.refresh) {
			reloadSource(item.getData(), true);
		} else if (id == me.aap.fermata.R.id.edit) {
			TvSourceItem source = item.getData();

			if (source instanceof TvM3uItem) {
				TvM3uItem i = (TvM3uItem) source;
				new TvM3uFileSystemProvider().edit(getMainActivity(), i.getResource())
						.onCompletion((ok, err) -> {
							if ((err != null) && !(err instanceof CancellationException)) {
								Log.e(err, "Failed to edit TV source ", i);
								UiUtils.showAlert(getContext(), err.getLocalizedMessage());
							}
							getMainActivity().showFragment(getFragmentId());
							if ((ok != null) && ok) refreshEditedSource(i);
						});
			} else if (source instanceof XtreamSourceItem) {
				XtreamSourceItem i = (XtreamSourceItem) source;
				new XtreamFileSystemProvider().edit(getMainActivity(), i.getAccount())
						.onCompletion((account, err) -> {
							if ((err != null) && !(err instanceof CancellationException)) {
								Log.e(err, "Failed to edit TV source ", i);
								UiUtils.showAlert(getContext(), err.getLocalizedMessage());
							}
							getMainActivity().showFragment(getFragmentId());
							if (account != null) {
								try {
									getRootItem().updateSource(account);
								} catch (RuntimeException ex) {
									failedToAddSource(ex);
									return;
								}
								i.setAccount(account);
								refreshEditedSource(i);
							}
						});
			}
		} else if (id == me.aap.fermata.R.id.delete) {
			TvRootItem root = getRootItem();
			root.removeItem(item.getData()).onSuccess(v -> getAdapter().setParent(root));
		}
		return true;
	}

	private FutureSupplier<?> reloadSource(TvSourceItem source, boolean showError) {
		return getRefreshCoordinator().manual(source).main()
				.onSuccess(result -> renderRefreshResult(source, result, showError, true));
	}

	private void autoReloadSources(TvAdapter adapter) {
		if (!(adapter.getParent() instanceof TvRootItem)) return;

		getRootItem().getUnsortedChildren().onCompletion((children, err) -> {
			if ((err != null) || (children == null)) {
				if (err != null) Log.e(err, "Failed to load TV sources for auto reload");
				return;
			}

			AtomicBoolean refreshAttempted = new AtomicBoolean();
			Async.forEach(child -> (child instanceof TvSourceItem) ?
					getRefreshCoordinator().auto((TvSourceItem) child)
							.onSuccess(result -> {
								Status status = result.status();
								if ((status != Status.SKIPPED_COOLDOWN) && (status != Status.INACTIVE))
									refreshAttempted.set(true);
								renderRefreshResult((TvSourceItem) child, result, false, false);
							}) : completedVoid(), children).main()
					.onCompletion((done, fail) -> {
						TvAdapter a = getAdapter();
						if (refreshAttempted.get() && (getView() != null) && (a != null) &&
								(a.getParent() instanceof TvRootItem)) reload();
					});
		});
	}

	private void refreshEditedSource(TvSourceItem source) {
		getRootItem().invalidateSearch();
		getRefreshCoordinator().edited(source).main().onSuccess(result -> {
			renderRefreshResult(source, result, false, false);
			if (getView() != null) refresh();
		});
	}

	private void renderRefreshResult(TvSourceItem source, Result<String> result,
														 boolean showError, boolean reloadList) {
		if (reloadList && (getView() != null)) reload();
		if (result.isSuccess()) getRootItem().invalidateSearch();
		if (!result.isFailure()) return;

		Throwable err = result.error();
		Log.e(err, "Failed to reload TV source ", source);
		if (!showError || (err == null) || isCancellation(err) || (getContext() == null)) return;
		String msg = err.getLocalizedMessage();
		UiUtils.showAlert(getContext(), (msg != null) ? msg : err.toString());
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);
		if (isRootItem()) return;
		TvAdapter a = getAdapter();

		if (a.getListView().isSelectionActive() && a.hasSelectable() && a.hasSelected()) {
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(me.aap.fermata.R.id.favorites_add, me.aap.fermata.R.drawable.favorite,
					me.aap.fermata.R.string.favorites_add);
			getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
		}
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getRootItem().isChildItemId(i.getId());
	}

	@Override
	protected boolean isRefreshSupported() {
		return true;
	}

	@Override
	public FutureSupplier<?> refresh() {
		BrowsableItem parent = getAdapter().getParent();
		XtreamSourceItem source = getXtreamSource(parent);
		if ((source != null) && (source != parent)) source.clearCache();
		return super.refresh();
	}

	private void addM3uSource(TvM3uFile m3u) {
		MainActivityDelegate a = getMainActivity();
		if (m3u != null) getRootItem().addSource(m3u);
		getAdapter().setParent(getRootItem());
		a.showFragment(getFragmentId());
	}

	private void addXtreamSource(XtreamAccount account) {
		MainActivityDelegate a = getMainActivity();
		if (account != null) {
			try {
				getRootItem().addSource(account);
			} catch (RuntimeException ex) {
				failedToAddSource(ex);
				return;
			}
		}
		getAdapter().setParent(getRootItem());
		a.showFragment(getFragmentId());
	}

	private void failedToAddSource(Throwable ex) {
		getMainActivity().showFragment(me.aap.fermata.R.id.tv_fragment);
		if (isCancellation(ex)) return;

		App.get().getHandler().post(() -> {
			String msg = ex.getLocalizedMessage();
			UiUtils.showAlert(getContext(),
					getString(R.string.err_failed_to_add_tv_source, (msg != null) ? msg : ex.toString()));
		});
	}

	private boolean isRootItem() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) || (p instanceof TvRootItem);
	}

	private XtreamSourceItem getXtreamSource(BrowsableItem item) {
		for (BrowsableItem i = item; i != null; i = i.getParent()) {
			if (i instanceof XtreamSourceItem) return (XtreamSourceItem) i;
		}
		return null;
	}

	private class TvAdapter extends ListAdapter {

		TvAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
			animateAddButton(parent);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return super.setParent(parent, userAction).onSuccess(v -> animateAddButton(parent));
		}

		public boolean isLongPressDragEnabled() {
			return isRootItem();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof TvRootItem) ((TvRootItem) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof MediaLib.Folders) ((MediaLib.Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		private void animateAddButton(BrowsableItem parent) {
			if (!(parent instanceof TvRootItem)) return;

			parent.getUnsortedChildren().onSuccess(c -> {
				if (!c.isEmpty()) return;

				FloatingButton fb = getMainActivity().getFloatingButton();
				fb.requestFocus();
				Animation shake =
						AnimationUtils.loadAnimation(getContext(), me.aap.utils.R.anim.shake_y_20);
				fb.startAnimation(shake);
			});
		}
	}
}
