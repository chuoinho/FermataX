package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.BuildConfig;

public final class AddonRegistry {
	private static final AddonRegistry instance = new AddonRegistry(BuildConfig.ADDONS);
	private final AddonInfo[] all;
	private final List<AddonInfo> available;
	private final Map<String, Integer> moduleCounts;
	private final Map<Object, AddonInfo> allIndex;
	private final Map<Object, AddonInfo> availableIndex;

	AddonRegistry(AddonInfo[] all) {
		this.all = all;
		available = new ArrayList<>(all.length);
		moduleCounts = new HashMap<>();

		for (AddonInfo i : all) {
			moduleCounts.merge(i.moduleName, 1, Integer::sum);
			if (isAvailable(i)) available.add(i);
		}

		allIndex = new HashMap<>(all.length * 3);
		availableIndex = new HashMap<>(available.size() * 3);
		for (AddonInfo i : all) addToIndex(allIndex, i);
		for (AddonInfo i : available) {
			addToIndex(availableIndex, i);
		}
	}

	public static AddonRegistry get() {
		return instance;
	}

	public AddonInfo[] getAll() {
		return all.clone();
	}

	public List<AddonInfo> getAvailable() {
		return new ArrayList<>(available);
	}

	public int size() {
		return all.length;
	}

	public boolean isAvailable(AddonInfo i) {
		return BuildConfig.AUTO || !i.isAuto;
	}

	public boolean isUniqueModule(AddonInfo i) {
		Integer count = moduleCounts.get(i.moduleName);
		return (count != null) && (count == 1);
	}

	@Nullable
	public AddonInfo get(Object key) {
		return (key == null) ? null : allIndex.get(key);
	}

	@Nullable
	public AddonInfo getAvailable(Object key) {
		return (key == null) ? null : availableIndex.get(key);
	}

	public AddonInfo require(String name) {
		AddonInfo i = get(name);
		if (i != null) return i;
		if ((name.indexOf('.') <= 0) && (moduleCounts.getOrDefault(name, 0) > 1)) {
			throw new RuntimeException("Ambiguous addon module: " + name);
		}
		throw new RuntimeException("Addon not found: " + name);
	}

	private void addToIndex(Map<Object, AddonInfo> index, AddonInfo i) {
		index.put(i.className, i);
		if (i.addonId != 0) index.put(i.addonId, i);
		if (isUniqueModule(i)) index.put(i.moduleName, i);
	}
}
