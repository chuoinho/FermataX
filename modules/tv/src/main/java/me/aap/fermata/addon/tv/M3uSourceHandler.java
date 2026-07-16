package me.aap.fermata.addon.tv;

import java.net.MalformedURLException;

import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

final class M3uSourceHandler {
	private final TvRootItem root;
	private final TvSourceRepository sources;

	M3uSourceHandler(TvRootItem root, TvSourceRepository sources) {
		this.root = root;
		this.sources = sources;
	}

	void addSource(TvM3uFile m3u) {
		int counter = sources.nextSourceId();

		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			sources.saveM3uSource(e, counter, TvM3uFileSystem.getInstance().toId(m3u.getRid()));
		}

		root.addItem(TvM3uItem.create(root, m3u, counter));
	}

	void sourceRemoved(TvM3uItem item) {
		int sourceId = item.getSourceId();

		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			sources.removeM3uSourcePrefs(e, sourceId);
		}

		TvM3uFileSystemProvider.removeSource(item.getResource());
	}

	FutureSupplier<TvM3uItem> create(int sourceId) {
		String m3uId = sources.getM3uId(sourceId);
		if (m3uId == null) return null;
		return TvM3uItem.create(root, sourceId, m3uId).onFailure(err -> {
			Log.e(err, "Failed to load source: ", m3uId);
			if (err instanceof MalformedURLException) sources.removeSource(sourceId);
		}).ifNull(() -> {
			Log.e("Failed to load source: ", m3uId);
			sources.removeSource(sourceId);
			return null;
		});
	}
}
