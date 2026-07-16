package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.audiobook.catalog.LibriVoxBook;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class AudiobookCatalogBookItem extends ExtBrowsable implements AudiobookItem {
	private final AudiobookRootItem root;
	private final BrowsableItem parent;
	private final LibriVoxBook book;

	AudiobookCatalogBookItem(AudiobookRootItem root, BrowsableItem parent, LibriVoxBook book) {
		super(AudiobookRootItem.catalogBookId(book.identifier()), parent, null);
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
		return book.title();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.audiobook;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return book.artworkUrl().isEmpty() ? completedNull() : completed(Uri.parse(book.artworkUrl()));
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
		return completed(book.author());
	}

	@Override
	protected FutureSupplier<String> buildDescription() {
		return completed(book.description());
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed(List.of(new AudiobookImportItem(root, this, book)));
	}
}
