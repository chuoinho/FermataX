package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.addon.tv.TvSourceItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class XtreamSourceItem extends ItemContainer<XtreamSectionItem> implements TvSourceItem {
	public static final String SCHEME = "tvx";
	private XtreamAccount account;
	private volatile XtreamApi api;
	private long catalogRevision;

	private XtreamSourceItem(TvRootItem root, XtreamAccount account) {
		super(toId(account.getSourceId()), root, null);
		this.account = account;
		api = new XtreamApi(account);
	}

	public static FutureSupplier<XtreamSourceItem> create(TvRootItem root, int sourceId) {
		XtreamAccount account = XtreamAccount.load(root, sourceId);
		return (account == null) ? completedNull() : completed(create(root, account));
	}

	public static XtreamSourceItem create(TvRootItem root, XtreamAccount account) {
		DefaultMediaLib lib = root.getLib();
		String id = toId(account.getSourceId());

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);

			if (i != null) {
				XtreamSourceItem item = (XtreamSourceItem) i;
				if (BuildConfig.D && !root.equals(item.getParent())) throw new AssertionError();
				item.setAccount(account);
				return item;
			} else {
				return new XtreamSourceItem(root, account);
			}
		}
	}

	public static String toId(int sourceId) {
		return SharedTextBuilder.get().append(SCHEME).append(':').append(sourceId).releaseString();
	}

	public FutureSupplier<XtreamSectionItem> getSection(String type) {
		return completed(XtreamSectionItem.create(this, type));
	}

	public XtreamApi getApi() {
		XtreamApi api = this.api;
		if (api != null) return api;
		return this.api = new XtreamApi(account);
	}

	public XtreamAccount getAccount() {
		return account;
	}

	public void setAccount(XtreamAccount account) {
		if (sameAccount(this.account, account)) return;
		boolean catalogChanged = !sameCatalog(this.account, account);
		if (catalogChanged) {
			removePersistedCatalogItems((TvRootItem) getParent(), this.account.getSourceId());
		}
		this.account = account;
		if (catalogChanged) {
			catalogRevision++;
			ItemContainer.invalidateResolvedChildren();
		}
		clearApiCache();
		updateTitles();
	}

	public static void prepareAccountUpdate(TvRootItem root, XtreamAccount previous,
			XtreamAccount replacement) {
		if (!sameCatalog(previous, replacement)) {
			removePersistedCatalogItems(root, replacement.getSourceId());
		}
	}

	private static void removePersistedCatalogItems(TvRootItem root, int sourceId) {
		root.getLib().removePersistedItems(id -> XtreamItemId.belongsToCatalog(id, sourceId));
	}

	long getCatalogRevision() {
		return catalogRevision;
	}

	XtreamAccount requireAccountRevision(long revision) {
		return requireAccountRevision(account, catalogRevision, revision);
	}

	static XtreamAccount requireAccountRevision(XtreamAccount account, long currentRevision,
			long requestedRevision) {
		if (requestedRevision != currentRevision) {
			throw new IllegalStateException("Xtream account catalog has changed");
		}
		return account;
	}

	public void warmUp() {
		getApi().warmUp();
	}

	public void clearCache() {
		clearApiCache();
	}

	@Override
	protected String getScheme() {
		return XtreamSectionItem.SCHEME;
	}

	@Override
	protected void saveChildren(List<XtreamSectionItem> children) {
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		warmUp();
		List<Item> children = new ArrayList<>(3);
		children.add(XtreamSectionItem.create(this, XtreamSectionItem.TYPE_LIVE));
		children.add(XtreamSectionItem.create(this, XtreamSectionItem.TYPE_VOD));
		children.add(XtreamSectionItem.create(this, XtreamSectionItem.TYPE_SERIES));
		return completed(children);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed(getLib().getContext().getString(R.string.xtream_source_subtitle));
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.xtream_source_subtitle);
	}

	@Override
	public FutureSupplier<Void> refresh() {
		clearApiCache();
		warmUp();
		return super.refresh();
	}

	private void clearApiCache() {
		XtreamApi api = this.api;
		if (api != null) api.clearCache();
		this.api = new XtreamApi(account);
	}

	private static boolean sameAccount(XtreamAccount a, XtreamAccount b) {
		if (a == b) return true;
		if ((a == null) || (b == null)) return false;
		return (a.getSourceId() == b.getSourceId())
				&& Objects.equals(a.getRawName(), b.getRawName())
				&& (a.getSchemeIndex() == b.getSchemeIndex())
				&& Objects.equals(a.getHost(), b.getHost())
				&& (a.getPort() == b.getPort())
				&& Objects.equals(a.getUsername(), b.getUsername())
				&& Objects.equals(a.getPassword(), b.getPassword())
				&& (a.getOutputIndex() == b.getOutputIndex())
				&& Objects.equals(a.getUserAgent(), b.getUserAgent())
				&& (a.getResponseTimeout() == b.getResponseTimeout());
	}

	static boolean sameCatalog(XtreamAccount a, XtreamAccount b) {
		if (a == b) return true;
		if ((a == null) || (b == null)) return false;
		return (a.getSourceId() == b.getSourceId())
				&& (a.getSchemeIndex() == b.getSchemeIndex())
				&& Objects.equals(a.getHost(), b.getHost())
				&& (a.getPort() == b.getPort())
				&& Objects.equals(a.getUsername(), b.getUsername());
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
		return TYPE_XTREAM;
	}
}
