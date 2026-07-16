package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class AudiobookBookItem extends ExtBrowsable implements AudiobookItem {
	private final AudiobookRootItem root;
	private final BrowsableItem parent;
	private final AudiobookBook book;

	AudiobookBookItem(AudiobookRootItem root, BrowsableItem parent, AudiobookBook book) {
		super(AudiobookRootItem.bookId(book.getId()), parent, null);
		this.root = root;
		this.parent = parent;
		this.book = book;
	}

	AudiobookBook getBook() {
		return book;
	}

	boolean canDownload() {
		return book.getRemoteId() != null;
	}

	boolean isDownloadsView() {
		return (parent instanceof AudiobookSectionItem section) &&
				(section.getSection() == AudiobookSection.DOWNLOADS);
	}

	@NonNull
	@Override
	public String getName() {
		return book.getTitle();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.audiobook;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		String artwork = book.getArtworkUrl();
		return artwork.isEmpty() ? completedNull() : completed(Uri.parse(artwork));
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
		String subtitle = book.getAuthor();
		if (subtitle.isEmpty()) subtitle = book.getNarrator();
		return completed(subtitle);
	}

	@Override
	protected FutureSupplier<String> buildDescription() {
		return completed(book.getDescription());
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return root.listChapters(this);
	}
}
