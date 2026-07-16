package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;

abstract class RadioFolder extends ExtBrowsable implements RadioItem {
	private final RadioRootItem root;
	private final BrowsableItem parent;
	private final String name;
	private final int icon;
	@Nullable
	private final String subtitle;

	RadioFolder(String id, @NonNull BrowsableItem parent, @NonNull String name,
							@DrawableRes int icon) {
		this(id, parent, name, icon, null);
	}

	RadioFolder(String id, @NonNull BrowsableItem parent, @NonNull String name,
							@DrawableRes int icon, @Nullable String subtitle) {
		super(id, parent, null);
		this.parent = parent;
		this.root = (RadioRootItem) parent.getRoot();
		this.name = name;
		this.icon = icon;
		this.subtitle = subtitle;
	}

	@NonNull
	@Override
	public RadioRootItem getRoot() {
		return root;
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getIcon() {
		return icon;
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return completed((seqNum == 0) ? name : (seqNum + ". " + name));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed((subtitle == null) ? "" : subtitle);
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		return parent;
	}
}
