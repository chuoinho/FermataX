package me.aap.fermata.addon.podcast;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.view.View;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Locale;
import java.util.regex.Pattern;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.data.PodcastImportResult;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.MediaItemView;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.addon.podcast.model.PodcastSource;
import me.aap.fermata.addon.podcast.download.PodcastDownloadState;
import me.aap.utils.async.Promise;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.local.LocalFileSystem;

public final class PodcastFragment extends MediaLibFragment {
	private FutureSupplier<String> queryInput;
	private FutureSupplier<?> pendingAction;

	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder binder) {
		return new PodcastAdapter(getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_podcast);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.podcast_fragment;
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
			builder.setTitle(R.string.podcast_add);
			builder.setSelectionHandler(this::actionSelected);
			for (PodcastAction action : PodcastAction.values()) {
				builder.addItem(action.menuId, action.icon, action.title);
			}
		});
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
		PodcastAdapter adapter = getAdapter();
		if (adapter == null) return;
		PodcastRootItem current = getRootItem();
		BrowsableItem parent = adapter.getParent();
		if ((parent == null) || (parent.getRoot() != current)) {
			adapter.setParent(current, false, false);
		}
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder builder, MediaItemMenuHandler handler) {
		if (handler.getItem() instanceof PodcastSubscriptionItem subscription) {
			builder.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
					me.aap.fermata.R.string.refresh).setData(subscription)
					.setHandler(this::subscriptionMenuSelected);
			builder.addItem(me.aap.fermata.R.id.edit, me.aap.fermata.R.drawable.edit,
					me.aap.fermata.R.string.edit).setData(subscription)
					.setHandler(this::subscriptionMenuSelected);
			builder.addItem(me.aap.fermata.R.id.delete, me.aap.fermata.R.drawable.delete,
					me.aap.fermata.R.string.delete).setData(subscription)
					.setHandler(this::subscriptionMenuSelected);
		} else if (handler.getItem() instanceof PodcastEpisodeItem episode) {
			boolean downloaded = episode.isDownloaded();
			builder.addItem(downloaded ? R.id.podcast_delete_download : R.id.podcast_download,
					downloaded ? me.aap.fermata.R.drawable.delete : me.aap.fermata.R.drawable.save,
					downloaded ? R.string.podcast_delete_download : R.string.podcast_download)
					.setData(episode).setHandler(this::episodeMenuSelected);
			boolean played = episode.getEpisode().isPlayed();
			builder.addItem(played ? R.id.podcast_mark_unplayed : R.id.podcast_mark_played,
					played ? me.aap.fermata.R.drawable.rw : me.aap.fermata.R.drawable.done,
					played ? R.string.podcast_mark_unplayed : R.string.podcast_mark_played)
					.setData(episode).setHandler(this::episodeMenuSelected);
		}
		super.contributeToContextMenu(builder, handler);
	}

	private boolean episodeMenuSelected(OverlayMenuItem menuItem) {
		PodcastEpisodeItem item = menuItem.getData();
		if (menuItem.getItemId() == R.id.podcast_download) {
			runEpisodeAction(getRootItem().download(item.getEpisode()));
			return true;
		}
		if (menuItem.getItemId() == R.id.podcast_delete_download) {
			runEpisodeAction(getRootItem().deleteDownload(item.getEpisode()));
			return true;
		}
		boolean played = menuItem.getItemId() == R.id.podcast_mark_played;
		runEpisodeAction(getRootItem().setPlayed(item.getEpisode(), played));
		return true;
	}

	private void runEpisodeAction(FutureSupplier<?> source) {
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

	@Override
	public void onDestroyView() {
		if (queryInput != null) queryInput.cancel();
		queryInput = null;
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

	PodcastRootItem getRootItem() {
		return getAddon().getRootItem((DefaultMediaLib) getMainActivity().getLib());
	}

	private PodcastAddon getAddon() {
		return requireNonNull(AddonManager.get().getAddon(PodcastAddon.class));
	}

	private boolean actionSelected(OverlayMenuItem item) {
		PodcastAction action = PodcastAction.fromMenuId(item.getItemId());
		if (action != null) openAction(action);
		return true;
	}

	private boolean subscriptionMenuSelected(OverlayMenuItem menuItem) {
		PodcastSubscriptionItem item = menuItem.getData();
		if (menuItem.getItemId() == me.aap.fermata.R.id.refresh) refreshSubscription(item);
		else if (menuItem.getItemId() == me.aap.fermata.R.id.edit) editSubscription(item);
		else if (menuItem.getItemId() == me.aap.fermata.R.id.delete) deleteSubscription(item);
		return true;
	}

	private void refreshSubscription(PodcastSubscriptionItem item) {
		cancelPendingAction();
		FutureSupplier<?> action = getRootItem().refresh(item.getSubscription()).main();
		pendingAction = action;
		action.onSuccess(result -> {
			if (pendingAction != action) return;
			pendingAction = null;
			item.refresh().main().thenRun(() -> {
				if (getView() != null) getAdapter().reload();
			});
		}).onFailure(error -> actionFailed(action, error));
	}

	private void editSubscription(PodcastSubscriptionItem item) {
		if (queryInput != null) queryInput.cancel();
		PodcastSubscription subscription = item.getSubscription();
		String initial = (subscription.getCredentialRef() == null) ?
				subscription.getCanonicalUrl() : "";
		FutureSupplier<PodcastSource> input = PodcastSourceDialog.show(getMainActivity(),
				R.string.podcast_edit_rss, initial).main();
		pendingAction = input;
		input.onSuccess(source -> {
			if (pendingAction != input) return;
			pendingAction = null;
			if (source == null) return;
			getMainActivity().showFragment(getFragmentId());
			cancelPendingAction();
			FutureSupplier<?> action = getRootItem().edit(subscription, source).main();
			pendingAction = action;
			action.onSuccess(result -> subscriptionSaved(action))
					.onFailure(error -> actionFailed(action, error));
		}).onFailure(error -> {
			if (pendingAction == input) pendingAction = null;
		});
	}

	private void deleteSubscription(PodcastSubscriptionItem item) {
		PodcastSubscription subscription = item.getSubscription();
		FutureSupplier<Void> confirm = UiUtils.showQuestion(getContext(),
				getString(R.string.podcast_delete_title),
				getString(R.string.podcast_delete_message, subscription.getTitle()),
				androidx.appcompat.content.res.AppCompatResources.getDrawable(getContext(),
						me.aap.fermata.R.drawable.delete)).main();
		cancelPendingAction();
		pendingAction = confirm;
		confirm.onSuccess(ignored -> {
			if (pendingAction != confirm) return;
			FutureSupplier<?> action = getRootItem().delete(subscription).main();
			pendingAction = action;
			action.onSuccess(result -> subscriptionSaved(action))
					.onFailure(error -> actionFailed(action, error));
		}).onFailure(error -> {
			if (pendingAction == confirm) pendingAction = null;
		});
	}

	private void openAction(PodcastAction action) {
		if (action == PodcastAction.ADD_RSS) {
			queryRssUrl();
			return;
		}
		if (action == PodcastAction.IMPORT_OPML) {
			importOpml();
			return;
		}
		if (action == PodcastAction.EXPORT_OPML) {
			exportOpml();
			return;
		}
		if (action != PodcastAction.SEARCH) {
			getAdapter().setParent(getRootItem().getActionItem(action));
			return;
		}

		if (queryInput != null) queryInput.cancel();
		FutureSupplier<String> input = UiUtils.queryText(getContext(), R.string.podcast_search,
				me.aap.fermata.R.drawable.search).main();
		queryInput = input;
		input.onSuccess(value -> {
			if (queryInput != input) return;
			queryInput = null;
			if ((getView() == null) || (value == null) || (value = value.trim()).isEmpty()) return;
			Locale locale = getResources().getConfiguration().getLocales().get(0);
			getAdapter().setParent(getRootItem().createSearchFolder(
					new PodcastSearchRequest(value, locale, 25)));
		}).onFailure(error -> {
			if (queryInput == input) queryInput = null;
		});
	}

	private void importOpml() {
		if (getMainActivity().isCarActivityNotMirror()) {
			importOpmlFromCarPicker();
			return;
		}
		cancelPendingAction();
		FutureSupplier<Intent> picker = getMainActivity().startActivityForResult(() ->
				new Intent(Intent.ACTION_OPEN_DOCUMENT)
						.addCategory(Intent.CATEGORY_OPENABLE)
						.setType("*/*"));
		pendingAction = picker;
		picker.main().onSuccess(data -> {
			if (pendingAction != picker) return;
			if ((data == null) || (data.getData() == null)) {
				pendingAction = null;
				return;
			}
			try {
				Uri uri = data.getData();
				InputStream input = getContext().getContentResolver().openInputStream(uri);
				if (input == null) throw new java.io.IOException("Unable to open OPML file");
				FutureSupplier<PodcastImportResult> task = getRootItem().importOpml(input).main();
				pendingAction = task;
				task.onSuccess(result -> importCompleted(task, result))
						.onFailure(error -> actionFailed(task, error));
			} catch (Throwable error) {
				pendingAction = null;
				UiUtils.showAlert(getContext(), getString(R.string.podcast_import_failed));
			}
		}).onFailure(error -> actionFailed(picker, error));
	}

	private void importOpmlFromCarPicker() {
		cancelPendingAction();
		Promise<Void> selection = new Promise<>();
		pendingAction = selection;
		if (!(getMainActivity().showFragment(me.aap.utils.R.id.file_picker)
				instanceof FilePickerFragment picker)) {
			pendingAction = null;
			selection.cancel();
			return;
		}
		picker.setMode(FilePickerFragment.FILE);
		picker.setPattern(Pattern.compile("(?i).+\\.(opml|xml)$"));
		picker.setFileSystem(LocalFileSystem.getInstance());
		picker.setFileConsumer(resource -> {
			getMainActivity().showFragment(getFragmentId());
			if (pendingAction != selection) return;
			pendingAction = null;
			if (resource == null) {
				selection.cancel();
				return;
			}
			try {
				File file = resource.getLocalFile();
				if ((file == null) || !file.isFile()) throw new java.io.IOException(
						"Unable to open OPML file");
				InputStream input = new FileInputStream(file);
				FutureSupplier<PodcastImportResult> task = getRootItem().importOpml(input).main();
				pendingAction = task;
				task.onSuccess(result -> importCompleted(task, result))
						.onFailure(error -> actionFailed(task, error));
				selection.complete(null);
			} catch (Throwable error) {
				selection.completeExceptionally(error);
				UiUtils.showAlert(getContext(), getString(R.string.podcast_import_failed));
			}
		});
	}

	private void importCompleted(FutureSupplier<?> action, PodcastImportResult result) {
		if (pendingAction != action) return;
		pendingAction = null;
		if (getView() == null) return;
		UiUtils.showInfo(getContext(), getString(R.string.podcast_import_result,
				result.imported(), result.failed()));
		PodcastRootItem root = getRootItem();
		root.refresh().main().thenRun(() -> {
			if (getView() != null) getAdapter().setParent(root);
		});
	}

	private void queryRssUrl() {
		cancelPendingAction();
		FutureSupplier<PodcastSource> input = PodcastSourceDialog.show(getMainActivity(),
				R.string.podcast_add_rss, "").main();
		pendingAction = input;
		input.onSuccess(source -> {
			if (pendingAction != input) return;
			pendingAction = null;
			if ((getView() == null) || (source == null)) return;
			getMainActivity().showFragment(getFragmentId());
			subscribe(source);
		}).onFailure(error -> {
			if (pendingAction == input) pendingAction = null;
		});
	}

	private void subscribe(PodcastSource source) {
		cancelPendingAction();
		FutureSupplier<?> action = getRootItem().subscribe(source).main();
		pendingAction = action;
		action.onSuccess(result -> subscriptionSaved(action)).onFailure(error ->
				actionFailed(action, error));
	}

	private void exportOpml() {
		cancelPendingAction();
		FutureSupplier<Boolean> choice = choosePrivateExport();
		pendingAction = choice;
		choice.onSuccess(includePrivate -> {
			if (pendingAction != choice) return;
			if (getMainActivity().isCarActivityNotMirror()) {
				exportOpmlToCarPicker(includePrivate);
				return;
			}
			FutureSupplier<Intent> picker = getMainActivity().startActivityForResult(() ->
					new Intent(Intent.ACTION_CREATE_DOCUMENT)
							.addCategory(Intent.CATEGORY_OPENABLE)
							.setType("text/x-opml")
							.putExtra(Intent.EXTRA_TITLE, "fermatax-podcasts.opml"));
			pendingAction = picker;
			picker.main().onSuccess(data -> exportToFile(picker, data, includePrivate))
					.onFailure(error -> actionFailed(picker, error));
		}).onFailure(error -> {
			if (pendingAction == choice) pendingAction = null;
		});
	}

	private void exportOpmlToCarPicker(boolean includePrivate) {
		Promise<Void> selection = new Promise<>();
		pendingAction = selection;
		if (!(getMainActivity().showFragment(me.aap.utils.R.id.file_picker)
				instanceof FilePickerFragment picker)) {
			pendingAction = null;
			selection.cancel();
			return;
		}
		picker.setMode((byte) (FilePickerFragment.FOLDER | FilePickerFragment.WRITABLE));
		picker.setFileSystem(LocalFileSystem.getInstance());
		picker.setFileConsumer(resource -> {
			getMainActivity().showFragment(getFragmentId());
			if (pendingAction != selection) return;
			pendingAction = null;
			if (resource == null) {
				selection.cancel();
				return;
			}
			try {
				File directory = resource.getLocalFile();
				if ((directory == null) || !directory.isDirectory()) {
					throw new java.io.IOException("Unable to open export folder");
				}
				File file = uniqueExportFile(directory);
				OutputStream output = new FileOutputStream(file);
				FutureSupplier<?> task = getRootItem().exportOpml(output, includePrivate).main();
				pendingAction = task;
				task.onSuccess(ignored -> {
					if (pendingAction == task) pendingAction = null;
				}).onFailure(error -> actionFailed(task, error));
				selection.complete(null);
			} catch (Throwable error) {
				selection.completeExceptionally(error);
				UiUtils.showAlert(getContext(), getString(R.string.podcast_export_failed));
			}
		});
	}

	private static File uniqueExportFile(File directory) {
		File file = new File(directory, "fermatax-podcasts.opml");
		for (int index = 1; file.exists(); index++) {
			file = new File(directory, "fermatax-podcasts-" + index + ".opml");
		}
		return file;
	}

	private FutureSupplier<Boolean> choosePrivateExport() {
		Promise<Boolean> result = new Promise<>();
		ActivityDelegate activity = ActivityDelegate.get(getContext());
		activity.createDialogBuilder(getContext())
				.setTitle(R.string.podcast_export_private_title)
				.setMessage(R.string.podcast_export_private_message)
				.setNegativeButton(R.string.podcast_export_public_only,
						(dialog, which) -> result.complete(false))
				.setPositiveButton(R.string.podcast_export_include_private,
						(dialog, which) -> result.complete(true))
				.show();
		return result;
	}

	private void exportToFile(FutureSupplier<?> picker, Intent data, boolean includePrivate) {
		if (pendingAction != picker) return;
		if ((data == null) || (data.getData() == null)) {
			pendingAction = null;
			return;
		}
		try {
			OutputStream output = getContext().getContentResolver().openOutputStream(data.getData());
			if (output == null) throw new java.io.IOException("Unable to create OPML file");
			FutureSupplier<?> task = getRootItem().exportOpml(output, includePrivate).main();
			pendingAction = task;
			task.onSuccess(ignored -> {
				if (pendingAction == task) pendingAction = null;
			}).onFailure(error -> actionFailed(task, error));
		} catch (Throwable error) {
			pendingAction = null;
			UiUtils.showAlert(getContext(), getString(R.string.podcast_export_failed));
		}
	}

	private void subscribe(String url) {
		cancelPendingAction();
		FutureSupplier<?> action = getRootItem().subscribe(url).main();
		pendingAction = action;
		action.onSuccess(result -> subscriptionSaved(action)).onFailure(error ->
				actionFailed(action, error));
	}

	private void subscribe(PodcastSubscribeItem item) {
		cancelPendingAction();
		FutureSupplier<PodcastSubscription> action =
				getRootItem().subscribe(item.getResult()).main();
		pendingAction = action;
		action.onSuccess(result -> subscriptionSaved(action, result)).onFailure(error ->
				actionFailed(action, error));
	}

	private void subscriptionSaved(FutureSupplier<?> action,
			PodcastSubscription subscription) {
		if (pendingAction != action) return;
		pendingAction = null;
		if (getView() == null) return;
		PodcastRootItem root = getRootItem();
		root.refresh().main().thenRun(() -> {
			if (getView() != null) getAdapter().setParent(
					new PodcastSubscriptionItem(root, subscription));
		});
	}

	private void subscriptionSaved(FutureSupplier<?> action) {
		if (pendingAction != action) return;
		pendingAction = null;
		if (getView() == null) return;
		PodcastRootItem root = getRootItem();
		root.refresh().main().thenRun(() -> {
			if (getView() != null) getAdapter().setParent(root);
		});
	}

	private void actionFailed(FutureSupplier<?> action, Throwable error) {
		if (pendingAction != action) return;
		pendingAction = null;
		if (isCancellation(error)) return;
		if (getView() != null) {
			String message = error.getLocalizedMessage();
			UiUtils.showAlert(getContext(), (message == null) ?
					getString(R.string.podcast_add_failed) : message);
		}
	}

	private void cancelPendingAction() {
		FutureSupplier<?> action = pendingAction;
		pendingAction = null;
		if (action != null) action.cancel();
	}

	private final class PodcastAdapter extends ListAdapter {
		PodcastAdapter(PodcastRootItem root) {
			super(getMainActivity(), root);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			BrowsableItem previous = getParent();
			if ((previous != parent) && (previous instanceof PodcastSearchFolder search)) {
				search.cancel();
			}
			return super.setParent(parent, userAction);
		}

		@Override
		public void onClick(View view) {
			Item item = ((MediaItemView) view).getItem();
			if (item instanceof PodcastActionItem action) {
				openAction(action.getAction());
				return;
			}
			if (item instanceof PodcastSubscribeItem subscribe) {
				subscribe(subscribe);
				return;
			}
			super.onClick(view);
		}
	}
}
