package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;

final class PodcastActionItem extends ExtBrowsable implements PodcastItem {
	private final PodcastRootItem root;
	private final PodcastAction action;

	PodcastActionItem(PodcastRootItem root, PodcastAction action) {
		super(PodcastRootItem.actionId(action), root, null);
		this.root = root;
		this.action = action;
	}

	PodcastAction getAction() {
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
}
