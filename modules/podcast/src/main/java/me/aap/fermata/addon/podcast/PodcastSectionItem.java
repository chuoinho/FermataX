package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class PodcastSectionItem extends ExtBrowsable implements PodcastItem {
	private final PodcastRootItem root;
	private final PodcastSection section;

	PodcastSectionItem(PodcastRootItem root, PodcastSection section) {
		super(PodcastRootItem.sectionId(section), root, null);
		this.root = root;
		this.section = section;
	}

	PodcastSection getSection() {
		return section;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(section.title);
	}

	@Override
	public int getIcon() {
		return section.icon;
	}

	@NonNull
	@Override
	public PodcastRootItem getRoot() {
		return root;
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		return root;
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
		return completed("");
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return root.listSection(this);
	}
}
