package me.aap.fermata.addon.tv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uEpgItem;
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
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
public class TvRootItem extends ItemContainer<TvSourceItem> implements TvItem {
	public static final String ID = "TV";
	private final DefaultMediaLib lib;
	private final TvSourceRepository sources;
	private final M3uSourceHandler m3uSources;
	private final XtreamSourceHandler xtreamSources;
	private final TvItemFactory itemFactory;

	public TvRootItem(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		sources = new TvSourceRepository(this);
		m3uSources = new M3uSourceHandler(this, sources);
		xtreamSources = new XtreamSourceHandler(this, sources);
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
		List<Item> children = new ArrayList<>(ids.length);
		return forEach(id -> {
			FutureSupplier<? extends TvSourceItem> f = createSource(id);
			if (f != null) return f.onSuccess(i -> {
				if (i != null) children.add(i);
			});
			return completedVoid();
		}, idList).map(v -> children);
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
				|| id.startsWith(XtreamSourceItem.SCHEME);
	}

	public void addSource(TvM3uFile m3u) {
		m3uSources.addSource(m3u);
	}

	@Override
	protected void itemRemoved(TvSourceItem i) {
		super.itemRemoved(i);
		if (i instanceof TvM3uItem) {
			m3uSources.sourceRemoved((TvM3uItem) i);
		} else if (i instanceof XtreamSourceItem) {
			xtreamSources.sourceRemoved((XtreamSourceItem) i);
		}
	}

	public void addSource(XtreamAccount account) {
		xtreamSources.addSource(account);
	}

	public void updateSource(XtreamAccount account) {
		xtreamSources.updateSource(account);
	}

	FutureSupplier<? extends TvSourceItem> createSource(int srcId) {
		if (!sources.hasSource(srcId)) return null;
		if (TvSourceItem.TYPE_XTREAM.equals(sources.getSourceType(srcId))) {
			return xtreamSources.create(srcId);
		}
		return m3uSources.create(srcId);
	}

	static String getSourceType(PreferenceStore ps, int sourceId) {
		return TvSourceRepository.getSourceType(ps, sourceId);
	}

	static Pref<Supplier<String>> sourceTypePref(int sourceId) {
		return TvSourceRepository.sourceTypePref(sourceId);
	}
}
