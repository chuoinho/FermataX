package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.audiobook.catalog.LibriVoxCatalogClient;
import me.aap.fermata.addon.audiobook.util.AudiobookIds;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class AudiobookCatalogFolder extends ExtBrowsable implements AudiobookItem {
	private final AudiobookRootItem root;
	private final AudiobookSectionItem parent;
	private final String query;
	private volatile FutureSupplier<List<Item>> active;

	AudiobookCatalogFolder(AudiobookRootItem root, AudiobookSectionItem parent, String query) {
		super(AudiobookRootItem.catalogSearchId(AudiobookIds.book("query", query)), parent, null);
		this.root = root;
		this.parent = parent;
		this.query = query;
	}

	void cancel() {
		FutureSupplier<?> task = active;
		if ((task != null) && !task.isDone()) task.cancel();
		active = null;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.audiobook_search_librivox);
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.search;
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
		return completed(query);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		FutureSupplier<List<Item>> task = root.listCatalog(this, query,
				LibriVoxCatalogClient.Sort.RELEVANCE);
		active = task;
		return task;
	}
}
