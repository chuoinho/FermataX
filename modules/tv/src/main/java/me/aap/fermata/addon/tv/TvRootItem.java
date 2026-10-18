package me.aap.fermata.addon.tv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermata.addon.tv.m3u.TvM3uEpgItem;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermata.addon.tv.m3u.TvM3uGroupItem;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.m3u.TvM3uTrackItem;
import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.addon.tv.xtream.XtreamCatchupFolder;
import me.aap.fermata.addon.tv.xtream.XtreamCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamEpgItem;
import me.aap.fermata.addon.tv.xtream.XtreamEpisodeItem;
import me.aap.fermata.addon.tv.xtream.XtreamMovieItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeasonItem;
import me.aap.fermata.addon.tv.xtream.XtreamSectionItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeriesCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeriesItem;
import me.aap.fermata.addon.tv.xtream.XtreamSourceItem;
import me.aap.fermata.addon.tv.xtream.XtreamTrackItem;
import me.aap.fermata.addon.tv.xtream.XtreamVodCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamWatchFromBeginningItem;
import me.aap.fermata.addon.tv.stalker.StalkerAccount;
import me.aap.fermata.addon.tv.stalker.StalkerCategoryItem;
import me.aap.fermata.addon.tv.stalker.StalkerCatchupFolder;
import me.aap.fermata.addon.tv.stalker.StalkerContentCategoryItem;
import me.aap.fermata.addon.tv.stalker.StalkerEpgItem;
import me.aap.fermata.addon.tv.stalker.StalkerEpisodeItem;
import me.aap.fermata.addon.tv.stalker.StalkerSeasonItem;
import me.aap.fermata.addon.tv.stalker.StalkerSectionItem;
import me.aap.fermata.addon.tv.stalker.StalkerSeriesItem;
import me.aap.fermata.addon.tv.stalker.StalkerSourceItem;
import me.aap.fermata.addon.tv.stalker.StalkerTrackItem;
import me.aap.fermata.addon.tv.stalker.StalkerVodItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.SearchFolder;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Function;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

/**
 * @author Andrey Pavlenko
 */
public class TvRootItem extends ItemContainer<TvSourceItem> implements TvItem {
	public static final String ID = "TV";
	private final DefaultMediaLib lib;
	private final TvSourceRepository sources;
	private final M3uSourceHandler m3uSources;
	private final XtreamSourceHandler xtreamSources;
	private final StalkerSourceHandler stalkerSources;
	private final TvItemFactory itemFactory;
	private List<String> failedSourceNames = List.of();

