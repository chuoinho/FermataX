package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class PodcastSearchResultItem extends ExtBrowsable implements PodcastItem {
	private static final String ID_PREFIX = PodcastRootItem.SCHEME + ":search-result:";
	private final PodcastSearchFolder parent;
	private final PodcastSearchResult result;

	PodcastSearchResultItem(PodcastSearchFolder parent, PodcastSearchResult result) {
		super(ID_PREFIX + result.itemKey(), parent, null);
		this.parent = parent;
		this.result = result;
	}

	PodcastSearchResult getResult() {
		return result;
	}

	@NonNull
	@Override
	public String getName() {
		return result.getTitle();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.podcast;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		String artwork = result.getArtworkUrl();
		return (artwork == null) ? completedNull() : completed(Uri.parse(artwork));
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
		String subtitle = result.getAuthor();
		if (subtitle.isEmpty()) subtitle = result.getDescription();
		return completed(subtitle);
	}

	@Override
	protected FutureSupplier<String> buildDescription() {
		return completed(result.getDescription());
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed(List.of(new PodcastSubscribeItem(this, result)));
	}
}
