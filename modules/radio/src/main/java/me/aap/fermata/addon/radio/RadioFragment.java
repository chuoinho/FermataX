package me.aap.fermata.addon.radio;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.RefreshCoordinator.Result;
import me.aap.fermata.media.lib.RefreshCoordinator.Status;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

public class RadioFragment extends MediaLibFragment {
	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new ListAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_radio);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.radio_fragment;
	}

	@Override
	public boolean isAddSourceSupported() {
		return isRootItem();
	}

	@Override
	public void addSource() {
		new RadioSourceProvider().select(getMainActivity()).main()
				.onFailure(this::failedToSaveSource).onSuccess(this::saveSource);
	}

	@Override
	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) autoReload();
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, MediaItemMenuHandler h) {
		if (h.getItem() instanceof RadioSourceItem source) {
			b.addItem(me.aap.fermata.R.id.edit, me.aap.fermata.R.drawable.edit,
						me.aap.fermata.R.string.edit).setData(source)
					.setHandler(this::contextMenuItemSelected);
			b.addItem(me.aap.fermata.R.id.delete, me.aap.fermata.R.drawable.delete,
						me.aap.fermata.R.string.delete).setData(source)
					.setHandler(this::contextMenuItemSelected);
		} else if ((h.getItem() instanceof RadioItem) && (h.getItem() instanceof BrowsableItem)) {
			b.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
							me.aap.fermata.R.string.refresh).setData(h.getItem())
					.setHandler(this::contextMenuItemSelected);
		}
		super.contributeToContextMenu(b, h);
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == me.aap.fermata.R.id.refresh) {
			reloadRadioItem(item.getData(), true);
		} else if (item.getItemId() == me.aap.fermata.R.id.edit) {
			RadioSourceItem source = item.getData();
			new RadioSourceProvider(source.getSource()).select(getMainActivity()).main()
					.onFailure(this::failedToSaveSource).onSuccess(this::saveSource);
		} else if (item.getItemId() == me.aap.fermata.R.id.delete) {
			RadioSourceItem source = item.getData();
			getRootItem().removeSource(source.getSource());
			showRecreatedRoot();
		}
		return true;
	}

	@Override
	public FutureSupplier<?> refresh() {
		ListAdapter adapter = getAdapter();
		if (adapter == null) return completedVoid();
		BrowsableItem parent = adapter.getParent();
		if (parent == null) return completedVoid();

		return getRefreshCoordinator().manual(parent, () -> {
			getRootItem().getApi().clearCache();
			getLib().getVfsManager().clearCache();
			return parent.refresh().main();
		}).main().then(result -> {
			renderRefreshResult(parent, result, false);
			return result.isFailure() ? failed(result.error()) : completedVoid();
		});
	}

	public RadioRootItem getRootItem() {
		return getAddon().getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	private RadioAddon getAddon() {
		return requireNonNull(AddonManager.get().getAddon(RadioAddon.class));
	}

	private void saveSource(RadioSource source) {
		if (source != null) getRootItem().saveSource(source);
		showRecreatedRoot();
	}

	private void showRecreatedRoot() {
		DefaultMediaLib lib = (DefaultMediaLib) getMainActivity().getLib();
		RadioRootItem root = getAddon().recreateRoot(lib);
		getAdapter().setParent(root);
		getMainActivity().showFragment(getFragmentId());
	}

	private void failedToSaveSource(Throwable error) {
		getMainActivity().showFragment(getFragmentId());
		if (isCancellation(error)) return;
		App.get().getHandler().post(() -> {
			String message = error.getLocalizedMessage();
			UiUtils.showAlert(getContext(), getString(R.string.radio_source_add_failed,
					(message == null) ? error.toString() : message));
		});
	}

	private RadioRefreshCoordinator getRefreshCoordinator() {
		return getAddon().getRefreshCoordinator();
	}

	private boolean isRootItem() {
		BrowsableItem parent = getAdapter().getParent();
		return (parent == null) || (parent instanceof RadioRootItem);
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
	public boolean isVideoModeSupported() {
		return false;
	}

	private void autoReload() {
		if (getAdapter() == null) return;
		BrowsableItem parent = getAdapter().getParent();
		if (parent instanceof RadioItem) {
			getRefreshCoordinator().auto(parent, () -> {
				getRootItem().getApi().clearCache();
				return parent.refresh();
			}).main().onSuccess(result -> renderRefreshResult(parent, result, false));
		} else {
			reload();
		}
	}

	private FutureSupplier<?> reloadRadioItem(BrowsableItem item, boolean showError) {
		return getRefreshCoordinator().manual(item, () -> {
			getRootItem().getApi().clearCache();
			return item.refresh();
		}).main().onSuccess(result -> renderRefreshResult(item, result, showError));
	}

	private void renderRefreshResult(BrowsableItem item, Result<String> result, boolean showError) {
		Status status = result.status();
		if ((status != Status.SKIPPED_COOLDOWN) && (status != Status.INACTIVE) &&
				(getView() != null)) reload();
		if (!result.isFailure()) return;

		Throwable err = result.error();
		Log.e(err, "Failed to reload radio item ", item);
		if (!showError || (err == null) || (getContext() == null)) return;
		String msg = err.getLocalizedMessage();
		UiUtils.showAlert(getContext(), (msg != null) ? msg : err.toString());
	}
}
