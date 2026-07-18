package me.aap.fermata.media.lib;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completedEmptyList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;


/**
 * @author Andrey Pavlenko
 */
public abstract class ItemContainer<C extends Item> extends BrowsableItemBase {
	private static final List<WeakReference<ItemContainer<?>>> containers = new ArrayList<>();

	protected ItemContainer(String id, @Nullable BrowsableItem parent, @Nullable VirtualResource file) {
		super(id, parent, file);
		synchronized (containers) {
			containers.add(new WeakReference<>(this));
		}
	}

	/** Invalidates persisted collections that may have resolved while an addon was unavailable. */
	public static void invalidateResolvedChildren() {
		synchronized (containers) {
			for (Iterator<WeakReference<ItemContainer<?>>> it = containers.iterator(); it.hasNext(); ) {
				ItemContainer<?> container = it.next().get();
				if (container == null) it.remove();
				else container.invalidateChildrenCache();
			}
		}
	}

	protected abstract String getScheme();

	protected abstract void saveChildren(List<C> children);

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	FutureSupplier<Item> getItem(String id) {
		assert id.startsWith(getScheme());

		return list().map(list -> {
			for (C i : list) if (id.equals(i.getId())) return i;
			return null;
		});
	}

	FutureSupplier<List<Item>> listChildren(PreferenceStore prefs, Pref<Supplier<String[]>> idsPref) {
		String[] ids = prefs.getStringArrayPref(idsPref);
		if ((ids == null) || (ids.length == 0)) return completedEmptyList();
		MediaLib lib = getLib();
		Item[] resolved = new Item[ids.length];
		AtomicBoolean resolutionFailed = new AtomicBoolean();
		List<Integer> indexes = new ArrayList<>(ids.length);
		for (int i = 0; i < ids.length; i++) indexes.add(i);

		return forEach(index -> lib.getItem(ids[index])
				.ifFail(err -> {
					resolutionFailed.set(true);
					Log.e(err, "Failed to get item: ", ids[index]);
					return null;
				}).map(c -> {
					resolved[index] = c;
					return null;
				}), indexes).main().map(v -> {
			List<Item> children = new ArrayList<>(ids.length);
			List<String> resolvedIds = new ArrayList<>(ids.length);
			boolean update = false;
			boolean pruneMissing = shouldPruneMissing(
					AddonManager.get().hasUnresolvedEnabledAddons(), resolutionFailed.get());

			for (int i = 0; i < ids.length; i++) {
				Item child = resolved[i];
				if (child == null) {
					Log.w("Item not found: ", ids[i]);
					if (pruneMissing) update = true;
					else resolvedIds.add(ids[i]);
					continue;
				}

				children.add(toChildItem(child));
				String newId = child.getId();
				resolvedIds.add(newId);
				if (!newId.equals(ids[i])) {
					Log.i("Item id has been changed. Updating ", ids[i], " -> ", newId);
					update = true;
				}
			}

			if (update) prefs.applyStringArrayPref(idsPref,
					resolvedIds.toArray(new String[0]));
			return children;
		});
	}

	static boolean shouldPruneMissing(boolean unresolvedAddons, boolean resolutionFailed) {
		return !unresolvedAddons && !resolutionFailed;
	}

	public FutureSupplier<Void> addItem(C item) {
		return list().map(children -> {
			C i = toChildItem(item);
			if (children.contains(i)) return null;

			List<C> newChildren = new ArrayList<>(children.size() + 1);
			newChildren.addAll(children);
			newChildren.add(i);
			itemAdded(i);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	public FutureSupplier<Void> addItems(List<C> items) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list.size() + items.size());
			boolean added = false;
			newChildren.addAll(list);

			for (C i : items) {
				i = toChildItem(i);
				if (list.contains(i)) continue;
				newChildren.add(i);
				itemAdded(i);
				added = true;
			}

			if (!added) return null;

			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	public FutureSupplier<Void> removeItem(int idx) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			C removed = newChildren.remove(idx);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			itemRemoved(removed);
			return null;
		});
	}

	public FutureSupplier<Void> removeItem(C item) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			C i = toChildItem(item);
			if (!newChildren.remove(i)) return null;

			setNewChildren(newChildren);
			saveChildren(newChildren);
			itemRemoved(i);
			return null;
		});
	}

	public FutureSupplier<Void> removeItems(List<C> items) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			List<C> removed = new ArrayList<>(items.size());

			for (C i : items) {
				if (newChildren.remove(i = toChildItem(i))) removed.add(i);
			}

			if (removed.isEmpty()) return null;
			setNewChildren(newChildren);
			saveChildren(newChildren);
			CollectionUtils.forEach(removed, this::itemRemoved);
			return null;
		});
	}

	protected void itemAdded(C i) {
	}

	@CallSuper
	protected void itemRemoved(C i) {
		getLib().removeFromCache(i);
	}

	public FutureSupplier<Void> moveItem(int fromPosition, int toPosition) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			CollectionUtils.move(newChildren, fromPosition, toPosition);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	public boolean isChildItemId(String id) {
		return id.startsWith(getScheme());
	}

	public String toChildItemId(String id) {
		if (isChildItemId(id)) return id;
		SharedTextBuilder tb = SharedTextBuilder.get();
		return tb.append(getScheme()).append(':').append(id).releaseString();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected void setNewChildren(List<C> c) {
		super.setChildren((List) c);
	}

	@SuppressWarnings("unchecked")
	protected C toChildItem(Item i) {
		String id = i.getId();
		if (isChildItemId(id)) return (C) i;
		if (!(i instanceof PlayableItem)) throw new IllegalArgumentException("Unsupported child: " + i);

		PlayableItem pi = (PlayableItem) i;
		return (C) pi.export(toChildItemId(pi.getOrigId()), this);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected FutureSupplier<List<C>> list() {
		return (FutureSupplier) getUnsortedChildren().main();
	}
}
