package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;

final class PodcastSubscribeItem extends ExtBrowsable implements PodcastItem {
	private static final String ID_PREFIX = PodcastRootItem.SCHEME + ":subscribe:";
	private final PodcastSearchResultItem parent;
	private final PodcastSearchResult result;

	PodcastSubscribeItem(PodcastSearchResultItem parent, PodcastSearchResult result) {
		super(ID_PREFIX + PodcastIds.hash(result.getFeedUrl()), parent, null);
		this.parent = parent;
		this.result = result;
	}

	PodcastSearchResult getResult() {
		return result;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.podcast_subscribe);
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.playlist_add;
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		return parent;
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
		return completed(result.getAuthor());
	}
}
