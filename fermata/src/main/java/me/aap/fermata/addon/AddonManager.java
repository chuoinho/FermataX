package me.aap.fermata.addon;

import static java.util.Collections.singletonList;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.lang.reflect.InvocationTargetException;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.backup.BackupContributor;
import me.aap.fermata.backup.BackupException;
import me.aap.fermata.addon.external.ExternalPlaybackHandler;
import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.addon.external.ExternalPlaybackRouter;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.AsyncOperationController.DiagnosticsObserver;
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
	private final Set<String> failedRetries = new HashSet<>();
	private final AddonLifecycleCoordinator lifecycle = new AddonLifecycleCoordinator(
			command -> FermataApplication.get().getHandler().post(command));
	private final AddonLoader loader = new AddonLoader(state, lifecycle);
	private final AddonModuleController modules = new AddonModuleController(state, this::requestAddonLoad,
			this::isRetained, this::isModuleRetained);
	private final AddonLauncher launcher = new AddonLauncher(this);
	private final DeferredMediaItemResolver deferredItemResolver = new DeferredMediaItemResolver();
	private final Map<String, Long> diagnosticLoadOperations = new HashMap<>();
	private final PreferenceStore store;
	private final boolean freshInstall;

	public AddonManager(PreferenceStore store) {
		this.store = store;
		freshInstall = enableAddonsByDefault(store);

		for (AddonInfo i : registry.getAvailable()) {
			if (!store.getBooleanPref(i.enabledPref)) continue;
			install(i);
		}

		store.addBroadcastListener(this);
	}

	static boolean enableAddonsByDefault(PreferenceStore store) {
		return AddonPreferenceMigrator.enableDefaults(store, registry.getAvailable());
	}

	public static AddonManager get() {
		return FermataApplication.get().getAddonManager();
	}

	public boolean isFreshInstall() {
		return freshInstall;
	}

	@Nullable
	public synchronized FermataAddon getAddon(String moduleOrClassName) {
		return state.get(moduleOrClassName);
	}

	public synchronized List<AddonInfo> getAddonInfos() {
		return registry.getAvailable();
	}

	@Nullable
	public synchronized AddonInfo getVoiceAddonInfo(String target) {
		AddonInfo info = findVoiceAddonInfo(target);
		return ((info != null) && store.getBooleanPref(info.enabledPref)) ? info : null;
	}

	/** Finds declared voice metadata without silently treating a disabled addon as unknown. */
	@Nullable
	public synchronized AddonInfo findVoiceAddonInfo(String target) {
		if ((target == null) || target.isBlank()) return null;
		for (AddonInfo info : registry.getAvailable()) {
			if (!info.hasCapability(AddonCapability.VOICE_SEARCH) ||
					!target.equals(info.voiceTarget)) continue;
			return info;
		}
		return null;
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
		AddonInfo info = registry.getAvailable(moduleOrClassName);
		boolean retry = (info != null) && state.isFailed(info) && isRetained(info) &&
				failedRetries.add(info.className);
		if (retry) state.clearFailed(info);

		FutureSupplier<FermataAddon> result = launcher.getOrInstallAddon(moduleOrClassName);
		if (retry) result.onCompletion((addon, error) -> {
			synchronized (AddonManager.this) {
				failedRetries.remove(info.className);
			}
		});
		return result;
	}

	public synchronized boolean hasUnresolvedEnabledAddons() {
		for (AddonInfo info : registry.getAvailable()) {
			if (!store.getBooleanPref(info.enabledPref)) continue;
			AddonState addonState = getAddonState(info);
			if ((addonState == AddonState.LOADING) ||
					(addonState == AddonState.ENABLED_PENDING) ||
					(addonState == AddonState.FAILED)) return true;
		}
		return false;
	}

	/**
	 * Returns true when an item belongs to an addon resolver that may be unavailable temporarily.
	 * This lets persisted Recent entries survive a disabled or not-yet-delivered dynamic addon.
	 */
	public synchronized boolean isDeferredItemId(String id) {
		if ((id == null) || id.isBlank()) return false;
		int separator = id.indexOf(':');
		if (separator <= 0) return false;
		String scheme = id.substring(0, separator);
		for (AddonInfo info : registry.getAvailable()) {
			if (info.hasResolverScheme(scheme) && (getAddonState(info) != AddonState.LOADED))
				return true;
		}
		return false;
	}

	/**
	 * Resolves the availability boundary for a persisted dynamic-addon item. Loaded addons remain on
	 * the existing synchronous resolver path; enabled unloaded addons join one delivery operation.
	 */
	public DeferredMediaItemResult resolveDeferredItem(DefaultMediaLib lib,
			@Nullable String scheme, String id) {
		if ((scheme == null) || scheme.isBlank()) return DeferredMediaItemResult.notHandled();

		AddonInfo resolverInfo = null;
		AddonState resolverState = null;
		synchronized (this) {
			for (AddonInfo info : registry.getAvailable()) {
				if (!info.hasResolverScheme(scheme)) continue;
				resolverInfo = info;
				resolverState = getAddonState(info);
				break;
			}
		}
		if (resolverInfo == null) return DeferredMediaItemResult.notHandled();

		AddonInfo info = resolverInfo;
		return deferredItemResolver.resolve(info.className, resolverState,
				() -> requestResolverDelivery(info),
				() -> resolveDeliveredItem(info, lib, scheme, id));
	}

	/** Queries a loaded resolver before a persisted item is pruned as permanently missing. */
	public FutureSupplier<Boolean> shouldRetainMissingItem(DefaultMediaLib lib, String id) {
		if ((id == null) || id.isBlank()) return me.aap.utils.async.Completed.completed(false);
		int separator = id.indexOf(':');
		if (separator <= 0) return me.aap.utils.async.Completed.completed(false);
		String scheme = id.substring(0, separator);
		MediaItemResolverAddon resolver = null;
		synchronized (this) {
			for (AddonInfo info : registry.getAvailable()) {
				if (!info.hasResolverScheme(scheme) ||
						(getAddonState(info) != AddonState.LOADED)) continue;
				FermataAddon addon = state.get(info.className);
				if (addon instanceof MediaItemResolverAddon itemResolver) resolver = itemResolver;
				break;
			}
		}
		if (resolver == null) return me.aap.utils.async.Completed.completed(false);
		try {
			FutureSupplier<Boolean> result = resolver.shouldRetainMissingItem(lib, scheme, id);
			if (result == null) return me.aap.utils.async.Completed.completed(false);
			return result.ifFail(failure -> {
				Log.e(failure, "Failed to query missing item retention: ", id);
				return true;
			});
		} catch (Throwable failure) {
			Log.e(failure, "Failed to query missing item retention: ", id);
			return me.aap.utils.async.Completed.completed(true);
		}
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public synchronized <A extends FermataAddon> A getAddon(Class<A> c) {
		return (A) state.get(c.getName());
	}

	public synchronized Collection<FermataAddon> getAddons() {
		return state.getAll();
	}

	synchronized List<SmartTopProviderLease> snapshotSmartTopProviderLeases() {
		List<SmartTopProviderLease> result = new ArrayList<>();
		for (FermataAddon addon : state.getAll()) {
			if (!(addon instanceof SmartTopProvider)) continue;
			AddonInfo info = addon.getInfo();
			AddonLifecycleCoordinator.LifecycleToken token = lifecycle.getToken(addon);
			if ((token == null) || !state.isLoaded(info, addon) ||
					!store.getBooleanPref(info.enabledPref)) continue;
			result.add(new SmartTopProviderLease(addon, token.generation()));
		}
		result.sort(Comparator.comparing(SmartTopProviderLease::addonClass));
		return List.copyOf(result);
	}

	synchronized boolean ownsSmartTopProviderLease(SmartTopProviderLease lease) {
		if (lease == null) return false;
		FermataAddon addon = lease.addon();
		if (!(addon instanceof SmartTopProvider) ||
				!lease.addonClass().equals(addon.getClass().getName())) return false;
		AddonInfo info = addon.getInfo();
		AddonLifecycleCoordinator.LifecycleToken token = lifecycle.getToken(addon);
		return (token != null) && (token.addon() == addon) &&
				(token.generation() == lease.lifecycleGeneration()) &&
				state.isLoaded(info, addon) && store.getBooleanPref(info.enabledPref);
	}

	boolean acceptsSmartTopCandidate(SmartTopProviderLease lease,
			@Nullable SmartTopCandidate candidate) {
		return (candidate != null) && ownsSmartTopProviderLease(lease) &&
				lease.addonClass().equals(candidate.addonClass()) &&
				(lease.lifecycleGeneration() == candidate.lifecycleGeneration());
	}

	FutureSupplier<List<SmartTopCandidate>> loadSmartTopCandidates(
			SmartTopProviderLease lease) {
		SmartTopProvider provider;
		synchronized (this) {
			if (!ownsSmartTopProviderLease(lease)) return me.aap.utils.async.Completed.completed(List.of());
			provider = (SmartTopProvider) lease.addon();
		}
		try {
			FutureSupplier<List<SmartTopCandidate>> candidates =
					provider.loadSmartTopCandidates(lease);
			return (candidates == null) ? me.aap.utils.async.Completed.completed(List.of()) : candidates;
		} catch (Throwable failure) {
			Log.e(failure, "SmartTop provider load failed: ", lease.addonClass());
			return me.aap.utils.async.Completed.completed(List.of());
		}
	}

	FutureSupplier<PlayableItem> resolveSmartTopCandidate(DefaultMediaLib lib,
			SmartTopProviderLease lease, SmartTopCandidate candidate) {
		SmartTopProvider provider;
		synchronized (this) {
			if (!acceptsSmartTopCandidate(lease, candidate)) return completedNull();
			provider = (SmartTopProvider) lease.addon();
		}
		try {
			FutureSupplier<PlayableItem> resolved = provider.resolveSmartTopCandidate(
					lib, lease, candidate.opaqueId());
			if (resolved == null) return completedNull();
			return resolved.map(item -> {
				if ((item == null) || !acceptsSmartTopCandidate(lease, candidate)) return null;
				return item;
			});
		} catch (Throwable failure) {
			Log.e(failure, "SmartTop provider resolution failed: ", lease.addonClass());
			return completedNull();
		}
	}

	/**
	 * Snapshot of installed addon-owned portable configuration providers. Disabled addons are
	 * instantiated without install/start lifecycle callbacks so their retained configuration is not
	 * silently omitted from a full backup.
	 */
	public synchronized List<BackupContributor> getBackupContributors() throws BackupException {
		List<BackupContributor> result = new ArrayList<>();
		for (AddonInfo info : registry.getAvailable()) {
			FermataAddon addon = state.getRegistered(info.className);
			if (addon instanceof BackupContributor contributor) {
				result.add(contributor);
				continue;
			}
			try {
				Class<?> type = Class.forName(info.className, false,
						AddonManager.class.getClassLoader());
				if (!BackupContributor.class.isAssignableFrom(type)) continue;
				Object instance = type.getDeclaredConstructor().newInstance();
				result.add((BackupContributor) instance);
			} catch (ClassNotFoundException ignored) {
				// An undelivered dynamic feature has no executable contributor to invoke.
			} catch (ReflectiveOperationException | LinkageError failure) {
				Throwable cause = (failure instanceof InvocationTargetException invocation &&
						(invocation.getCause() != null)) ? invocation.getCause() : failure;
				throw new BackupException(BackupException.Code.INCOMPLETE_BACKUP,
						"Unable to initialize an installed addon backup contributor", cause);
			}
		}
		return List.copyOf(result);
	}

	public FutureSupplier<PlayableItem> resolveExternalPlayback(DefaultMediaLib lib,
			ExternalPlaybackRequest request) {
		return ExternalPlaybackRouter.route(snapshotExternalPlaybackHandlers(request),
				handler -> isExternalPlaybackHandlerAvailable(handler, request), lib, request);
	}

	private synchronized boolean isExternalPlaybackHandlerAvailable(
			ExternalPlaybackHandler handler, ExternalPlaybackRequest request) {
		AddonInfo info = handler.getInfo();
		return state.isLoaded(info, handler) && store.getBooleanPref(info.enabledPref) &&
				info.hasCapability(request.getTargetKind().getCapability()) &&
				(handler.getExternalPlaybackTargetKind() == request.getTargetKind());
	}

	private synchronized List<ExternalPlaybackHandler> snapshotExternalPlaybackHandlers(
			ExternalPlaybackRequest request) {
		return selectExternalPlaybackHandlers(state.getAll(), addon -> {
			AddonInfo info = addon.getInfo();
			return state.isLoaded(info, addon) && store.getBooleanPref(info.enabledPref);
		}, request);
	}

	static List<ExternalPlaybackHandler> selectExternalPlaybackHandlers(
			Collection<FermataAddon> addons, Predicate<FermataAddon> enabled,
			ExternalPlaybackRequest request) {
		List<ExternalPlaybackHandler> handlers = new ArrayList<>();
		for (FermataAddon addon : addons) {
			if (!(addon instanceof ExternalPlaybackHandler handler) || !enabled.test(addon)) continue;
			AddonInfo info = addon.getInfo();
			if (!info.hasCapability(request.getTargetKind().getCapability()) ||
					(handler.getExternalPlaybackTargetKind() != request.getTargetKind())) continue;
			handlers.add(handler);
		}
		handlers.sort(Comparator.comparingInt(ExternalPlaybackHandler::getExternalPlaybackPriority)
				.thenComparing(handler -> handler.getInfo().className));
		return List.copyOf(handlers);
	}

	public void onFavoriteChanged(PlayableItem item, boolean favorite) {
		notifyFavoriteChanged(snapshotResolverAddons(), item, favorite);
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

	/** Starts a fresh automotive runtime generation without reloading addon settings or modules. */
	public List<String> onAutomotiveSessionStarted() {
		return notifyAutomotiveParticipants(false);
	}

	/** Releases addon-owned runtime resources at a confirmed automotive session boundary. */
	public List<String> onAutomotiveShutdown() {
		return notifyAutomotiveParticipants(true);
	}

	private List<String> notifyAutomotiveParticipants(boolean shutdown) {
		List<FermataAddon> snapshot;
		synchronized (this) {
			snapshot = new ArrayList<>(state.getAll());
		}
		List<String> failures = new ArrayList<>();
		for (FermataAddon addon : snapshot) {
			if (!(addon instanceof AutomotiveShutdownParticipant participant)) continue;
			try {
				if (shutdown) participant.onAutomotiveShutdown();
				else participant.onAutomotiveSessionStarted();
			} catch (Throwable failure) {
				String name = addon.getClass().getName();
				failures.add(name + ':' + failure.getClass().getSimpleName());
				Log.e(failure, shutdown ? "Automotive addon shutdown failed: " :
						"Automotive addon restart failed: ", name);
			}
		}
		return List.copyOf(failures);
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
	public FutureSupplier<? extends Item>
	getItem(DefaultMediaLib lib, @Nullable String scheme, String id) {
		return resolveItem(snapshotResolverAddons(), lib, scheme, id);
	}

	private synchronized List<FermataAddon> snapshotResolverAddons() {
		List<FermataAddon> snapshot = new ArrayList<>();
		for (FermataAddon addon : state.getAll()) {
			if ((addon instanceof MediaLibAddon) || (addon instanceof MediaItemResolverAddon))
				snapshot.add(addon);
		}
		return snapshot;
	}

	static void notifyFavoriteChanged(List<FermataAddon> addons, PlayableItem item,
			boolean favorite) {
		for (FermataAddon addon : addons) {
			if (!(addon instanceof MediaItemResolverAddon resolver)) continue;
			try {
				resolver.onFavoriteChanged(item, favorite);
			} catch (Throwable failure) {
				Log.e(failure, "Addon favorite callback failed: ", addon.getClass().getName());
			}
		}
	}

	@Nullable
	static FutureSupplier<? extends Item> resolveItem(List<FermataAddon> addons,
			DefaultMediaLib lib, @Nullable String scheme, String id) {
		for (FermataAddon addon : addons) {
			try {
				if (addon instanceof MediaLibAddon mediaLibAddon) {
					FutureSupplier<? extends Item> item = mediaLibAddon.getItem(lib, scheme, id);
					if (item != null) return item;
				}
				if (addon instanceof MediaItemResolverAddon resolver) {
					FutureSupplier<? extends Item> item = resolver.getItem(lib, scheme, id);
					if (item != null) return item;
				}
			} catch (Throwable failure) {
				Log.e(failure, "Addon item resolver failed: ", addon.getClass().getName());
			}
		}
		return null;
	}

	private FutureSupplier<?> requestResolverDelivery(AddonInfo info) {
		synchronized (this) {
			// Resolver lookup is not an enable action. A user-disabled addon stays disabled.
			if (!store.getBooleanPref(info.enabledPref)) return completedVoid();
			if (isLoaded(info)) return completedVoid();
			if (state.isFailed(info))
				return failed(new IllegalStateException("Addon failed: " + info.className));

			install(info);
			FutureSupplier<?> installing = getInstallingTask(info);
			if (installing != null) return installing;
			if (isLoaded(info)) return completedVoid();
			if (state.isFailed(info))
				return failed(new IllegalStateException("Addon failed: " + info.className));
			return failed(new IllegalStateException("Addon delivery did not start: " + info.className));
		}
	}

	private FutureSupplier<? extends Item> resolveDeliveredItem(AddonInfo info, DefaultMediaLib lib,
			String scheme, String id) {
		synchronized (this) {
			if (!store.getBooleanPref(info.enabledPref) || !isLoaded(info)) return completedNull();
		}
		FutureSupplier<? extends Item> item = getItem(lib, scheme, id);
		return (item != null) ? item : completedNull();
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
			if (!info.hasCapability(capability) || !isRoutableState(getAddonState(info))) continue;
			if (info.addonId != 0) return info.addonId;
			FermataAddon addon = state.get(info.className);
			if (addon instanceof FermataFragmentAddon fa) return fa.getFragmentId();
		}
		return 0;
	}

	static boolean isRoutableState(AddonState state) {
		return state == AddonState.LOADED;
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
		long operationId = DiagnosticsObserver.nextId();
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.INSTALL_STARTED, i.addonId,
				0L, operationId, null);

		List<AddonInfo> order;
		try {
			// A delivered core addon may contain legacy module-only dependencies with no AddonInfo.
			order = dependencyResolver.resolveInstallOrder(i, loader.isClassAvailable(i));
		} catch (RuntimeException ex) {
			state.markFailed(i);
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.INSTALL_FAILED, i.addonId,
					0L, operationId, ex);
			Log.e(ex, "Failed to resolve addon dependencies: ", i.className);
			return;
		}

		modules.install(order, i);
		FutureSupplier<?> installation = modules.getInstalling(i);
		if (installation == null) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.INSTALL_COMPLETED, i.addonId,
					0L, operationId, null);
		} else {
			installation.onCompletion((ignored, error) -> DiagnosticsObserver.addon(
					installTerminalEvent(error),
					i.addonId, 0L, operationId, error));
		}
	}

	static DiagnosticsObserver.AddonEvent installTerminalEvent(Throwable error) {
		if (error == null) return DiagnosticsObserver.AddonEvent.INSTALL_COMPLETED;
		return isCancellation(error) ? DiagnosticsObserver.AddonEvent.INSTALL_CANCELLED :
				DiagnosticsObserver.AddonEvent.INSTALL_FAILED;
	}

	synchronized void installAddon(AddonInfo i) {
		install(i);
	}

	synchronized FutureSupplier<?> getInstallingTask(AddonInfo i) {
		return modules.getInstalling(i);
	}

	private FutureSupplier<Boolean> requestAddonLoad(AddonInfo info) {
		Promise<Boolean> activation;
		long operationId;
		synchronized (this) {
			if (isLoaded(info)) return me.aap.utils.async.Completed.completed(true);
			activation = activations.get(info.className);
			if (activation != null) return activation;
			if (!isRetained(info)) return me.aap.utils.async.Completed.completed(false);
			activation = new Promise<>();
			activations.put(info.className, activation);
			operationId = DiagnosticsObserver.nextId();
			diagnosticLoadOperations.put(info.className, operationId);
		}
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.LOAD_STARTED, info.addonId,
				0L, operationId, null);

		Promise<Boolean> pending = activation;
		AtomicBoolean loadFailed = new AtomicBoolean();
		FermataAddon addon = loader.load(info,
				(loaded, commit) -> commitAddonLoad(info, pending, commit),
				() -> markAddonLoadFailed(info, pending, loadFailed),
				loaded -> addonReplayCompleted(info, loaded, pending));
		if (addon == null) {
			synchronized (this) {
				activations.remove(info.className, pending);
				diagnosticLoadOperations.remove(info.className);
			}
			if (!loadFailed.get()) {
				DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.LOAD_CANCELLED, info.addonId,
						0L, operationId, null);
			}
			pending.complete(false);
		}
		return pending;
	}

	private synchronized boolean commitAddonLoad(AddonInfo info, Promise<Boolean> activation,
														 Runnable commit) {
		if ((activations.get(info.className) != activation) || !isRetained(info)) return false;
		commit.run();
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.LOAD_COMMITTED, info.addonId,
					0L, diagnosticLoadOperations.getOrDefault(info.className, 0L), null);
		return true;
	}

	private synchronized void markAddonLoadFailed(AddonInfo info,
											 Promise<Boolean> activation,
											 AtomicBoolean failureReported) {
		if (activations.get(info.className) == activation) {
			state.markFailed(info);
			if (failureReported.compareAndSet(false, true)) {
				DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.LOAD_FAILED, info.addonId,
						0L, diagnosticLoadOperations.getOrDefault(info.className, 0L), null);
			}
		}
	}

	private synchronized void addonReplayCompleted(AddonInfo info, FermataAddon addon,
														 Promise<Boolean> activation) {
		if (activations.get(info.className) != activation) return;
		long operationId = diagnosticLoadOperations.getOrDefault(info.className, 0L);
		AddonLifecycleCoordinator.LifecycleToken token = lifecycle.getToken(addon);
		if (!isRetained(info) || !state.isRegistered(info, addon)) {
			activations.remove(info.className, activation);
			diagnosticLoadOperations.remove(info.className);
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.LOAD_FAILED, info.addonId,
					(token == null) ? 0L : token.generation(), operationId, null);
			activation.complete(false);
			if (state.isRegistered(info, addon)) uninstallUnretained(info);
			return;
		}
		state.activate(info, addon);
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.REPLAY_COMPLETED, info.addonId,
				(token == null) ? 0L : token.generation(), operationId, null);
		ItemContainer.invalidateResolvedChildren();
		PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
		fireBroadcastEvent(c -> c.onAddonChanged(this, info, true));
		prefs.fireBroadcastEvent(
				listener -> listener.onPreferenceChanged(prefs, singletonList(info.enabledPref)));
		activations.remove(info.className, activation);
		diagnosticLoadOperations.remove(info.className);
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
		long operationId = DiagnosticsObserver.nextId();
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.UNLOAD_STARTED, i.addonId,
				0L, operationId, null);
		state.clearFailed(i);
		boolean installing = modules.cancelInstall(i);
		Promise<Boolean> activation;
		Long loadOperationId;
		synchronized (this) {
			activation = activations.remove(i.className);
			loadOperationId = diagnosticLoadOperations.remove(i.className);
		}
		if (activation != null) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.LOAD_CANCELLED, i.addonId,
					0L, (loadOperationId == null) ? 0L : loadOperationId, null);
			activation.complete(false);
		}
		var removed = loader.unload(i,
				() -> addonUninstallCompleted(i, installing, operationId));
		if (removed != null) return;
		if (installing && shouldUninstallModule(i, allInfos, this::isRetained)) modules.uninstall(i);
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.UNLOAD_COMPLETED, i.addonId,
				0L, operationId, null);
	}

	private synchronized void addonUninstallCompleted(AddonInfo info, boolean wasInstalling,
			long operationId) {
		ItemContainer.invalidateResolvedChildren();
		if (!isLoaded(info)) {
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			fireBroadcastEvent(c -> c.onAddonChanged(this, info, false));
			prefs.fireBroadcastEvent(
					listener -> listener.onPreferenceChanged(prefs, singletonList(info.enabledPref)));
		}
		if ((!isLoaded(info) || wasInstalling) &&
				shouldUninstallModule(info, allInfos, this::isRetained)) modules.uninstall(info);
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.UNLOAD_COMPLETED, info.addonId,
				0L, operationId, null);
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
