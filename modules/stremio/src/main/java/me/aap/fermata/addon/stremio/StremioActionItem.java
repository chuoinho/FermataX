package me.aap.fermata.addon.stremio;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;

final class StremioActionItem extends ExtBrowsable implements StremioItem {
	private final StremioRootItem root;
	private final StremioAction action;

	StremioActionItem(StremioRootItem root, StremioAction action) {
		super(StremioRootItem.actionId(action), root, null);
		this.root = root;
		this.action = action;
	}

	StremioAction getAction() {
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
	public StremioRootItem getRoot() {
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
