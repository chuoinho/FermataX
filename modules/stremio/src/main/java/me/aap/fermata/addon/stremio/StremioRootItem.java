package me.aap.fermata.addon.stremio;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore.Pref;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.item.StremioProviderItem;
import me.aap.fermata.addon.stremio.runtime.StremioRuntime;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioRootItem extends ExtRoot implements StremioItem {
	public static final String ID = "Stremio";
	static final String SCHEME = "stremio";
	private static final String ACTION_PREFIX = SCHEME + ":action:";
	private static final int MAX_RESOLVE_DEPTH = 8;
	private final StremioAddon addon;
	private final StremioItemGateway fixedGateway;
	private final EnumMap<StremioAction, StremioActionItem> actions =
			new EnumMap<>(StremioAction.class);
	private AutoCloseable sourceObserver;

	StremioRootItem(DefaultMediaLib lib) {
		this(null, lib, null);
	}

	StremioRootItem(StremioAddon addon, DefaultMediaLib lib) {
		this(addon, lib, null);
	}

	StremioRootItem(DefaultMediaLib lib, StremioItemGateway gateway) {
		this(null, lib, gateway);
	}

	private StremioRootItem(StremioAddon addon, DefaultMediaLib lib,
			StremioItemGateway fixedGateway) {
		super(ID, lib, AddonCapability.STREMIO);
		this.addon = addon;
		this.fixedGateway = fixedGateway;
		for (StremioAction action : StremioAction.values()) {
			actions.put(action, new StremioActionItem(this, action));
		}
	}

	@Nullable
	FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		if (!SCHEME.equals(scheme)) return null;
		for (StremioActionItem item : actions.values()) {
			if (item.getId().equals(id)) return completed(item);
		}
		if (!isChildItemId(id)) return completedNull();
		return findItem(this, id, MAX_RESOLVE_DEPTH, new HashSet<>());
	}

	static String actionId(StremioAction action) {
		return ACTION_PREFIX + action.name().toLowerCase(java.util.Locale.ROOT);
	}

	boolean isChildItemId(String id) {
		return ID.equals(id) || id.startsWith(ACTION_PREFIX) ||
				StremioItemIds.isStremioId(id);
	}

	void bind(StremioRuntime runtime) {
		closeObserver();
		sourceObserver = runtime.sources().observe(snapshot -> invalidateChildrenCache());
		invalidateChildrenCache();
	}

	void close() {
		closeObserver();
		invalidateChildrenCache();
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(R.string.stremio_title));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
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
	public float getFloatPref(Pref<? extends DoubleSupplier> pref) {
		if (me.aap.fermata.media.pref.MediaPrefs.SUB_SIZE.getName().equals(pref.getName())) {
			return StremioAddon.subtitleSize();
		}
		return super.getFloatPref(pref);
	}

	@Override
	public String getStringPref(Pref<? extends Supplier<String>> pref) {
		if (me.aap.fermata.media.pref.MediaPrefs.SUB_LANG.getName().equals(pref.getName())) {
			return StremioAddon.preferredSubtitlePattern();
		}
		return super.getStringPref(pref);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		if (fixedGateway != null) return providerItems(fixedGateway);
		if ((addon == null) || (getLib() == null)) {
			return completed(new ArrayList<>(actions.values()));
		}
		return addon.getGraph(getLib()).then(graph -> providerItems(graph.items()));
	}

	private FutureSupplier<List<Item>> providerItems(StremioItemGateway gateway) {
		return gateway.providers().map(providers -> {
			List<Item> children = new ArrayList<>(actions.size() + providers.size());
			children.addAll(actions.values());
			for (var provider : providers) {
				if (provider.enabled()) {
					children.add(new StremioProviderItem(this, this, gateway, provider));
				}
			}
			return List.copyOf(children);
		});
	}

	private FutureSupplier<Item> findItem(Item item, String id, int depth,
			Set<String> visited) {
		if (id.equals(item.getId())) return completed(item);
		if ((depth == 0) || !(item instanceof me.aap.fermata.media.lib.MediaLib.BrowsableItem folder) ||
				!visited.add(item.getId())) return completedNull();
		return folder.getUnsortedChildren().then(children ->
				findChildren(children.iterator(), id, depth - 1, visited));
	}

	private FutureSupplier<Item> findChildren(Iterator<Item> children, String id,
			int depth, Set<String> visited) {
		if (!children.hasNext()) return completedNull();
		return findItem(children.next(), id, depth, visited).then(found ->
				(found != null) ? completed(found) : findChildren(children, id, depth, visited));
	}

	private void closeObserver() {
		AutoCloseable observer = sourceObserver;
		sourceObserver = null;
		if (observer == null) return;
		try {
			observer.close();
		} catch (Exception ignored) {
		}
	}
}
