package me.aap.fermata.addon.tv;

import me.aap.fermata.addon.tv.stalker.StalkerAccount;
import me.aap.fermata.addon.tv.stalker.StalkerSourceItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;

final class StalkerSourceHandler {
	private final TvRootItem root;
	private final TvSourceRepository sources;

	StalkerSourceHandler(TvRootItem root, TvSourceRepository sources) {
		this.root = root;
		this.sources = sources;
	}

	void addSource(StalkerAccount account) {
		StalkerAccount.requireCredentialStorage();
		int sourceId = sources.nextSourceId();
		StalkerAccount source = account.withSourceId(sourceId);
		try (PreferenceStore.Edit edit = root.editPreferenceStore()) {
			sources.saveStalkerSource(edit, sourceId, source);
		}
		StalkerSourceItem item = StalkerSourceItem.create(root, source);
		item.warmUp();
		root.addItem(item);
	}

	void updateSource(StalkerAccount account) {
		StalkerAccount.requireCredentialStorage();
		int sourceId = account.getSourceId();
		try (PreferenceStore.Edit edit = root.editPreferenceStore()) {
			sources.updateStalkerSource(edit, sourceId, account);
		}
		var cached = root.getLib().getCachedItem(StalkerSourceItem.toId(sourceId));
		if (cached instanceof StalkerSourceItem source) source.setAccount(account);
		root.invalidateSearch();
	}

	void sourceRemoved(StalkerSourceItem item) {
		try (PreferenceStore.Edit edit = root.editPreferenceStore()) {
			sources.removeStalkerSourcePrefs(edit, item.getSourceId());
		}
	}

	FutureSupplier<StalkerSourceItem> create(int sourceId) {
		return StalkerSourceItem.create(root, sourceId);
	}
}
