package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;

final class AddonRuntimeState {
	private final AddonRegistry registry;
	private final Map<Object, FermataAddon> index;
	private final List<FermataAddon> addons;
	private final Set<FermataAddon> active = new HashSet<>();
	private final Map<String, FutureSupplier<?>> installing = new HashMap<>();
	private final Set<String> failed = new HashSet<>();

	AddonRuntimeState(AddonRegistry registry) {
		this.registry = registry;
		index = new HashMap<>(registry.size() * 3);
		addons = new ArrayList<>(registry.size());
	}

	@Nullable
	synchronized FermataAddon get(Object key) {
		FermataAddon addon = index.get(key);
		return ((addon != null) && active.contains(addon)) ? addon : null;
	}

	@Nullable
	synchronized FermataAddon getRegistered(Object key) {
		return index.get(key);
	}

	synchronized Collection<FermataAddon> getAll() {
		List<FermataAddon> result = new ArrayList<>(active.size());
		for (FermataAddon addon : addons) {
			if (active.contains(addon)) result.add(addon);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	synchronized <A extends FermataAddon> List<A> getAll(Class<A> type) {
		return (List<A>) CollectionUtils.filter(getAll(), type::isInstance);
	}

	synchronized boolean isLoaded(AddonInfo info) {
		FermataAddon addon = index.get(info.className);
		return (addon != null) && active.contains(addon);
	}

	synchronized boolean isLoaded(AddonInfo info, FermataAddon addon) {
		return (index.get(info.className) == addon) && active.contains(addon);
	}

	synchronized boolean isRegistered(AddonInfo info, FermataAddon addon) {
		return index.get(info.className) == addon;
	}

	synchronized boolean activate(AddonInfo info, FermataAddon addon) {
		return (index.get(info.className) == addon) && active.add(addon);
	}

	synchronized void add(FermataAddon addon) {
		AddonInfo info = addon.getInfo();
		int addonId = info.addonId;
		FermataAddon existing = index.get(info.className);
		if (existing != null) {
			throw new RegistrationException("Addon already loaded: " + info.className);
		}
		if ((addonId != 0) && (index.get(addonId) != null)) {
			throw new RegistrationException("Addon ID already loaded: " + addonId);
		}
		if (registry.isUniqueModule(info) && (index.get(info.moduleName) != null)) {
			throw new RegistrationException("Addon module already loaded: " + info.moduleName);
		}

		addons.add(addon);
		if (addonId != 0) index.put(addonId, addon);
		index.put(info.className, addon);
		if (registry.isUniqueModule(info)) index.put(info.moduleName, addon);
	}

	@Nullable
	synchronized FermataAddon remove(AddonInfo info) {
		FermataAddon addon = index.remove(info.className);
		if (addon == null) return null;
		addons.remove(addon);
		active.remove(addon);
		int addonId = info.addonId;
		if (addonId != 0) index.remove(addonId, addon);
		if (registry.isUniqueModule(info)) index.remove(info.moduleName, addon);
		return addon;
	}

	synchronized boolean isFailed(AddonInfo info) {
		return failed.contains(info.className);
	}

	synchronized void markFailed(AddonInfo info) {
		failed.add(info.className);
	}

	synchronized void clearFailed(AddonInfo info) {
		failed.remove(info.className);
	}

	@Nullable
	synchronized FutureSupplier<?> getInstalling(AddonInfo info) {
		return installing.get(info.className);
	}

	synchronized boolean isInstalling(AddonInfo info) {
		return installing.containsKey(info.className);
	}

	synchronized boolean isInstalling(AddonInfo info, FutureSupplier<?> task) {
		return installing.get(info.className) == task;
	}

	synchronized boolean setInstallingIfAbsent(AddonInfo info, FutureSupplier<?> task) {
		if (installing.containsKey(info.className)) return false;
		installing.put(info.className, task);
		return true;
	}

	synchronized boolean replaceInstalling(AddonInfo info, FutureSupplier<?> expected,
														 FutureSupplier<?> replacement) {
		if (installing.get(info.className) != expected) return false;
		installing.put(info.className, replacement);
		return true;
	}

	synchronized void removeInstalling(AddonInfo info, FutureSupplier<?> task) {
		CollectionUtils.remove(installing, info.className, task);
	}

	static final class RegistrationException extends IllegalStateException {
		RegistrationException(String message) {
			super(message);
		}
	}
}
