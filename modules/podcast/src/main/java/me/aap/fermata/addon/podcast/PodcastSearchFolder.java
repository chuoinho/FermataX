package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.addon.podcast.provider.PodcastSearchCoordinator;
import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class PodcastSearchFolder extends ExtBrowsable implements PodcastItem {
	private static final String ID_PREFIX = PodcastRootItem.SCHEME + ":search:";
	private final PodcastRootItem root;
	private final PodcastSearchRequest request;
	private final PodcastSearchCoordinator coordinator;
	private volatile FutureSupplier<List<Item>> active;

	PodcastSearchFolder(PodcastRootItem root, PodcastSearchRequest request,
			PodcastSearchCoordinator coordinator) {
		super(ID_PREFIX + PodcastIds.hash(request.cacheKey()), root, null);
		this.root = root;
		this.request = request;
		this.coordinator = coordinator;
	}

	PodcastSearchRequest getRequest() {
		return request;
	}

	void cancel() {
		FutureSupplier<List<Item>> task = active;
		if ((task != null) && !task.isDone()) task.cancel();
		active = null;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		FutureSupplier<List<Item>> task = coordinator.search(request).map(this::toItems);
		active = task;
		return task;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.podcast_search);
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.search;
	}

	@NonNull
	@Override
	public PodcastRootItem getRoot() {
		return root;
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		return root;
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
	protected FutureSupplier<String> buildSubtitle() {
		return completed(request.getQuery());
	}

	@Override
	public FutureSupplier<Void> refresh() {
		coordinator.invalidate(request);
		return super.refresh();
	}

	private List<Item> toItems(List<PodcastSearchResult> results) {
		List<Item> items = new ArrayList<>(results.size());
		for (PodcastSearchResult result : results) {
			items.add(new PodcastSearchResultItem(this, result));
		}
		return items;
	}
}
