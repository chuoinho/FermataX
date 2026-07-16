package me.aap.fermata.addon.tv;

import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.addon.tv.xtream.XtreamSourceItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;

final class XtreamSourceHandler {
	private final TvRootItem root;
	private final TvSourceRepository sources;

	XtreamSourceHandler(TvRootItem root, TvSourceRepository sources) {
		this.root = root;
		this.sources = sources;
	}

	void addSource(XtreamAccount account) {
		XtreamAccount.requireCredentialStorage();
		int counter = sources.nextSourceId();
		XtreamAccount source = account.withSourceId(counter);

		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			sources.saveXtreamSource(e, counter, source);
		}

		XtreamSourceItem item = XtreamSourceItem.create(root, source);
		item.warmUp();
		root.addItem(item);
	}

	void updateSource(XtreamAccount account) {
		XtreamAccount.requireCredentialStorage();
		int sourceId = account.getSourceId();

		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			sources.updateXtreamSource(e, sourceId, account);
		}
	}

	void sourceRemoved(XtreamSourceItem item) {
		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			sources.removeXtreamSourcePrefs(e, item.getSourceId());
		}
	}

	FutureSupplier<XtreamSourceItem> create(int sourceId) {
		return XtreamSourceItem.create(root, sourceId);
	}
}
