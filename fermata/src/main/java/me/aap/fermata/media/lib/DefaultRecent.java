package me.aap.fermata.media.lib;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.mapToArray;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Recent;
import me.aap.fermata.media.pref.RecentPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;

class DefaultRecent extends ItemContainer<PlayableItem> implements Recent, RecentPrefs {
	public static final String ID = "Recent";
	public static final String SCHEME = "recent";
	private static final int MAX_RECENT_ITEMS = 30;
	private final DefaultMediaLib lib;
	private final SharedPreferenceStore recentPrefStore;

	DefaultRecent(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		SharedPreferences prefs = lib.getContext().getSharedPreferences("recent", Context.MODE_PRIVATE);
		recentPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.recent);
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getName());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public BrowsableItem getRoot() {
		return this;
	}

	@NonNull
	@Override
	public PreferenceStore getRecentPreferenceStore() {
		return recentPrefStore;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}

	@Override
	public FutureSupplier<List<Item>> listChildren() {
		return listChildren(getRecentPreferenceStore(), RECENT).map(children -> {
			List<Item> eligible = new ArrayList<>(children.size());
			for (Item child : children) {
				if ((child instanceof PlayableItem playable) && playable.isRecentEligible()) {
					eligible.add(child);
				}
			}

			if (eligible.size() != children.size()) {
				setRecentPref(mapToArray(eligible, item -> ((PlayableItem) item).getOrigId(),
						String[]::new));
			}
			return eligible;
		});
	}

	@Override
	public boolean isRecentItemId(String id) {
		return isChildItemId(id);
	}

	@Override
	protected String getScheme() {
		return SCHEME;
	}

	@Override
	protected void saveChildren(List<PlayableItem> children) {
		setRecentPref(mapToArray(children, PlayableItem::getOrigId, String[]::new));
	}

	@Override
	public FutureSupplier<Void> addItem(PlayableItem item) {
		if (!item.isRecentEligible()) return completedVoid();
		return list().map(children -> {
			PlayableItem recent = toChildItem(item);
			String origId = recent.getOrigId();
			if (!children.isEmpty() && origId.equals(children.get(0).getOrigId())) return null;
			List<PlayableItem> newChildren = new ArrayList<>(Math.min(children.size() + 1, MAX_RECENT_ITEMS));
			List<PlayableItem> removed = new ArrayList<>(1);
			newChildren.add(recent);

			for (PlayableItem child : children) {
				if ((child == recent) || origId.equals(child.getOrigId())) continue;
				if (newChildren.size() < MAX_RECENT_ITEMS) {
					newChildren.add(child);
				} else {
					removed.add(child);
				}
			}

			setNewChildren(newChildren);
			saveChildren(newChildren);
			CollectionUtils.forEach(removed, this::itemRemoved);
			return null;
		});
	}

	@Override
	protected void itemRemoved(PlayableItem i) {
		super.itemRemoved(i);
	}
}
