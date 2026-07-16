package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

final class PodcastSubscriptionItem extends ExtBrowsable implements PodcastItem {
	private final PodcastRootItem root;
	private final PodcastSubscription subscription;

	PodcastSubscriptionItem(PodcastRootItem root, PodcastSubscription subscription) {
		super(PodcastRootItem.feedId(subscription.getFeedKey()), root, null);
		this.root = root;
		this.subscription = subscription;
	}

	PodcastSubscription getSubscription() {
		return subscription;
	}

	@NonNull
	@Override
	public String getName() {
		return subscription.getTitle();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.podcast;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return root.resolveArtwork(subscription).ifFail(error -> "").map(artwork ->
				artwork.isEmpty() ? null : Uri.parse(artwork));
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
		return completed(subscription.getAuthor());
	}

	@Override
	protected FutureSupplier<String> buildDescription() {
		return completed(subscription.getDescription());
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return root.openSubscription(this);
	}
}
