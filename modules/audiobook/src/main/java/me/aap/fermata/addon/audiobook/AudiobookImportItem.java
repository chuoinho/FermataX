package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.audiobook.catalog.LibriVoxBook;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class AudiobookImportItem extends ExtBrowsable implements AudiobookItem {
	private final AudiobookRootItem root;
	private final AudiobookCatalogBookItem parent;
	private final LibriVoxBook book;

	AudiobookImportItem(AudiobookRootItem root, AudiobookCatalogBookItem parent,
			LibriVoxBook book) {
		super(AudiobookRootItem.catalogImportId(book.identifier()), parent, null);
		this.root = root;
		this.parent = parent;
		this.book = book;
	}

	LibriVoxBook getBook() {
		return book;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.audiobook_add_to_library);
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.playlist_add;
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
		return completed(List.of());
	}
}
