package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class AudiobookCatalogActionItem extends ExtBrowsable implements AudiobookItem {
	private final AudiobookRootItem root;
	private final AudiobookSectionItem parent;
	private final AudiobookCatalogAction action;

	AudiobookCatalogActionItem(AudiobookRootItem root, AudiobookSectionItem parent,
			AudiobookCatalogAction action) {
		super(AudiobookRootItem.catalogActionId(action), parent, null);
		this.root = root;
		this.parent = parent;
		this.action = action;
	}

	AudiobookCatalogAction getAction() {
		return action;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(action.title);
	}

	@Override
	public int getIcon() {
		return action.icon;
	}

	@NonNull
	@Override
	public AudiobookRootItem getRoot() {
		return root;
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		return parent;
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed(getLib().getContext().getString(R.string.audiobook_source_librivox));
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return (action == AudiobookCatalogAction.SEARCH) ? completed(List.of()) :
				root.listCatalog(this, "", action.sort);
	}
}
