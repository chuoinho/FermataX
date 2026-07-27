package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.BuildConfig.AUTO;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaItemResolverAddon;
import me.aap.fermata.addon.external.ExternalPlaybackHandler;
import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.addon.external.ExternalPlaybackTargetKind;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Recent;
import me.aap.fermata.media.pref.FavoritesPrefs;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Completed;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeAddon extends WebBrowserAddon
		implements PreferenceStore.Listener, MediaItemResolverAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(YoutubeAddon.class.getName());
	public static final int YT_DARK_MODE_DISABLED = 0;
	public static final int YT_DARK_MODE_ENABLED = 1;
	public static final int YT_DARK_MODE_AUTO = 2;
	private static final Pref<IntSupplier> YT_DARK_MODE = Pref.i("YT_DARK_MODE", YT_DARK_MODE_AUTO);
	private static final Pref<BooleanSupplier> YT_DESKTOP_VERSION = Pref.b("YT_DESKTOP_VERSION", false);
	private static final Pref<Supplier<String[]>> YT_BOOKMARKS = Pref.sa("YT_BOOKMARKS");
	private static final Pref<Supplier<String>> VIDEO_SCALE = Pref.s("VIDEO_SCALE", VideoScale.CONTAIN::prefName);
	private static final Pref<Supplier<String>> YT_LAST_URL = Pref.s("YT_LAST_URL", "https://m.youtube.com");
	private static final Pref<BooleanSupplier> YT_SESSION_RESET_PENDING =
			Pref.b("YT_SESSION_RESET_PENDING", false);
	private static final Pref<LongSupplier> YT_SESSION_LEFT_AT =
			Pref.l("YT_SESSION_LEFT_AT", 0L);
	private static final long YT_SESSION_RETENTION_MS = 10L * 60L * 1000L;
	private static final Pref<BooleanSupplier> YT_OPEN_ON_START = Pref.b("YT_OPEN_ON_START", false);
	private static final Pref<BooleanSupplier> YT_AUTO_HIGHEST_QUALITY =
			Pref.b("YT_AUTO_HIGHEST_QUALITY", false);
	private static final Pref<Supplier<String>> YT_ITEM_HISTORY = Pref.s("YT_ITEM_HISTORY", "");
	private static final Pref<Supplier<String>> YT_PINNED_ITEMS = Pref.s("YT_PINNED_ITEMS", "");
	private static final YoutubeRetentionPolicy ITEM_RETENTION =
			new YoutubeRetentionPolicy(10, Long.MAX_VALUE);
	private final SponsorBlockController sponsorBlockController = new SponsorBlockController();
	private static final int HISTORY_ATTACH_MAX_ATTEMPTS = 100;
	private static final long HISTORY_ATTACH_RETRY_MS = 100L;
	private static final long HISTORY_ATTACH_TIMEOUT_MS =
			(HISTORY_ATTACH_MAX_ATTEMPTS * HISTORY_ATTACH_RETRY_MS) + 1_000L;

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "youtube";
	}

	@NonNull
	@Override
	public ExternalPlaybackTargetKind getExternalPlaybackTargetKind() {
		return ExternalPlaybackTargetKind.YOUTUBE_ID;
	}

	@Override
	public int getExternalPlaybackPriority() {
		return 100;
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> createExternalPlaybackItem(DefaultMediaLib lib,
			ExternalPlaybackRequest request) {
		if (request.getTargetKind() != ExternalPlaybackTargetKind.YOUTUBE_ID)
			return ExternalPlaybackHandler.unavailable();
		YoutubeItem descriptor = externalDescriptor(request);
		updateYoutubeItem(descriptor);
		return Completed.completed(new YoutubeHistoryItem(this, lib, descriptor));
	}

	static YoutubeItem externalDescriptor(ExternalPlaybackRequest request) {
		String videoId = request.getTarget();
		return new YoutubeItem(videoId, "https://m.youtube.com/watch?v=" + videoId,
				request.getTitle(), request.getArtworkUri(), request.getDurationMillis(), 0L);
	}

	@Override
	public boolean resolveVoiceSelection(MainActivityDelegate activity, String stableId) {
		if (!(activity.getActiveFragment() instanceof YoutubeFragment fragment)) return false;
		return fragment.playVoiceSelection(stableId);
	}
	private static final Pref<BooleanSupplier> YT_SKIP_ADD = AUTO ? Pref.b("YT_SKIP_ADD", true) : null;
	private final YoutubePlaybackMetadata playbackMetadata = new YoutubePlaybackMetadata();
	private boolean ignorePrefChange;

	@Override
	public void install() {
		reconcilePinnedItems();
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@NonNull
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new YoutubeFragment();
	}

	@Override
	public Pref<IntSupplier> getForceDarkPref() {
		return YT_DARK_MODE;
	}

	@Override
	public Pref<BooleanSupplier> getDesktopVersionPref() {
		return YT_DESKTOP_VERSION;
	}

	@Override
	public Pref<Supplier<String[]>> getBookmarksPref() {
		return YT_BOOKMARKS;
	}

	boolean skipAd() {
		return AUTO && getPreferenceStore().getBooleanPref(YT_SKIP_ADD);
	}

	boolean skipAdChanged(List<Pref<?>> prefs) {
		return AUTO && prefs.contains(YT_SKIP_ADD);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		super.contributeSettings(ctx, store, set, visibility);
		getPreferenceStore().addBroadcastListener(this);
		MainActivityPrefs.get().addBroadcastListener(this);
		FermataApplication.get().getPreferenceStore().addBroadcastListener(this);

		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = YT_AUTO_HIGHEST_QUALITY;
			o.title = R.string.auto_highest_video_quality;
			o.visibility = visibility;
		});

		if (AUTO) {
			set.addBooleanPref(o -> {
				o.store = getPreferenceStore();
				o.pref = YT_SKIP_ADD;
				o.title = R.string.try_to_skip_ad;
				o.visibility = visibility;
			});
		}

		YoutubeSponsorBlock.contributeSettings(getPreferenceStore(), set, visibility);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (ignorePrefChange) return;
		ignorePrefChange = true;

		if (prefs.contains(getInfo().enabledPref)) {
			if (!store.getBooleanPref(getInfo().enabledPref)) {
				MainActivityPrefs ap = MainActivityPrefs.get();
				getPreferenceStore().applyBooleanPref(YT_OPEN_ON_START, false);
				if (getInfo().className.equals(ap.getShowAddonOnStartPref()))
					ap.setShowAddonOnStartPref(null);
			}
		} else if (prefs.contains(YT_OPEN_ON_START)) {
			MainActivityPrefs ap = MainActivityPrefs.get();
			if (store.getBooleanPref(YT_OPEN_ON_START)) {
				ap.setShowAddonOnStartPref(getInfo().className);
			} else if (getInfo().className.equals(ap.getShowAddonOnStartPref())) {
				ap.setShowAddonOnStartPref(null);
			}
		} else if (prefs.contains(MainActivityPrefs.SHOW_ADDON_ON_START)) {
			getPreferenceStore().applyBooleanPref(YT_OPEN_ON_START,
					getInfo().className.equals(MainActivityPrefs.get().getShowAddonOnStartPref()));
		}

		ignorePrefChange = false;
	}

	@Override
	public void uninstall() {
		stopOwnedPlayback();
		getPreferenceStore().removeBroadcastListener(this);
		MainActivityPrefs.get().removeBroadcastListener(this);
		FermataApplication.get().getPreferenceStore().removeBroadcastListener(this);
	}

	private void stopOwnedPlayback() {
		MainActivityDelegate activity = MainActivityDelegate.getActivityDelegate(
				FermataApplication.get()).peek();
		if (activity != null) {
			stopOwnedPlayback(activity.getMediaSessionCallback());
			return;
		}

		FermataMediaServiceConnection.connect(null).main().onSuccess(connection -> {
			MediaSessionCallback callback = connection.getMediaSessionCallback();
			if (callback != null) stopOwnedPlayback(callback);
			connection.disconnect();
		}).onFailure(error -> Log.d(error, "YouTube service unavailable during uninstall"));
	}

	private void stopOwnedPlayback(MediaSessionCallback callback) {
		MediaEngine engine = callback.getEngine();
		if ((engine instanceof YoutubeMediaEngine youtube) && youtube.belongsTo(this)) {
			callback.onStop();
		} else if ((engine instanceof YoutubeDeferredMediaEngine pending) &&
				pending.belongsTo(this)) {
			callback.onStop();
		}
	}

	VideoScale getScale() {
		switch (getPreferenceStore().getStringPref(VIDEO_SCALE)) {
			case "fill":
				return VideoScale.FILL;
			case "contain":
				return VideoScale.CONTAIN;
			case "cover":
				return VideoScale.COVER;
			default:
				return VideoScale.NONE;
		}
	}

	void setScale(VideoScale scale) {
		getPreferenceStore().applyStringPref(VIDEO_SCALE, scale.prefName());
	}

	String getLastYoutubeUrl() {
		return getPreferenceStore().getStringPref(YT_LAST_URL);
	}

	void setLastYoutubeUrl(String url) {
		getPreferenceStore().applyStringPref(YT_LAST_URL, url);
	}

	void markPlaybackSessionLeft(long timestampMillis) {
		PreferenceStore store = getPreferenceStore();
		store.applyLongPref(YT_SESSION_LEFT_AT, Math.max(0L, timestampMillis));
		store.applyBooleanPref(YT_SESSION_RESET_PENDING, true);
	}

	SessionReturnAction consumePlaybackSessionReturn(long timestampMillis) {
		PreferenceStore store = getPreferenceStore();
		if (!store.getBooleanPref(YT_SESSION_RESET_PENDING)) return SessionReturnAction.KEEP;

		long leftAt = store.getLongPref(YT_SESSION_LEFT_AT);
		store.removePref(YT_SESSION_RESET_PENDING);
		store.removePref(YT_SESSION_LEFT_AT);
		long elapsed = timestampMillis - leftAt;
		return ((leftAt > 0L) && (elapsed >= YT_SESSION_RETENTION_MS)) ?
				SessionReturnAction.RESET_HOME : SessionReturnAction.KEEP;
	}

	boolean autoHighestQuality() {
		return getPreferenceStore().getBooleanPref(YT_AUTO_HIGHEST_QUALITY);
	}

	YoutubePlaybackMetadata getPlaybackMetadata() {
		return playbackMetadata;
	}

	void rememberYoutubeItem(YoutubeItem item) {
		if (item == null) return;
		storeYoutubeItem(item.playedAt(System.currentTimeMillis()));
	}

	void updateYoutubeItem(YoutubeItem item) {
		if (item == null) return;
		storeYoutubeItem(item);
	}

	private void storeYoutubeItem(YoutubeItem item) {
		YoutubeItemCodec.DecodeResult storedResult = decodePreference(YT_ITEM_HISTORY);
		if (storedResult.isUnsupported()) {
			Log.w("Keeping unsupported YouTube history format");
			return;
		}
		List<YoutubeItem> stored = new ArrayList<>(storedResult.items());
		YoutubeItem existing = findYoutubeItem(stored, item.videoId());
		YoutubeItem merged = mergeYoutubeItem(existing, item);
		stored.removeIf(candidate -> candidate.videoId().equals(merged.videoId()));
		stored.add(merged);
		List<YoutubeItem> retained = ITEM_RETENTION.retain(stored, System.currentTimeMillis());
		getPreferenceStore().applyStringPref(YT_ITEM_HISTORY,
				YoutubeItemCodec.encode(retained));
		updatePinnedItem(merged);
		updateCachedHistoryItem(merged);

		if (stored.size() == retained.size()) return;
		List<YoutubeItem> evicted = new ArrayList<>();
		for (YoutubeItem candidate : stored) {
			boolean keep = false;
			for (YoutubeItem keepItem : retained) {
				if (candidate.videoId().equals(keepItem.videoId())) {
					keep = true;
					break;
				}
			}
			if (!keep) evicted.add(candidate);
		}
		removeEvictedFromRecent(evicted);
	}

	private void updateCachedHistoryItem(YoutubeItem item) {
		MainActivityDelegate activity = currentActivity();
		if (activity == null) return;
		Item cached = activity.getLib().getCachedItem(item.stableId());
		if (cached instanceof YoutubeHistoryItem history) history.updateDescriptor(item);
	}

	private void removeEvictedFromRecent(List<YoutubeItem> evicted) {
		if (evicted.isEmpty()) return;
		MainActivityDelegate activity = MainActivityDelegate.getActivityDelegate(
				FermataApplication.get()).peek();
		if (activity != null) {
			removeEvictedFromRecent((DefaultMediaLib) activity.getLib(), evicted);
			return;
		}

		FermataMediaServiceConnection.connect(null).main().onSuccess(connection -> {
			MediaSessionCallback callback = connection.getMediaSessionCallback();
			if (callback == null) {
				connection.disconnect();
				return;
			}
			removeEvictedFromRecent((DefaultMediaLib) callback.getMediaLib(), evicted)
					.onCompletion((result, error) -> {
						if (error != null) Log.w(error, "Failed to remove evicted YouTube Recent items");
						connection.disconnect();
					});
		}).onFailure(error -> Log.w(error, "Failed to connect for YouTube Recent cleanup"));
	}

	private FutureSupplier<Void> removeEvictedFromRecent(DefaultMediaLib lib,
																				 List<YoutubeItem> evicted) {
		List<YoutubeItem> current = getYoutubeItems();
		List<String> ids = getStillEvictedIds(current, evicted);
		if (ids.isEmpty()) return Completed.completedVoid();
		Set<String> idSet = Set.copyOf(ids);
		lib.removeCachedItems(item -> idSet.contains(item.getId()) ||
				((item instanceof PlayableItem playable) && idSet.contains(playable.getOrigId())));
		return ((Recent) lib.getRecent()).removeItemsById(ids);
	}

	static List<String> getStillEvictedIds(List<YoutubeItem> current,
			List<YoutubeItem> candidates) {
		List<String> ids = new ArrayList<>(candidates.size());
		for (YoutubeItem item : candidates) {
			if (findYoutubeItem(current, item.videoId()) == null) ids.add(item.stableId());
		}
		return List.copyOf(ids);
	}

	List<YoutubeItem> getYoutubeItems() {
		return decodePreference(YT_ITEM_HISTORY).items();
	}

	private List<YoutubeItem> getPinnedYoutubeItems() {
		return decodePreference(YT_PINNED_ITEMS).items();
	}

	private void updatePinnedItem(YoutubeItem item) {
		YoutubeItemCodec.DecodeResult result = decodePreference(YT_PINNED_ITEMS);
		if (result.isUnsupported()) return;
		List<YoutubeItem> pinned = new ArrayList<>(result.items());
		YoutubeItem existing = findYoutubeItem(pinned, item.videoId());
		if ((existing == null) && !isFavoriteId(item.stableId())) return;
		if (existing == null) {
			pinned.add(item);
			getPreferenceStore().applyStringPref(YT_PINNED_ITEMS, YoutubeItemCodec.encode(pinned));
			return;
		}
		pinned.remove(existing);
		pinned.add(mergeYoutubeItem(existing, item));
		getPreferenceStore().applyStringPref(YT_PINNED_ITEMS, YoutubeItemCodec.encode(pinned));
	}

	@Override
	public void onFavoriteChanged(PlayableItem item, boolean favorite) {
		String id = (item == null) ? "" : item.getOrigId();
		String prefix = "youtube:video:";
		if (!id.startsWith(prefix)) return;
		String videoId = id.substring(prefix.length());
		YoutubeItemCodec.DecodeResult result = decodePreference(YT_PINNED_ITEMS);
		if (result.isUnsupported()) return;
		List<YoutubeItem> pinned = new ArrayList<>(result.items());
		YoutubeItem current = findYoutubeItem(pinned, videoId);
		if (!favorite) {
			if (current == null) return;
			pinned.remove(current);
		} else {
			YoutubeItem descriptor = (item instanceof YoutubeDescriptorItem descriptorItem) ?
					descriptorItem.getYoutubeDescriptor() : findYoutubeItem(getYoutubeItems(), videoId);
			if (descriptor == null) return;
			if (current != null) pinned.remove(current);
			pinned.add(mergeYoutubeItem(current, descriptor));
		}
		getPreferenceStore().applyStringPref(YT_PINNED_ITEMS, YoutubeItemCodec.encode(pinned));
	}

	private void reconcilePinnedItems() {
		YoutubeItemCodec.DecodeResult pinnedResult = decodePreference(YT_PINNED_ITEMS);
		if (pinnedResult.isUnsupported()) return;
		YoutubeItemCodec.DecodeResult historyResult = decodePreference(YT_ITEM_HISTORY);
		if (historyResult.isUnsupported()) return;

		Set<String> favoriteIds = getFavoriteYoutubeIds();
		List<YoutubeItem> pinned = new ArrayList<>();
		boolean changed = false;
		for (YoutubeItem item : pinnedResult.items()) {
			if (favoriteIds.contains(item.stableId())) pinned.add(item);
			else changed = true;
		}
		for (String id : favoriteIds) {
			String videoId = id.substring("youtube:video:".length());
			if (findYoutubeItem(pinned, videoId) != null) continue;
			YoutubeItem descriptor = findYoutubeItem(historyResult.items(), videoId);
			if (descriptor != null) {
				pinned.add(descriptor);
				changed = true;
			}
		}
		if (changed) getPreferenceStore().applyStringPref(YT_PINNED_ITEMS,
				YoutubeItemCodec.encode(pinned));
	}

	private Set<String> getFavoriteYoutubeIds() {
		SharedPreferenceStore store = SharedPreferenceStore.create(
			FermataApplication.get().getSharedPreferences("favorites", Context.MODE_PRIVATE));
		String[] ids = store.getStringArrayPref(FavoritesPrefs.FAVORITES);
		Set<String> result = new HashSet<>();
		if (ids != null) {
			for (String id : ids) {
				if ((id != null) && id.startsWith("youtube:video:")) result.add(id);
			}
		}
		return result;
	}

	private boolean isFavoriteId(String id) {
		return getFavoriteYoutubeIds().contains(id);
	}

	private <T> YoutubeItemCodec.DecodeResult decodePreference(Pref<Supplier<String>> pref) {
		return YoutubeItemCodec.decodeResult(getPreferenceStore().getStringPref(pref));
	}

	@Nullable
	private static YoutubeItem findYoutubeItem(List<YoutubeItem> items, String videoId) {
		for (YoutubeItem item : items) if (item.videoId().equals(videoId)) return item;
		return null;
	}

	static YoutubeItem mergeYoutubeItem(@Nullable YoutubeItem existing, YoutubeItem update) {
		if (existing == null) return update;
		if (!existing.videoId().equals(update.videoId()))
			throw new IllegalArgumentException("YouTube item identity mismatch");
		String title = update.title().isEmpty() ? existing.title() : update.title();
		String thumbnail = update.thumbnailUrl().isEmpty() ? existing.thumbnailUrl() :
				update.thumbnailUrl();
		long duration = (update.durationMillis() > 0L) ? update.durationMillis() :
				existing.durationMillis();
		return new YoutubeItem(update.videoId(), update.pageUrl(), title, thumbnail, duration,
				Math.max(existing.lastPlayedAtMillis(), update.lastPlayedAtMillis()));
	}

	boolean autoHighestQualityChanged(List<Pref<?>> prefs) {
		return prefs.contains(YT_AUTO_HIGHEST_QUALITY);
	}

	boolean getSponsorBlockEnabled() {
		return YoutubeSponsorBlock.isEnabled(getPreferenceStore());
	}

	SponsorBlockController getSponsorBlockController() {
		return sponsorBlockController;
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
			String id) {
		if (!"youtube".equals(scheme) || (id == null) || !id.startsWith("youtube:video:"))
			return null;
		String videoId = id.substring("youtube:video:".length());
		YoutubeItemCodec.DecodeResult history = decodePreference(YT_ITEM_HISTORY);
		YoutubeItemCodec.DecodeResult pinned = decodePreference(YT_PINNED_ITEMS);
		YoutubeItem item = findYoutubeItem(history.items(), videoId);
		if (item == null) item = findYoutubeItem(pinned.items(), videoId);
		if (item != null) return Completed.completed(new YoutubeHistoryItem(this, lib, item));
		if (history.isUnsupported() || pinned.isUnsupported())
			return Completed.failed(new IllegalStateException("Unsupported YouTube item format"));
		return Completed.completedNull();
	}

	@Nullable
	private MediaEngine createHistoryEngine(@Nullable MediaEngine current,
																						 MediaEngine.Listener listener,
																						 YoutubeItem descriptor) {
		if ((current instanceof YoutubeMediaEngine engine) && engine.belongsTo(this)) return current;
		if ((current instanceof YoutubeDeferredMediaEngine pending) && pending.belongsTo(this))
			return current;
		MainActivityDelegate activity = currentActivity();
		if (activity != null) {
			ActivityFragment fragment = activity.getActiveFragment();
			if (fragment instanceof YoutubeFragment youtube) {
				YoutubeWebView web = youtube.getWebView();
				if ((web != null) && (web.getMediaEngine() != null) &&
						web.getMediaEngine().belongsTo(this)) return web.getMediaEngine();
			}
		}
		if (listener instanceof MediaSessionCallback callback)
			return new YoutubeDeferredMediaEngine(this, descriptor, callback);
		return null;
	}

	void attachHistoryEngine(YoutubeDeferredMediaEngine pending, long generation) {
		if (!pending.belongsTo(this) || !pending.isAttachmentCurrent(generation)) return;
		attachHistoryEngine(pending, generation, 0);
		FermataApplication.get().getHandler().postDelayed(() -> {
			if (pending.isAttachmentCurrent(generation)) {
				Log.w("Timed out waiting for YouTube Activity/WebView attachment");
				pending.failAttachment(generation);
			}
		}, HISTORY_ATTACH_TIMEOUT_MS);
	}

	private void attachHistoryEngine(YoutubeDeferredMediaEngine pending,
			long generation, int attempt) {
		if (!pending.isAttachmentCurrent(generation)) return;
		if (attempt >= HISTORY_ATTACH_MAX_ATTEMPTS) {
			Log.w("Timed out waiting for YouTube Activity/WebView attachment");
			pending.failAttachment(generation);
			return;
		}
		MainActivityDelegate activity = currentActivity();
		if (activity == null) {
			FermataApplication.get().getHandler().postDelayed(
					() -> attachHistoryEngine(pending, generation, attempt + 1),
					HISTORY_ATTACH_RETRY_MS);
			return;
		}
		activity.post(() -> attachHistoryEngine(activity, pending, generation, attempt));
	}

	@Nullable
	private MainActivityDelegate currentActivity() {
		MainActivity mobile = MainActivity.getActiveInstance();
		if (mobile != null) {
			MainActivityDelegate activity = mobile.getActivityDelegate().peek();
			if (activity != null) return activity;
		}

		try {
			var resolver = ActivityDelegate.getContextToDelegate();
			if (resolver == null) return null;
			ActivityDelegate activity = resolver.apply(FermataApplication.get());
			return (activity instanceof MainActivityDelegate main) ? main : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private void attachHistoryEngine(MainActivityDelegate activity,
			YoutubeDeferredMediaEngine pending, long generation, int attempt) {
		if (!pending.isAttachmentCurrent(generation)) return;
		if (attempt >= HISTORY_ATTACH_MAX_ATTEMPTS) {
			Log.w("Timed out waiting for YouTube Activity/WebView attachment");
			pending.failAttachment(generation);
			return;
		}
		if (activity.getAppActivity().isFinishing() || activity.getAppActivity().isDestroyed()) {
			pending.failAttachment(generation);
			return;
		}
		ActivityFragment fragment = activity.showFragment(getAddonId());
		if (!(fragment instanceof YoutubeFragment youtube)) {
			activity.postDelayed(() -> attachHistoryEngine(activity, pending, generation, attempt + 1),
					HISTORY_ATTACH_RETRY_MS);
			return;
		}
		YoutubeWebView web = youtube.getWebView();
		YoutubeMediaEngine engine = (web == null) ? null : web.getMediaEngine();
		if (engine == null) {
			activity.postDelayed(() -> attachHistoryEngine(activity, pending, generation, attempt + 1),
					HISTORY_ATTACH_RETRY_MS);
			return;
		}
		if (!pending.attach(engine, generation)) {
			activity.postDelayed(() -> attachHistoryEngine(activity, pending, generation, attempt + 1),
					HISTORY_ATTACH_RETRY_MS);
		}
	}

	static final class YoutubeHistoryItem extends YoutubeMediaEngine.YoutubePlayableItem
			implements YoutubeDescriptorItem {
		private final YoutubeAddon addon;
		private YoutubeItem descriptor;

		YoutubeHistoryItem(YoutubeAddon addon, DefaultMediaLib lib, YoutubeItem descriptor) {
			super(descriptor.stableId(), new ExtRoot("youtube", lib, AddonCapability.YOUTUBE),
					GenericFileSystem.getInstance().create(descriptor.pageUrl()));
			this.addon = addon;
			this.descriptor = descriptor;
		}

		@Override
		public YoutubeItem getYoutubeDescriptor() {
			return descriptor;
		}

		void updateDescriptor(YoutubeItem update) {
			if ((update == null) || !descriptor.videoId().equals(update.videoId())) return;
			YoutubeItem merged = mergeYoutubeItem(descriptor, update);
			if (merged.equals(descriptor)) return;
			descriptor = merged;
			reset();
			updateTitles();
		}

		@Override
		public String getName() {
			String title = descriptor.title();
			return title.isEmpty() ? getLib().getContext().getString(
					me.aap.fermata.R.string.addon_name_youtube) : title;
		}

		@Nullable
		@Override
		public MediaEngine getMediaEngine(@Nullable MediaEngine current, MediaEngine.Listener listener) {
			return addon.createHistoryEngine(current, listener, descriptor);
		}

		@Override
		protected FutureSupplier<MediaMetadataCompat> loadMeta() {
			MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
					.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getName())
					.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, descriptor.durationMillis());
			if (!descriptor.thumbnailUrl().isEmpty()) {
				builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
						descriptor.thumbnailUrl());
			}
			return Completed.completed(builder.build());
		}
	}

	enum VideoScale {
		FILL, CONTAIN, COVER, NONE;

		String prefName() {
			return name().toLowerCase();
		}
	}

	enum SessionReturnAction {
		KEEP,
		RESET_HOME
	}
}
