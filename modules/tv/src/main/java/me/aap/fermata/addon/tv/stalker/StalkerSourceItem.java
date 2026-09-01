package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.addon.tv.TvSourceItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

public final class StalkerSourceItem extends ItemContainer<StalkerCategoryItem>
		implements TvSourceItem, StalkerCatalogItem {
	public static final String SCHEME = "tvs";
	private StalkerAccount account;
	private volatile StalkerApi api;
	private long catalogRevision;

	private StalkerSourceItem(TvRootItem root, StalkerAccount account) {
		super(toId(account.getSourceId()), root, null);
		this.account = account;
		api = new StalkerApi(account, root.getLib().getContext());
	}

	public static FutureSupplier<StalkerSourceItem> create(TvRootItem root, int sourceId) {
		StalkerAccount account = StalkerAccount.load(root, sourceId);
		return (account == null) ? completedNull() : completed(create(root, account));
	}

	public static StalkerSourceItem create(TvRootItem root, StalkerAccount account) {
		DefaultMediaLib lib = root.getLib();
		String id = toId(account.getSourceId());
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerSourceItem item = (StalkerSourceItem) cached;
				if (BuildConfig.D && !root.equals(item.getParent())) throw new AssertionError();
				item.setAccount(account);
				return item;
			}
			return new StalkerSourceItem(root, account);
		}
	}

	public static String toId(int sourceId) {
		return SharedTextBuilder.get().append(SCHEME).append(':').append(sourceId).releaseString();
	}

	public StalkerAccount getAccount() {
		return account;
	}

	public void setAccount(StalkerAccount replacement) {
		if (sameAccount(account, replacement)) return;
		boolean catalogChanged = !sameCatalog(account, replacement);
		account = replacement;
		if (catalogChanged) {
			catalogRevision++;
			ItemContainer.invalidateResolvedChildren();
		}
		clearCache();
		updateTitles();
	}

	public StalkerApi getApi() {
		StalkerApi current = api;
		if (current != null) return current;
		return api = new StalkerApi(account, getLib().getContext());
	}

	public FutureSupplier<StalkerCategoryItem> getCategory(String categoryId, String name) {
		return completed(StalkerCategoryItem.create(this,
				new StalkerCategory(categoryId, name)));
	}

	public void warmUp() {
		getApi().warmUp();
	}

	public void clearCache() {
		StalkerApi current = api;
		if (current != null) current.clearCache();
		api = new StalkerApi(account, getLib().getContext());
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@Override
	protected String getScheme() {
		return StalkerCategoryItem.SCHEME;
	}

	@Override
	protected void saveChildren(List<StalkerCategoryItem> children) {
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		warmUp();
		return getApi().getCategories().map(categories -> {
			List<Item> children = new ArrayList<>(categories.size() + 1);
			children.add(StalkerCategoryItem.create(this, new StalkerCategory(
					StalkerCategoryItem.ALL, getLib().getContext().getString(
							R.string.stalker_all_channels))));
			for (StalkerCategory category : categories) {
				if (!StalkerCategoryItem.ALL.equals(category.id())) {
					children.add(StalkerCategoryItem.create(this, category));
				}
			}
			return children;
		});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed(getLib().getContext().getString(R.string.stalker_source_subtitle));
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.stalker_source_subtitle);
	}

	@Override
	public FutureSupplier<Void> refresh() {
		clearCache();
		warmUp();
		return super.refresh();
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@NonNull
	@Override
	public String getName() {
		return account.getName();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	public int getSourceId() {
		return account.getSourceId();
	}

	@Override
	public String getSourceType() {
		return TYPE_STALKER;
	}

	private static boolean sameAccount(StalkerAccount first, StalkerAccount second) {
		return sameCatalog(first, second) &&
				Objects.equals(first.getRawName(), second.getRawName()) &&
				Objects.equals(first.getRawUserAgent(), second.getRawUserAgent()) &&
				(first.getResponseTimeout() == second.getResponseTimeout());
	}

	private static boolean sameCatalog(StalkerAccount first, StalkerAccount second) {
		return (first == second) || ((first != null) && (second != null) &&
				(first.getSourceId() == second.getSourceId()) &&
				Objects.equals(first.getPortal(), second.getPortal()) &&
				Objects.equals(first.getMac(), second.getMac()) &&
				Objects.equals(first.getSerial(), second.getSerial()) &&
				Objects.equals(first.getDeviceId(), second.getDeviceId()));
	}
}
