package me.aap.fermata.media.lib;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedVoid;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private volatile ChildOrderSnapshot childOrder = ChildOrderSnapshot.EMPTY;

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

	/** IDs kept in persistence while their dynamic addon is unavailable. */
	protected final List<String> getUnresolvedChildIds() {
		return childOrder;
	}

	protected final boolean removeUnresolvedChildIds(java.util.Collection<String> ids) {
		ChildOrderSnapshot current = childOrder;
		if (current.isEmpty() || ids.isEmpty()) return false;
		Set<String> remove = new HashSet<>(ids);
		remove.retainAll(current.unresolvedIds);
		if (remove.isEmpty()) return false;
		List<String> unresolved = new ArrayList<>(current.unresolvedIds);
		List<String> ordered = new ArrayList<>(current.orderedIds);
		unresolved.removeAll(remove);
		ordered.removeAll(remove);
		childOrder = ChildOrderSnapshot.create(ordered, unresolved);
		return true;
	}

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
		if ((ids == null) || (ids.length == 0)) {
			childOrder = ChildOrderSnapshot.EMPTY;
			return completedEmptyList();
		}
		DefaultMediaLib lib = getLib();
		Item[] resolved = new Item[ids.length];
		boolean[] resolutionFailed = new boolean[ids.length];
		boolean[] retainMissing = new boolean[ids.length];
		List<Integer> indexes = new ArrayList<>(ids.length);
		for (int i = 0; i < ids.length; i++) indexes.add(i);

		return forEach(index -> lib.getItem(ids[index])
				.ifFail(err -> {
					resolutionFailed[index] = true;
					Log.e(err, "Failed to get item: ", ids[index]);
					return null;
				}).then(c -> {
					resolved[index] = c;
					if (c != null) return completedVoid();
					return AddonManager.get().shouldRetainMissingItem(lib, ids[index]).map(retain -> {
						retainMissing[index] = Boolean.TRUE.equals(retain);
						return null;
					});
				}), indexes).main().map(v -> {
			List<Item> children = new ArrayList<>(ids.length);
			List<String> resolvedIds = new ArrayList<>(ids.length);
			List<String> unresolved = new ArrayList<>();
			boolean update = false;

			for (int i = 0; i < ids.length; i++) {
				Item child = resolved[i];
				if (child == null) {
					Log.w("Item not found: ", ids[i]);
					if (shouldPruneMissing(AddonManager.get().isDeferredItemId(ids[i]) ||
							retainMissing[i], resolutionFailed[i])) update = true;
					else {
						resolvedIds.add(ids[i]);
						unresolved.add(ids[i]);
					}
					continue;
				}

				children.add(toChildItem(child));
				String newId = PersistentMediaItem.idOf(child);
				resolvedIds.add(newId);
				if (!newId.equals(ids[i])) {
					Log.i("Item id has been changed. Updating ", ids[i], " -> ", newId);
					update = true;
				}
			}

			childOrder = ChildOrderSnapshot.create(resolvedIds, unresolved);
			if (update) prefs.applyStringArrayPref(idsPref,
					resolvedIds.toArray(new String[0]));
			return children;
		});
	}

	static boolean shouldPruneMissing(boolean unresolvedAddons, boolean resolutionFailed) {
		return !unresolvedAddons && !resolutionFailed;
	}

	static String[] mergeChildIds(String[] resolved, List<String> unresolved, int maxItems) {
		if (unresolved instanceof ChildOrderSnapshot snapshot) {
			String[] merged = mergeChildIds(resolved, snapshot.unresolvedIds,
					snapshot.orderedIds, maxItems);
			if (snapshot != ChildOrderSnapshot.EMPTY) snapshot.update(merged, resolved);
			return merged;
		}
		return mergeChildIds(resolved, unresolved, unresolved, maxItems);
	}

	static String[] mergeChildIds(String[] resolved, List<String> unresolved,
			List<String> storedOrder, int maxItems) {
		if (maxItems < 0) throw new IllegalArgumentException("maxItems");
		if (maxItems == 0) return new String[0];

		LinkedHashSet<String> resolvedIds = new LinkedHashSet<>();
		for (String id : resolved) resolvedIds.add(id);
		LinkedHashSet<String> unresolvedIds = new LinkedHashSet<>(unresolved);
		unresolvedIds.removeAll(resolvedIds);

		Map<String, List<String>> before = new LinkedHashMap<>();
		List<String> trailing = new ArrayList<>();
		Set<String> assigned = new HashSet<>();
		for (int i = 0; i < storedOrder.size(); i++) {
			String id = storedOrder.get(i);
			if (!unresolvedIds.contains(id) || !assigned.add(id)) continue;

			String anchor = null;
			for (int j = i + 1; j < storedOrder.size(); j++) {
				String candidate = storedOrder.get(j);
				if (unresolvedIds.contains(candidate)) continue;
				if (resolvedIds.contains(candidate)) {
					anchor = candidate;
					break;
				}
			}

			if (anchor == null) trailing.add(id);
			else before.computeIfAbsent(anchor, key -> new ArrayList<>()).add(id);
		}
		for (String id : unresolvedIds) if (assigned.add(id)) trailing.add(id);

		LinkedHashSet<String> result = new LinkedHashSet<>(Math.min(maxItems,
				resolvedIds.size() + unresolvedIds.size()));
		for (String id : resolvedIds) {
			List<String> placeholders = before.get(id);
			if (placeholders != null) {
				for (String placeholder : placeholders) {
					if (result.size() == maxItems) return result.toArray(new String[0]);
					result.add(placeholder);
				}
			}
			if (result.size() == maxItems) return result.toArray(new String[0]);
			result.add(id);
		}
		for (String id : trailing) {
			if (result.size() == maxItems) break;
			result.add(id);
		}
		return result.toArray(new String[0]);
	}

	private static final class ChildOrderSnapshot extends AbstractList<String> {
		static final ChildOrderSnapshot EMPTY = new ChildOrderSnapshot(List.of(), List.of());
		volatile List<String> orderedIds;
		volatile List<String> unresolvedIds;

		private ChildOrderSnapshot(List<String> orderedIds, List<String> unresolvedIds) {
			this.orderedIds = orderedIds;
			this.unresolvedIds = unresolvedIds;
		}

		static ChildOrderSnapshot create(List<String> orderedIds, List<String> unresolvedIds) {
			if (orderedIds.isEmpty() && unresolvedIds.isEmpty()) return EMPTY;
			return new ChildOrderSnapshot(List.copyOf(orderedIds), List.copyOf(unresolvedIds));
		}

		void update(String[] merged, String[] resolved) {
			Set<String> resolvedIds = new HashSet<>(List.of(resolved));
			Set<String> previousUnresolved = new HashSet<>(unresolvedIds);
			List<String> nextUnresolved = new ArrayList<>();
			for (String id : merged) {
				if (previousUnresolved.contains(id) && !resolvedIds.contains(id))
					nextUnresolved.add(id);
			}
			orderedIds = List.of(merged.clone());
			unresolvedIds = List.copyOf(nextUnresolved);
		}

		@Override
		public String get(int index) {
			return unresolvedIds.get(index);
		}

		@Override
		public int size() {
			return unresolvedIds.size();
		}
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