	public TvRootItem(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		sources = new TvSourceRepository(this);
		m3uSources = new M3uSourceHandler(this, sources);
		xtreamSources = new XtreamSourceHandler(this, sources);
		stalkerSources = new StalkerSourceHandler(this, sources);
		itemFactory = new TvItemFactory(this);
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		return itemFactory.getItem(scheme, id);
	}


	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_tv));
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
	public MediaLib.BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public MediaLib.BrowsableItem getRoot() {
		return this;
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
	protected FutureSupplier<List<Item>> listChildren() {
		int[] ids = sources.getSourceIds();
		List<Integer> idList = new ArrayList<>(ids.length);
		for (int i : ids) idList.add(i);
		List<String> failures = new ArrayList<>();
		Map<Integer, UnavailableTvSourceItem> unavailable = new HashMap<>();
		return loadAvailableSources(idList, this::createSource, (id, failure) -> {
			String name = getSourceName(id);
			failures.add(name);
			if (failure != null) Log.e(failure, "TV source unavailable: ", name);
			else Log.e("TV source unavailable: ", name);
			unavailable.put(id, createUnavailableSource(sources.getStore(), this, id, name, failure));
		}).map(available -> {
			setFailedSourceNames(failures);
			List<Item> children = new ArrayList<>(idList.size());
			int availIdx = 0;
			for (int id : idList) {
				UnavailableTvSourceItem item = unavailable.get(id);
				if (item != null) {
					children.add(item);
				} else if (availIdx < available.size()) {
					children.add(available.get(availIdx++));
				}
			}
			return children;
		});
	}

	static <T> FutureSupplier<List<T>> loadAvailableSources(List<Integer> sourceIds,
			Function<Integer, FutureSupplier<? extends T>> loader,
			BiConsumer<Integer, Throwable> onFailure) {
		List<T> children = new ArrayList<>(sourceIds.size());
		return forEach(id -> {
			FutureSupplier<? extends T> source;
			try {
				source = loader.apply(id);
			} catch (Throwable failure) {
				onFailure.accept(id, failure);
				return completedVoid();
			}

			if (source == null) {
				onFailure.accept(id, null);
				return completedVoid();
			}

			return source.onSuccess(item -> {
				if (item != null) children.add(item);
				else onFailure.accept(id, null);
			}).ifFail(failure -> {
				onFailure.accept(id, failure);
				return null;
			});
		}, sourceIds).map(v -> children);
	}

	String getSourceName(int sourceId) {
		try {
			if (TvSourceItem.TYPE_XTREAM.equals(sources.getSourceType(sourceId))) {
				PreferenceStore store = sources.getStore();
				String name = store.getStringPref(XtreamAccount.namePref(sourceId));
				if (!isNullOrBlank(name)) return name;
				String host = store.getStringPref(XtreamAccount.hostPref(sourceId));
				if (!isNullOrBlank(host)) return host;
			} else if (TvSourceItem.TYPE_STALKER.equals(sources.getSourceType(sourceId))) {
				PreferenceStore store = sources.getStore();
				String name = store.getStringPref(StalkerAccount.namePref(sourceId));
				if (!isNullOrBlank(name)) return name;
				String portal = store.getStringPref(StalkerAccount.portalPref(sourceId));
				if (!isNullOrBlank(portal)) return portal;
			} else {
				String m3uId = sources.getM3uId(sourceId);
				if (!isNullOrBlank(m3uId)) {
					TvM3uFileSystem fs = TvM3uFileSystem.getInstance();
					String name = new TvM3uFile(fs.toRid(m3uId)).getName();
					if (!isNullOrBlank(name)) return name;
				}
			}
		} catch (Throwable failure) {
			Log.e(failure, "Failed to resolve TV source name for ", sourceId);
		}

		return getLib().getContext().getString(R.string.tv_source_name) + " #" + sourceId;
	}

	private synchronized void setFailedSourceNames(List<String> names) {
		failedSourceNames = names.isEmpty() ? List.of() : List.copyOf(names);
	}

	synchronized List<String> consumeFailedSourceNames() {
		if (failedSourceNames.isEmpty()) return List.of();
		List<String> result = failedSourceNames;
		failedSourceNames = List.of();
		return result;
	}

	@Override
	protected String getScheme() {
		return TvM3uItem.SCHEME;
	}

	@Override
	protected void saveChildren(List<TvSourceItem> children) {
		sources.setSourceIds(CollectionUtils.map(children,
				(i, t, a) -> a[i] = t.getSourceId(), int[]::new));
	}

	@Override
	public boolean isChildItemId(String id) {
		return id.startsWith(TvM3uTrackItem.SCHEME)
				|| id.startsWith(TvM3uGroupItem.SCHEME)
				|| id.startsWith(TvM3uEpgItem.SCHEME)
				|| id.startsWith(TvM3uItem.SCHEME)
				|| id.startsWith(XtreamTrackItem.SCHEME)
				|| id.startsWith(XtreamEpgItem.SCHEME)
				|| id.startsWith(XtreamCatchupFolder.SCHEME)
				|| id.startsWith(XtreamWatchFromBeginningItem.SCHEME)
				|| id.startsWith(XtreamMovieItem.SCHEME)
				|| id.startsWith(XtreamEpisodeItem.SCHEME)
				|| id.startsWith(XtreamSeasonItem.SCHEME)
				|| id.startsWith(XtreamSeriesItem.SCHEME)
				|| id.startsWith(XtreamSeriesCategoryItem.SCHEME)
				|| id.startsWith(XtreamVodCategoryItem.SCHEME)
				|| id.startsWith(XtreamCategoryItem.SCHEME)
				|| id.startsWith(XtreamSectionItem.SCHEME)
				|| id.startsWith(XtreamSourceItem.SCHEME)
				|| id.startsWith(StalkerTrackItem.SCHEME)
				|| id.startsWith(StalkerEpgItem.SCHEME)
				|| id.startsWith(StalkerCatchupFolder.SCHEME)
				|| id.startsWith(StalkerEpisodeItem.SCHEME)
				|| id.startsWith(StalkerSeasonItem.SCHEME)
				|| id.startsWith(StalkerSeriesItem.SCHEME)
				|| id.startsWith(StalkerVodItem.SCHEME)
				|| id.startsWith(StalkerContentCategoryItem.SCHEME)
				|| id.startsWith(StalkerSectionItem.SCHEME)
				|| id.startsWith(StalkerCategoryItem.SCHEME)
				|| id.startsWith(StalkerSourceItem.SCHEME)
				|| id.startsWith(UnavailableTvSourceItem.SCHEME);
	}

	public void addSource(TvM3uFile m3u) {
		m3uSources.addSource(m3u);
	}

	public String getM3uId(int sourceId) {
		return sources.getM3uId(sourceId);
	}

	@Override
	protected void itemAdded(TvSourceItem item) {
		super.itemAdded(item);
		invalidateSearch();
	}

	@Override
	protected void itemRemoved(TvSourceItem i) {
		super.itemRemoved(i);
		if (i instanceof TvM3uItem) {
			m3uSources.sourceRemoved((TvM3uItem) i);
		} else if (i instanceof XtreamSourceItem) {
			xtreamSources.sourceRemoved((XtreamSourceItem) i);
		} else if (i instanceof StalkerSourceItem) {
			stalkerSources.sourceRemoved((StalkerSourceItem) i);
		} else if (i instanceof UnavailableTvSourceItem) {
			String m3uId = sources.getM3uId(i.getSourceId());
			if (m3uId != null) {
				try {
					TvM3uFileSystem fs = TvM3uFileSystem.getInstance();
					TvM3uFile f = new TvM3uFile(fs.toRid(m3uId));
					TvM3uFileSystemProvider.removeSource(f);
				} catch (Throwable err) {
					Log.e(err, "Failed to clean up M3U file for source ", i.getSourceId());
				}
			}
			sources.removeSource(i.getSourceId());
		}
		invalidateSearch();
	}

	public void addSource(XtreamAccount account) {
		xtreamSources.addSource(account);
	}

	public void updateSource(XtreamAccount account) {
		xtreamSources.updateSource(account);
	}

	public void addSource(StalkerAccount account) {
		stalkerSources.addSource(account);
	}

	public void updateSource(StalkerAccount account) {
		stalkerSources.updateSource(account);
	}

	void invalidateSearch() {
		SearchFolder.invalidate(this);
	}

	FutureSupplier<? extends TvSourceItem> createSource(int srcId) {
		if (!sources.hasSource(srcId)) return null;
		if (TvSourceItem.TYPE_XTREAM.equals(sources.getSourceType(srcId))) {
			return xtreamSources.create(srcId);
		}
		if (TvSourceItem.TYPE_STALKER.equals(sources.getSourceType(srcId))) {
			return stalkerSources.create(srcId);
		}
		return m3uSources.create(srcId);
	}

	public static UnavailableTvSourceItem createUnavailableSource(int id, String name, Throwable failure) {
		return createUnavailableSource((PreferenceStore) null, null, id, name, failure);
	}

	public static UnavailableTvSourceItem createUnavailableSource(PreferenceStore store, int id, String name, Throwable failure) {
		return createUnavailableSource(store, null, id, name, failure);
	}

	public static UnavailableTvSourceItem createUnavailableSource(PreferenceStore store, MediaLib.BrowsableItem parent,
			int id, String name, Throwable failure) {
		String type = (store != null) ? TvSourceRepository.getSourceType(store, id) : TvSourceItem.TYPE_M3U;
		return new UnavailableTvSourceItem(parent, id, type, name, failure);
	}

	static String getSourceType(PreferenceStore ps, int sourceId) {
		return TvSourceRepository.getSourceType(ps, sourceId);
	}

	static Pref<Supplier<String>> sourceTypePref(int sourceId) {
		return TvSourceRepository.sourceTypePref(sourceId);
	}
}
