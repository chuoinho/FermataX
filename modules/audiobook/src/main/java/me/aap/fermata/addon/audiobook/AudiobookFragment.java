package me.aap.fermata.addon.audiobook;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.fermata.ui.view.MediaItemView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.local.LocalFileSystem;

public final class AudiobookFragment extends MediaLibFragment {
	private FutureSupplier<?> pendingAction;

	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder binder) {
		return new AudiobookAdapter(getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_audiobook);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.audiobook_fragment;
	}

	@Override
	public boolean isAddSourceSupported() {
		return true;
	}

	@Override
	public int getAddSourceIcon() {
		return me.aap.fermata.R.drawable.playlist_add;
	}

	@Override
	public void addSource() {
		getMainActivity().getContextMenu().show(builder -> {
			builder.setTitle(R.string.audiobook_add_source);
			builder.setSelectionHandler(this::sourceTypeSelected);
			builder.addItem(R.id.audiobook_add_local, me.aap.fermata.R.drawable.add_folder,
					R.string.audiobook_add_folder);
			builder.addItem(R.id.audiobook_add_audiobookshelf, me.aap.fermata.R.drawable.audiobook,
					R.string.audiobook_add_audiobookshelf);
			builder.addItem(R.id.audiobook_add_opds, me.aap.fermata.R.drawable.audiobook,
					R.string.audiobook_add_opds);
		});
	}

	private boolean sourceTypeSelected(OverlayMenuItem item) {
		int id = item.getItemId();
		if (id == R.id.audiobook_add_local) addFolder();
		else if (id == R.id.audiobook_add_audiobookshelf) addAudiobookshelf();
		else if (id == R.id.audiobook_add_opds) addOpds();
		return true;
	}

	@Override
	public void navBarItemReselected(int itemId) {
		if (getAdapter() != null) getAdapter().setParent(getRootItem());
	}

	@Override
	public void onResume() {
		super.onResume();
		rebindRepositoryRoot();
	}

	@Override
	public void switchingFrom(@Nullable ActivityFragment currentFragment) {
		super.switchingFrom(currentFragment);
		rebindRepositoryRoot();
	}

	private void rebindRepositoryRoot() {
		ListAdapter adapter = getAdapter();
		if (adapter == null) return;
		AudiobookRootItem root = getRootItem();
		BrowsableItem parent = adapter.getParent();
		if ((parent == null) || (parent.getRoot() != root)) adapter.setParent(root, false, false);
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder builder,
			MediaItemMenuHandler handler) {
		if (handler.getItem() instanceof AudiobookSourceItem source) {
			builder.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
					me.aap.fermata.R.string.refresh).setData(source)
					.setHandler(this::sourceMenuSelected);
			builder.addItem(me.aap.fermata.R.id.delete, me.aap.fermata.R.drawable.delete,
					me.aap.fermata.R.string.delete).setData(source)
					.setHandler(this::sourceMenuSelected);
		} else if ((handler.getItem() instanceof AudiobookBookItem book) && book.canDownload()) {
			boolean downloaded = book.isDownloadsView();
			builder.addItem(downloaded ? R.id.audiobook_delete_download : R.id.audiobook_download,
					downloaded ? me.aap.fermata.R.drawable.delete : me.aap.fermata.R.drawable.save,
					downloaded ? R.string.audiobook_delete_download :
							R.string.audiobook_download_book).setData(book)
					.setHandler(this::bookMenuSelected);
		}
		super.contributeToContextMenu(builder, handler);
	}

	private boolean bookMenuSelected(OverlayMenuItem menuItem) {
		AudiobookBookItem item = menuItem.getData();
		FutureSupplier<Void> source = (menuItem.getItemId() == R.id.audiobook_delete_download) ?
				getRootItem().deleteDownload(item.getBook()) :
				getRootItem().download(item.getBook());
		runBookAction(source);
		return true;
	}

	private void runBookAction(FutureSupplier<?> source) {
		cancelPendingAction();
		FutureSupplier<?> action = source.main();
		pendingAction = action;
		action.onSuccess(result -> {
			if (pendingAction != action) return;
			pendingAction = null;
			if (getView() == null) return;
			BrowsableItem parent = getAdapter().getParent();
			if (parent == null) return;
			parent.refresh().main().onSuccess(ignored -> {
				if ((getView() != null) && (getAdapter().getParent() == parent)) {
					getAdapter().reload();
				}
			});
		}).onFailure(error -> actionFailed(action, error));
	}

	private boolean sourceMenuSelected(OverlayMenuItem menuItem) {
		AudiobookSourceItem item = menuItem.getData();
		if (menuItem.getItemId() == me.aap.fermata.R.id.refresh) refreshSource(item);
		else if (menuItem.getItemId() == me.aap.fermata.R.id.delete) deleteSource(item);
		return true;
	}

	private void addFolder() {
		if (getMainActivity().isCarActivityNotMirror()) {
			addFolderFromCarPicker();
			return;
		}
		cancelPendingAction();
		FutureSupplier<Intent> picker = getMainActivity().startActivityForResult(() ->
				new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
						.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
								Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
		pendingAction = picker;
		picker.main().onSuccess(data -> {
			if (pendingAction != picker) return;
			pendingAction = null;
			if ((data == null) || (data.getData() == null)) return;
			Uri uri = data.getData();
			try {
				getContext().getContentResolver().takePersistableUriPermission(uri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} catch (SecurityException ignore) {
			}
			FutureSupplier<VirtualFolder> folder = ((DefaultMediaLib) getMainActivity().getLib())
					.getVfsManager().getFolder(uri.toString());
			pendingAction = folder;
			folder.main().onSuccess(this::addFolder).onFailure(error -> actionFailed(folder, error));
		}).onFailure(error -> actionFailed(picker, error));
	}

	private void addAudiobookshelf() {
		cancelPendingAction();
		FutureSupplier<AudiobookshelfSourceDialog.Config> input =
				AudiobookshelfSourceDialog.show(getMainActivity()).main();
		pendingAction = input;
		input.onSuccess(config -> {
			if (pendingAction != input) return;
			FutureSupplier<?> action = getRootItem().addAudiobookshelf(config.endpoint(),
					config.username(), config.password()).main();
			pendingAction = action;
			action.onSuccess(result -> sourceChanged(action)).onFailure(error ->
					actionFailed(action, error));
		}).onFailure(error -> {
			if (pendingAction == input) pendingAction = null;
		});
	}

	private void addOpds() {
		cancelPendingAction();
		FutureSupplier<OpdsSourceDialog.Config> input = OpdsSourceDialog.show(getMainActivity()).main();
		pendingAction = input;
		input.onSuccess(config -> {
			if (pendingAction != input) return;
			FutureSupplier<?> action = getRootItem().addOpds(config.endpoint(), config.username(),
					config.password(), config.bearerToken()).main();
			pendingAction = action;
			action.onSuccess(result -> sourceChanged(action)).onFailure(error ->
					actionFailed(action, error));
		}).onFailure(error -> {
			if (pendingAction == input) pendingAction = null;
		});
	}

	private void addFolderFromCarPicker() {
		cancelPendingAction();
		Promise<Void> selection = new Promise<>();
		pendingAction = selection;
		if (!(getMainActivity().showFragment(me.aap.utils.R.id.file_picker)
				instanceof FilePickerFragment picker)) {
			pendingAction = null;
			selection.cancel();
			return;
		}
		picker.setMode(FilePickerFragment.FOLDER);
		picker.setFileSystem(LocalFileSystem.getInstance());
		picker.setFileConsumer(resource -> {
			getMainActivity().showFragment(getFragmentId());
			if (pendingAction != selection) return;
			pendingAction = null;
			if (!(resource instanceof VirtualFolder folder)) {
				selection.cancel();
				return;
			}
			addFolder(folder);
			selection.complete(null);
		});
	}

	private void addFolder(@Nullable VirtualFolder folder) {
		if (folder == null) {
			UiUtils.showAlert(getContext(), getString(R.string.audiobook_add_failed));
			return;
		}
		cancelPendingAction();
		FutureSupplier<?> action = getRootItem().addLocalFolder(folder).main();
		pendingAction = action;
		action.onSuccess(result -> sourceChanged(action)).onFailure(error ->
				actionFailed(action, error));
	}

	private void refreshSource(AudiobookSourceItem item) {
		cancelPendingAction();
		FutureSupplier<?> action = getRootItem().refresh(item.getSource()).main();
		pendingAction = action;
		action.onSuccess(result -> sourceChanged(action)).onFailure(error ->
				actionFailed(action, error));
	}

	private void deleteSource(AudiobookSourceItem item) {
		AudiobookSource source = item.getSource();
		FutureSupplier<Void> confirm = UiUtils.showQuestion(getContext(),
				getString(R.string.audiobook_delete_source_title),
				getString(R.string.audiobook_delete_source_message, source.getName()),
				androidx.appcompat.content.res.AppCompatResources.getDrawable(getContext(),
						me.aap.fermata.R.drawable.delete)).main();
		cancelPendingAction();
		pendingAction = confirm;
		confirm.onSuccess(ignored -> {
			if (pendingAction != confirm) return;
			FutureSupplier<?> action = getRootItem().delete(source).main();
			pendingAction = action;
			action.onSuccess(result -> sourceChanged(action)).onFailure(error ->
					actionFailed(action, error));
		}).onFailure(error -> {
			if (pendingAction == confirm) pendingAction = null;
		});
	}

	private void sourceChanged(FutureSupplier<?> action) {
		if (pendingAction != action) return;
		pendingAction = null;
		if (getView() == null) return;
		AudiobookRootItem root = getRootItem();
		root.refresh().main().thenRun(() -> {
			if (getView() != null) getAdapter().setParent(root);
		});
	}

	private void searchLibriVox() {
		cancelPendingAction();
		FutureSupplier<String> input = UiUtils.queryText(getContext(),
				R.string.audiobook_search_librivox, me.aap.fermata.R.drawable.search).main();
		pendingAction = input;
		input.onSuccess(value -> {
			if (pendingAction != input) return;
			pendingAction = null;
			if ((getView() == null) || (value == null) || (value = value.trim()).isEmpty()) return;
			getAdapter().setParent(getRootItem().createSearchFolder(value));
		}).onFailure(error -> {
			if (pendingAction == input) pendingAction = null;
		});
	}

	private void importBook(AudiobookImportItem item) {
		cancelPendingAction();
		FutureSupplier<AudiobookBook> action = getRootItem().importBook(item.getBook()).main();
		pendingAction = action;
		action.onSuccess(book -> {
			if (pendingAction != action) return;
			pendingAction = null;
			if (getView() == null) return;
			AudiobookRootItem root = getRootItem();
			root.refresh().main().onSuccess(ignored -> {
				if (getView() != null) getAdapter().setParent(root.createLibraryBook(book));
			});
		}).onFailure(error -> actionFailed(action, error));
	}

	private void actionFailed(FutureSupplier<?> action, Throwable error) {
		if (pendingAction != action) return;
		pendingAction = null;
		if (isCancellation(error)) return;
		String message = error.getLocalizedMessage();
		UiUtils.showAlert(getContext(), (message == null) ?
				getString(R.string.audiobook_add_failed) : message);
	}

	private void cancelPendingAction() {
		FutureSupplier<?> action = pendingAction;
		pendingAction = null;
		if (action != null) action.cancel();
	}

	@Override
	public void onDestroyView() {
		cancelPendingAction();
		super.onDestroyView();
	}

	@Override
	protected boolean isSupportedItem(Item item) {
		return getRootItem().isChildItemId(item.getId());
	}

	@Override
	public boolean isVideoModeSupported() {
		return false;
	}

	private AudiobookRootItem getRootItem() {
		return getAddon().getRootItem((DefaultMediaLib) getMainActivity().getLib());
	}

	private AudiobookAddon getAddon() {
		return requireNonNull(AddonManager.get().getAddon(AudiobookAddon.class));
	}

	private final class AudiobookAdapter extends ListAdapter {
		AudiobookAdapter(AudiobookRootItem root) {
			super(getMainActivity(), root);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			BrowsableItem previous = getParent();
			if ((previous != parent) && (previous instanceof AudiobookCatalogFolder search)) {
				search.cancel();
			}
			return super.setParent(parent, userAction);
		}

		@Override
		public void onClick(View view) {
			Item item = ((MediaItemView) view).getItem();
			if ((item instanceof AudiobookCatalogActionItem action) &&
					(action.getAction() == AudiobookCatalogAction.SEARCH)) {
				searchLibriVox();
				return;
			}
			if (item instanceof AudiobookImportItem imported) {
				importBook(imported);
				return;
			}
			super.onClick(view);
		}
	}
}
