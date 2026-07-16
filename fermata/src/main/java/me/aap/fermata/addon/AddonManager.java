package me.aap.fermata.addon;

import static java.util.Collections.singletonList;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class AddonManager extends BasicEventBroadcaster<AddonManager.Listener>
		implements PreferenceStore.Listener {
	private static final AddonRegistry registry = AddonRegistry.get();
	private static final List<AddonInfo> allInfos = Arrays.asList(registry.getAll());
	private final AddonDependencyResolver dependencyResolver = new AddonDependencyResolver(registry);
	private final AddonRuntimeState state = new AddonRuntimeState(registry);
	private final Map<String, Promise<Boolean>> activations = new HashMap<>();
	private final AddonLifecycleCoordinator lifecycle = new AddonLifecycleCoordinator(
			command -> FermataApplication.get().getHandler().post(command));
	private final AddonLoader loader = new AddonLoader(state, lifecycle);
	private final AddonModuleController modules = new AddonModuleController(state, this::requestAddonLoad,
			this::isRetained, this::isModuleRetained);
	private final AddonLauncher launcher = new AddonLauncher(this);
	private final PreferenceStore store;

	public AddonManager(PreferenceStore store) {
		this.store = store;
		enableAddonsByDefault(store);

		for (AddonInfo i : registry.getAvailable()) {
			if (!store.getBooleanPref(i.enabledPref)) continue;
			install(i);
		}

		store.addBroadcastListener(this);
	}

	static void enableAddonsByDefault(PreferenceStore store) {
		AddonPreferenceMigrator.enableDefaults(store, registry.getAvailable());
	}

	public static AddonManager get() {
		return FermataApplication.get().getAddonManager();
	}

	@Nullable
	public synchronized FermataAddon getAddon(String moduleOrClassName) {
		return state.get(moduleOrClassName);
	}

	public synchronized List<AddonInfo> getAddonInfos() {
		return registry.getAvailable();
	}

	@Nullable
	public synchronized AddonInfo getAddonInfo(Object moduleClassOrId) {
		if (moduleClassOrId == null) return null;
		AddonInfo info = registry.getAvailable(moduleClassOrId);
		if (info != null) return info;

		for (AddonInfo i : registry.getAvailable()) {
			if (moduleClassOrId.equals(i.className)) return i;
			FermataAddon a = state.get(i.className);
			if ((a != null) && moduleClassOrId.equals(a.getAddonId())) return i;
		}

		return null;
	}

	public synchronized AddonState getAddonState(AddonInfo i) {
		return resolveState(registry.isAvailable(i), store.getBooleanPref(i.enabledPref), isLoaded(i),
				state.isFailed(i), state.isInstalling(i));
	}

	static AddonState resolveState(boolean available, boolean enabled, boolean loaded,
											 boolean failed, boolean loading) {
		if (!available || !enabled) return AddonState.DISABLED;
		if (loaded) return AddonState.LOADED;
		if (failed) return AddonState.FAILED;
		if (loading) return AddonState.LOADING;
		return AddonState.ENABLED_PENDING;
	}

	public <A extends FermataAddon> FutureSupplier<A> getOrInstallAddon(Class<A> c) {
		return getOrInstallAddon(c.getName()).cast();
	}

	public synchronized FutureSupplier<FermataAddon> getOrInstallAddon(String moduleOrClassName) {
		return launcher.getOrInstallAddon(moduleOrClassName);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public synchronized <A extends FermataAddon> A getAddon(Class<A> c) {
		return (A) state.get(c.getName());
	}

	public synchronized Collection<FermataAddon> getAddons() {
		return state.getAll();
	}

	/**
	 * @noinspection unchecked
	 */
	public synchronized <A extends FermataAddon> List<A> getAddons(Class<A> c) {
		return state.getAll(c);
	}

	public void onActivityCreate(MainActivityDelegate activity) {
		lifecycle.onActivityCreate(activity);
	}

	public void onActivityResume(MainActivityDelegate activity) {
		lifecycle.onActivityResume(activity);
	}

	public void onActivityPause(MainActivityDelegate activity) {
		lifecycle.onActivityPause(activity);
	}

	public void onActivityDestroy(MainActivityDelegate activity) {
		lifecycle.onActivityDestroy(activity);
	}

	public void onServiceCreate(MediaSessionCallback service) {
		lifecycle.onServiceCreate(service);
	}

	public void onServiceDestroy(MediaSessionCallback service) {
		lifecycle.onServiceDestroy(service);
	}

	public synchronized boolean hasAddon(@IdRes int id) {
		return state.get(id) != null;
	}

	@Nullable
	public synchronized ActivityFragment createFragment(@IdRes int id) {
		FermataAddon a = state.get(id);
		if (a instanceof FermataFragmentAddon fa) return fa.createFragment();
		return null;
	}

	@Nullable
	public synchronized FutureSupplier<? extends Item>
	getItem(DefaultMediaLib lib, @Nullable String scheme, String id) {
		for (FermataAddon a : state.getAll()) {
			if (a instanceof MediaLibAddon) {
				FutureSupplier<? extends Item> i = ((MediaLibAddon) a).getItem(lib, scheme, id);
				if (i != null) return i;
			}
		}

		return null;
	}

	@Nullable
	public synchronized MediaLibAddon getMediaLibAddon(Item i) {
		for (FermataAddon a : state.getAll()) {
			if (a instanceof MediaLibAddon mla) {
				if (mla.isSupportedItem(i)) return mla;
			}
		}

		return null;
	}

	@IdRes
	public synchronized int getFragmentId(Item i) {
		MediaLibAddon a = getMediaLibAddon(i);
		if (a == null) return 0;
		AddonInfo info = a.getInfo();
		return (info.addonId != 0) ? info.addonId : a.getFragmentId();
	}

	@IdRes
	public synchronized int getFragmentId(AddonCapability capability) {
		for (AddonInfo info : allInfos) {
			if (!info.hasCapability(capability)) continue;
			if (info.addonId != 0) return info.addonId;
			FermataAddon addon = state.get(info.className);
			if (addon instanceof FermataFragmentAddon fa) return fa.getFragmentId();
		}
		return 0;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		for (AddonInfo i : allInfos) {
			if (prefs.contains(i.enabledPref)) {
				if (store.getBooleanPref(i.enabledPref)) install(i);
				else uninstall(i);
			}
		}
	}

	private synchronized void install(AddonInfo i) {
		if (!isRetained(i)) return;
		if (state.isFailed(i) || isLoaded(i) || state.isInstalling(i)) return;

		List<AddonInfo> order;
		try {
			// A delivered core addon may contain legacy module-only dependencies with no AddonInfo.
			order = dependencyResolver.resolveInstallOrder(i, loader.isClassAvailable(i));
		} catch (RuntimeException ex) {
			state.markFailed(i);
			Log.e(ex, "Failed to resolve addon dependencies: ", i.className);
			return;
		}

		modules.install(order, i);
	}

	synchronized void installAddon(AddonInfo i) {
		install(i);
	}

	synchronized FutureSupplier<?> getInstallingTask(AddonInfo i) {
		return modules.getInstalling(i);
	}

	private FutureSupplier<Boolean> requestAddonLoad(AddonInfo info) {
		Promise<Boolean> activation;
		synchronized (this) {
			if (isLoaded(info)) return me.aap.utils.async.Completed.completed(true);
			activation = activations.get(info.className);
			if (activation != null) return activation;
			if (!isRetained(info)) return me.aap.utils.async.Completed.completed(false);
			activation = new Promise<>();
			activations.put(info.className, activation);
		}

		Promise<Boolean> pending = activation;
		FermataAddon addon = loader.load(info,
				(loaded, commit) -> commitAddonLoad(info, pending, commit),
				() -> markAddonLoadFailed(info, pending),
				loaded -> addonReplayCompleted(info, loaded, pending));
		if (addon == null) {
			synchronized (this) {
				activations.remove(info.className, pending);
			}
			pending.complete(false);
		}
		return pending;
	}

	private synchronized boolean commitAddonLoad(AddonInfo info, Promise<Boolean> activation,
												 Runnable commit) {
		if ((activations.get(info.className) != activation) || !isRetained(info)) return false;
		commit.run();
		return true;
	}

	private synchronized void markAddonLoadFailed(AddonInfo info,
												 Promise<Boolean> activation) {
		if (activations.get(info.className) == activation) state.markFailed(info);
	}

	private synchronized void addonReplayCompleted(AddonInfo info, FermataAddon addon,
															 Promise<Boolean> activation) {
		if (activations.get(info.className) != activation) return;
		if (!isRetained(info) || !state.isRegistered(info, addon)) {
			activations.remove(info.className, activation);
			activation.complete(false);
			if (state.isRegistered(info, addon)) uninstallUnretained(info);
			return;
		}
		state.activate(info, addon);
		PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
		fireBroadcastEvent(c -> c.onAddonChanged(this, info, true));
		prefs.fireBroadcastEvent(
				listener -> listener.onPreferenceChanged(prefs, singletonList(info.enabledPref)));
		activations.remove(info.className, activation);
		activation.complete(true);
	}

	private synchronized void uninstall(AddonInfo i) {
		if (isRetained(i)) return;
		uninstallUnretained(i);
		for (AddonInfo dependency : allInfos) {
			if (!isRetained(dependency) &&
					(isLoaded(dependency) || state.isInstalling(dependency))) {
				uninstallUnretained(dependency);
			}
		}
	}

	private void uninstallUnretained(AddonInfo i) {
		state.clearFailed(i);
		boolean installing = modules.cancelInstall(i);
		Promise<Boolean> activation;
		synchronized (this) {
			activation = activations.remove(i.className);
		}
		if (activation != null) activation.complete(false);
		var removed = loader.unload(i, () -> addonUninstallCompleted(i, installing));
		if (removed != null) return;
		if (installing && shouldUninstallModule(i, allInfos, this::isRetained)) modules.uninstall(i);
	}

	private synchronized void addonUninstallCompleted(AddonInfo info, boolean wasInstalling) {
		if (!isLoaded(info)) {
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			fireBroadcastEvent(c -> c.onAddonChanged(this, info, false));
			prefs.fireBroadcastEvent(
					listener -> listener.onPreferenceChanged(prefs, singletonList(info.enabledPref)));
		}
		if ((!isLoaded(info) || wasInstalling) &&
				shouldUninstallModule(info, allInfos, this::isRetained)) modules.uninstall(info);
	}

	static boolean shouldUninstallModule(AddonInfo removed, Iterable<AddonInfo> infos,
															 Predicate<AddonInfo> retained) {
		return AddonModulePolicy.shouldUninstall(removed, infos, retained);
	}

	private boolean isLoaded(AddonInfo i) {
		return state.isLoaded(i);
	}

	private boolean isRetained(AddonInfo info) {
		return store.getBooleanPref(info.enabledPref) || dependencyResolver.isRequiredBy(info,
				allInfos, candidate -> store.getBooleanPref(candidate.enabledPref));
	}

	private boolean isModuleRetained(String moduleName) {
		for (AddonInfo info : allInfos) {
			if (moduleName.equals(info.moduleName) && isRetained(info)) return true;
		}
		return false;
	}

	public interface Listener {
		void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed);
	}
}
