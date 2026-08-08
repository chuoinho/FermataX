package me.aap.fermata.addon.stremio;

import android.content.Context;
import android.os.Build;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AutomotiveShutdownParticipant;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.addon.MediaItemResolverAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.addon.stremio.integration.StremioFutureBridge;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.runtime.StremioRuntime;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeFactory;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeGraph;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioVoiceResult;
import me.aap.fermata.addon.stremio.session.StremioVoiceCandidate;
import me.aap.fermata.addon.stremio.session.StremioItemAvailability;
import me.aap.fermata.addon.stremio.session.StremioItemResolution;
import me.aap.fermata.addon.stremio.source.StremioSourceInput;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.voice.VoiceSession;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Supplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

@Keep
@SuppressWarnings("unused")
public final class StremioAddon implements MediaLibAddon, MediaItemResolverAddon,
		VoiceSearchAddon, AutomotiveShutdownParticipant {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(StremioAddon.class.getName());
	private static final StremioSourceInput CINEMETA = new StremioSourceInput(
			"https://v3-cinemeta.strem.io/manifest.json", null);
	private static final StremioSourceInput OPEN_SUBTITLES = new StremioSourceInput(
			"https://opensubtitles-v3.strem.io/manifest.json", null);
	private static final String DEFAULT_SOURCE_PREFS = "stremio_default_sources";
	private static final String OPEN_SUBTITLES_HANDLED = "opensubtitles_v3_handled";
	private static final Pref<Supplier<String>> SUBTITLE_LANGUAGE = Pref.s(
			"STREMIO_SUBTITLE_LANGUAGE", StremioAddon::applicationLanguageTag);
	private static final Pref<DoubleSupplier> SUBTITLE_SIZE =
			Pref.f("STREMIO_SUBTITLE_SIZE", 1f);
	private StremioRootItem root;
	private CompletableFuture<StremioRuntime> runtimeOpening;
	private CompletableFuture<StremioRuntime> runtimeFuture;
	private long runtimeGeneration;
	private long voiceGeneration;
	private volatile StremioVoiceResult voiceResult;
	private volatile boolean voiceSelectionPlay;
	private final Map<String, Boolean> directFavoriteUpdates = new HashMap<>();

	@Override
	public void contributeSettings(Context context, PreferenceStore store, PreferenceSet set,
			ChangeableCondition visibility) {
		set.addLocalePref(options -> {
			options.store = store;
			options.pref = SUBTITLE_LANGUAGE;
			options.removeDefault = false;
			options.title = R.string.stremio_subtitle_language;
			options.subtitle = me.aap.fermata.R.string.string_format;
			options.formatSubtitle = true;
			options.visibility = visibility;
		});
		set.addFloatPref(options -> {
			options.store = store;
			options.pref = SUBTITLE_SIZE;
			options.title = me.aap.fermata.R.string.subtitles_size;
			options.scale = 0.05f;
			options.seekMin = 10;
			options.seekMax = 40;
			options.visibility = visibility;
		});
	}

	public static List<String> preferredSubtitleLanguages() {
		LinkedHashSet<String> preferred = new LinkedHashSet<>(configuredSubtitleLanguages());
		if (preferred.isEmpty()) {
			Locale locale = Locale.forLanguageTag(applicationLanguageTag());
			if (!locale.toLanguageTag().isBlank()) preferred.add(locale.toLanguageTag());
			if (!locale.getLanguage().isBlank()) preferred.add(locale.getLanguage());
		}
		preferred.add("en");
		return List.copyOf(preferred);
	}

	/** Exact and base language selected in Stremio settings, without fallback languages. */
	public static List<String> configuredSubtitleLanguages() {
		FermataApplication app = FermataApplication.get();
		if ((app == null) || !app.getPreferenceStore().hasPref(SUBTITLE_LANGUAGE)) return List.of();
		String tag = app.getPreferenceStore().getStringPref(SUBTITLE_LANGUAGE);
		if ((tag == null) || tag.isBlank()) tag = applicationLanguageTag();
		Locale locale = Locale.forLanguageTag(tag);
		LinkedHashSet<String> configured = new LinkedHashSet<>();
		if (!locale.toLanguageTag().isBlank()) configured.add(locale.toLanguageTag());
		if (!locale.getLanguage().isBlank()) configured.add(locale.getLanguage());
		return List.copyOf(configured);
	}

	public static String preferredSubtitlePattern() {
		return String.join(",", preferredSubtitleLanguages());
	}

	public static float subtitleSize() {
		FermataApplication app = FermataApplication.get();
		return (app == null) ? 1f : app.getPreferenceStore().getFloatPref(SUBTITLE_SIZE);
	}

	private static String applicationLanguageTag() {
		FermataApplication app = FermataApplication.get();
		if (app == null) return Locale.getDefault().toLanguageTag();
		var configuration = app.getResources().getConfiguration();
		Locale locale = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ?
				configuration.getLocales().get(0) : configuration.locale;
		String tag = locale.toLanguageTag();
		return tag.isBlank() ? Locale.ENGLISH.toLanguageTag() : tag;
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.stremio_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return StremioSessionCoordinator.VOICE_TARGET;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new StremioFragment();
	}

	@Override
	public boolean isSupportedItem(Item item) {
		return (item instanceof StremioItem) || StremioItemIds.isStremioId(item.getId());
	}

	@Override
	public synchronized StremioRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) {
			if (root != null) root.close();
			root = new StremioRootItem(this, lib);
			if (lib != null) {
				StremioRootItem current = root;
				getRuntime(lib).whenComplete((runtime, error) -> {
					if ((error == null) && isCurrentRoot(current, lib)) current.bind(runtime);
				});
			}
		}
		return root;
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
			String id) {
		StremioRootItem currentRoot = getRootItem(lib);
		if (!StremioRootItem.SCHEME.equals(scheme) ||
				!id.startsWith("stremio:video:")) {
			return currentRoot.getItem(scheme, id);
		}
		return getGraph(lib).then(graph ->
				graph.sessionItems().resolveMediaItem(lib, currentRoot, id));
	}

	@Override
	public FutureSupplier<Boolean> shouldRetainMissingItem(DefaultMediaLib lib,
			@Nullable String scheme, String id) {
		if (!StremioRootItem.SCHEME.equals(scheme) || !id.startsWith("stremio:video:")) {
			return me.aap.utils.async.Completed.completed(false);
		}
		return getGraph(lib).then(graph -> StremioFutureBridge.from(graph.sessions()
				.resolveStableItem(id).thenApply(StremioAddon::retainMissingResolution)));
	}

	static boolean retainMissingResolution(StremioItemResolution resolution) {
		return (resolution.availability() == StremioItemAvailability.AVAILABLE) ||
				(resolution.availability() == StremioItemAvailability.PROVIDER_DISABLED);
	}

	@Override
	public void onFavoriteChanged(PlayableItem item, boolean favorite) {
		if ((item == null) || !StremioItemIds.isStremioId(item.getOrigId())) return;
		synchronized (this) {
			Boolean direct = directFavoriteUpdates.get(item.getOrigId());
			if (Objects.equals(direct, favorite)) {
				directFavoriteUpdates.remove(item.getOrigId());
				return;
			}
		}
		if (!(item.getLib() instanceof DefaultMediaLib lib)) return;
		getRootItem(lib);
		FutureSupplier<Void> sync = getGraph(lib).then(graph -> StremioFutureBridge.from(
				graph.sessions().synchronizeFavorite(item.getOrigId(), favorite)));
		sync.onFailure(error -> rollbackFavoriteCollection(lib, item, favorite));
	}

	FutureSupplier<Void> setFavorite(DefaultMediaLib lib, PlayableItem item, boolean favorite) {
		String stableId = item.getOrigId();
		if (!StremioItemIds.isStremioId(stableId)) {
			return me.aap.utils.async.Completed.failed(
					new IllegalArgumentException("Not a Stremio favorite"));
		}
		synchronized (this) {
			directFavoriteUpdates.put(stableId, favorite);
		}
		Favorites favorites = lib.getFavorites();
		FutureSupplier<Void> collectionUpdate = favorite ?
				favorites.addItem(item) : favorites.removeItem(item);
		FutureSupplier<Void> result = synchronizeFavoriteTransaction(collectionUpdate,
				() -> getGraph(lib).then(graph -> StremioFutureBridge.from(
						graph.sessions().synchronizeFavorite(stableId, favorite))),
				() -> rollbackFavoriteCollection(lib, item, favorite));
		result.onCompletion((ignored, error) -> {
			clearDirectFavoriteUpdate(stableId, favorite);
		});
		return result;
	}

	static FutureSupplier<Void> synchronizeFavoriteTransaction(
			FutureSupplier<Void> collectionUpdate,
			java.util.function.Supplier<FutureSupplier<Void>> synchronize,
			java.util.function.Supplier<FutureSupplier<Void>> rollback) {
		Promise<Void> result = new Promise<>();
		collectionUpdate.onCompletion((ignored, collectionError) -> {
			if (collectionError != null) {
				result.completeExceptionally(collectionError);
				return;
			}
			final FutureSupplier<Void> sync;
			try {
				sync = Objects.requireNonNull(synchronize.get(), "favorite synchronization");
			} catch (Throwable error) {
				completeFavoriteRollback(result, error, rollback);
				return;
			}
			sync.onCompletion((value, syncError) -> {
				if (syncError == null) result.complete(null);
				else completeFavoriteRollback(result, syncError, rollback);
			});
		});
		return result;
	}

	private static void completeFavoriteRollback(Promise<Void> result, Throwable syncError,
			java.util.function.Supplier<FutureSupplier<Void>> rollback) {
		final FutureSupplier<Void> compensation;
		try {
			compensation = Objects.requireNonNull(rollback.get(), "favorite rollback");
		} catch (Throwable rollbackError) {
			if (rollbackError != syncError) syncError.addSuppressed(rollbackError);
			result.completeExceptionally(syncError);
			return;
		}
		compensation.onCompletion((ignored, rollbackError) -> {
			if ((rollbackError != null) && (rollbackError != syncError)) {
				syncError.addSuppressed(rollbackError);
			}
			result.completeExceptionally(syncError);
		});
	}

	private FutureSupplier<Void> rollbackFavoriteCollection(DefaultMediaLib lib,
			PlayableItem item, boolean attemptedFavorite) {
		String stableId = item.getOrigId();
		boolean restoredFavorite = !attemptedFavorite;
		synchronized (this) {
			directFavoriteUpdates.put(stableId, restoredFavorite);
		}
		Favorites favorites = lib.getFavorites();
		FutureSupplier<Void> rollback = restoredFavorite ?
				favorites.addItem(item) : favorites.removeItem(item);
		rollback.onCompletion((ignored, error) ->
				clearDirectFavoriteUpdate(stableId, restoredFavorite));
		return rollback;
	}

	private synchronized void clearDirectFavoriteUpdate(String stableId, boolean value) {
		if (Objects.equals(directFavoriteUpdates.get(stableId), value)) {
			directFavoriteUpdates.remove(stableId);
		}
	}

	@Override
	public boolean handleVoiceSearch(MainActivityDelegate activity, String query, boolean play) {
		if ((activity == null) || (query == null) || query.isBlank()) return false;
		long searchGeneration = beginVoiceRequest();
		DefaultMediaLib lib = (DefaultMediaLib) activity.getLib();
		StremioRootItem currentRoot = getRootItem(lib);
		getGraph(lib).main(activity.getHandler()).onSuccess(graph -> {
			if (!isCurrentVoiceRequest(searchGeneration, activity)) return;
			graph.sessions().searchVoice(query, activity.getPrefs().getLocalePref())
				.whenComplete((result, error) -> activity.post(() -> {
					if (!isCurrentVoiceRequest(searchGeneration, activity)) return;
					if ((error != null) || (result == null) || result.choices().isEmpty()) {
						voiceResult = null;
						voiceSelectionPlay = false;
						return;
					}
					voiceResult = result;
					if (activity.getActiveFragment() instanceof StremioFragment fragment) {
						fragment.showSearchResults(query);
					}
					if (result.choices().size() == 1) {
						voiceResult = null;
						graph.sessions().selectVoiceResult(result, 1)
								.whenComplete((resolution, selectionError) -> activity.post(() -> {
									if (!isCurrentVoiceRequest(searchGeneration, activity)) return;
									if ((selectionError == null) && (resolution != null) &&
											resolution.isAvailable()) {
										openVoiceItem(activity, graph.sessionItems()
												.resolveMediaItem(lib, currentRoot,
														result.choices().get(0).stableId()), play);
									}
								}));
						return;
					}
					voiceSelectionPlay = play;
					List<VoiceSession.Option> options = new ArrayList<>(result.choices().size());
					for (StremioVoiceCandidate candidate : result.choices()) {
						options.add(new VoiceSession.Option(candidate.stableId(), candidate.title(),
								candidate.subtitle(), getVoiceTarget()));
					}
					activity.beginVoiceSelectionOptions(options);
				}));
		});
		return true;
	}

	@Override
	public boolean resolveVoiceSelection(MainActivityDelegate activity, String stableId) {
		if ((activity == null) || !StremioItemIds.isStremioId(stableId)) return false;
		StremioVoiceResult pendingResult = voiceResult;
		boolean pendingPlay = voiceSelectionPlay;
		long selectionGeneration = beginVoiceRequest();
		DefaultMediaLib lib = (DefaultMediaLib) activity.getLib();
		StremioRootItem currentRoot = getRootItem(lib);
		getGraph(lib).main(activity.getHandler()).onSuccess(graph -> {
			if (!isCurrentVoiceRequest(selectionGeneration, activity)) return;
			int selection = choiceIndex(pendingResult, stableId);
			var selected = (selection > 0) ? graph.sessions().selectVoiceResult(
					pendingResult, selection) :
					graph.sessions().resolveStableItem(stableId);
			selected.whenComplete((resolution, error) -> {
				voiceResult = null;
			if (!isCurrentVoiceRequest(selectionGeneration, activity)) return;
			if ((error == null) && (resolution != null) && resolution.isAvailable()) {
				openVoiceItem(activity, graph.sessionItems()
						.resolveMediaItem(lib, currentRoot, stableId), pendingPlay);
				return;
			}
			FutureSupplier<? extends Item> current = currentRoot.getItem(
					StremioRootItem.SCHEME, stableId);
			if (current != null) openVoiceItem(activity, current, pendingPlay);
		});
		});
		return true;
	}

	private static void openVoiceItem(MainActivityDelegate activity,
			FutureSupplier<? extends Item> itemFuture, boolean play) {
		itemFuture.main(activity.getHandler()).onSuccess(item -> {
			if (item == null) return;
			if (play && (item instanceof PlayableItem playable)) {
				activity.getMediaServiceBinder().playItem(playable);
			}
			activity.goToItem(item);
		});
	}

	private static int choiceIndex(StremioVoiceResult result, String stableId) {
		if (result == null) return -1;
		for (int i = 0; i < result.choices().size(); i++) {
			if (stableId.equals(result.choices().get(i).stableId())) return i + 1;
		}
		return -1;
	}

	@Override
	public synchronized void stop() {
		runtimeGeneration++;
		voiceGeneration++;
		CompletableFuture<StremioRuntime> opening = runtimeOpening;
		runtimeOpening = null;
		CompletableFuture<StremioRuntime> future = runtimeFuture;
		runtimeFuture = null;
		if (root != null) root.close();
		root = null;
		voiceResult = null;
		voiceSelectionPlay = false;
		directFavoriteUpdates.clear();
		closeWhenReady(opening);
		if (future != opening) closeWhenReady(future);
	}

	@Override
	public void onAutomotiveShutdown() {
		stop();
	}

	FutureSupplier<StremioRuntimeGraph> getGraph(DefaultMediaLib lib) {
		return StremioFutureBridge.from(getRuntime(lib).thenApply(StremioRuntime::graph));
	}

	private synchronized CompletableFuture<StremioRuntime> getRuntime(DefaultMediaLib lib) {
		if (runtimeFuture != null) return runtimeFuture;
		long expectedGeneration = runtimeGeneration;
		CompletableFuture<StremioRuntime> opening = StremioRuntimeFactory.open(
				lib.getContext(), NetworkConsent.STRICT);
		runtimeOpening = opening;
		runtimeFuture = opening;
		CompletableFuture<StremioRuntime> current = runtimeFuture;
		current.whenComplete((runtime, error) -> {
			boolean active;
			synchronized (this) {
				active = (runtimeGeneration == expectedGeneration) && (runtimeFuture == current);
				if (active) runtimeOpening = null;
			}
			if (!active) {
				if (runtime != null) runtime.closeAsync();
				return;
			}
			// Optional provider bootstrap must never delay root binding or first navigation.
			if ((error == null) && (runtime != null)) {
				initializeCinemeta(runtime);
				initializeOpenSubtitles(lib.getContext(), runtime);
			}
		});
		return current;
	}

	private static CompletableFuture<StremioRuntime> initializeCinemeta(StremioRuntime runtime) {
		return runtime.sources().initializeCinemeta(
				AddonManager.get().isFreshInstall(), CINEMETA).handle((outcome, error) -> runtime);
	}

	private static CompletableFuture<StremioRuntime> initializeOpenSubtitles(
			Context context, StremioRuntime runtime) {
		var preferences = context.getSharedPreferences(DEFAULT_SOURCE_PREFS,
				Context.MODE_PRIVATE);
		if (preferences.getBoolean(OPEN_SUBTITLES_HANDLED, false)) {
			return CompletableFuture.completedFuture(runtime);
		}
		return runtime.sources().add(OPEN_SUBTITLES).handle((outcome, error) -> {
			if ((error == null) && StremioDefaultSourcePolicy.marksHandled(outcome)) {
				preferences.edit().putBoolean(OPEN_SUBTITLES_HANDLED, true).commit();
			}
			return runtime;
		});
	}

	private synchronized long beginVoiceRequest() {
		voiceResult = null;
		voiceSelectionPlay = false;
		return ++voiceGeneration;
	}

	private synchronized boolean isCurrentVoiceRequest(long generation,
			MainActivityDelegate activity) {
		return (voiceGeneration == generation) &&
				(activity.getActiveFragment() instanceof StremioFragment);
	}

	private static void closeWhenReady(CompletableFuture<StremioRuntime> future) {
		if (future == null) return;
		future.whenComplete((runtime, error) -> {
			if (runtime != null) runtime.closeAsync();
		});
	}

	private synchronized boolean isCurrentRoot(StremioRootItem candidate, DefaultMediaLib lib) {
		return (root == candidate) && (candidate.getLib() == lib) && (runtimeFuture != null);
	}

}
