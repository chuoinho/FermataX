package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class AudiobookSourceItem extends ExtBrowsable implements AudiobookItem {
	private final AudiobookRootItem root;
	private final BrowsableItem parent;
	private final AudiobookSource source;

	AudiobookSourceItem(AudiobookRootItem root, BrowsableItem parent, AudiobookSource source) {
		super(AudiobookRootItem.sourceId(source.getId()), parent, null);
		this.root = root;
		this.parent = parent;
		this.source = source;
	}

	AudiobookSource getSource() {
		return source;
	}

	@NonNull
	@Override
	public String getName() {
		return source.getName();
	}

	@Override
	public int getIcon() {
		return source.getType() == AudiobookSourceType.LOCAL ?
				me.aap.fermata.R.drawable.add_folder : me.aap.fermata.R.drawable.audiobook;
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
		int title = switch (source.getType()) {
			case LOCAL -> R.string.audiobook_source_local;
			case LIBRIVOX -> R.string.audiobook_source_librivox;
			case AUDIOBOOKSHELF -> R.string.audiobook_source_audiobookshelf;
			case OPDS -> R.string.audiobook_source_opds;
		};
		return completed(getLib().getContext().getString(title));
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return root.listSourceBooks(this);
	}
}
