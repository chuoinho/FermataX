package me.aap.fermata.addon.stremio.item;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.playback.StremioStreamEligibilityPolicy;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Completed;

public final class StremioStreamPickerItem extends StremioBrowsableItem {
	private final String sourceUuid;
	private final StreamAggregationRequest request;
	private final CopyOnWriteArrayList<Item.ChangeListener> listeners =
			new CopyOnWriteArrayList<>();
	private long loadGeneration;
	private StreamAggregationResult pendingLateResult;
	private boolean initialChildrenReady;
	private boolean lateRefreshPublished;

	public StremioStreamPickerItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, BrowseMedia media, @Nullable BrowseEpisode episode) {
		this(parent, root, gateway, media.sourceUuid(),
				StremioStreamRequestFactory.create(media, episode));
	}

	private StremioStreamPickerItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, String sourceUuid, StreamAggregationRequest request) {
		super(StremioItemIds.streamPicker(sourceUuid, request.type(),
				request.contentId(), request.videoId()), parent, root, gateway,
				request.metadata().title(), "", "", request.metadata().artwork());
		this.sourceUuid = sourceUuid;
		this.request = request;
	}

	public StreamAggregationRequest request() {
		return request;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		StreamAggregationResult cached;
		long generation;
		synchronized (this) {
			cached = pendingLateResult;
			if (cached != null) {
				pendingLateResult = null;
				return children(cached);
			}
			generation = ++loadGeneration;
			initialChildrenReady = false;
			lateRefreshPublished = false;
		}
		FutureSupplier<List<Item>> children = gateway().streams(sourceUuid, request,
				(result, error) -> acceptLateResult(generation, result, error)).then(this::children);
		children.onCompletion((items, failure) -> initialLoadCompleted(generation, failure));
		return children;
	}

	private FutureSupplier<List<Item>> children(StreamAggregationResult result) {
		return append(result, 0, new ArrayList<>());
	}

	private void acceptLateResult(long generation, StreamAggregationResult result, Throwable error) {
		if (error != null || result == null) result = new StreamAggregationResult(List.of());
		boolean publish;
		synchronized (this) {
			if ((generation != loadGeneration) || lateRefreshPublished) return;
			pendingLateResult = result;
			publish = initialChildrenReady;
			if (publish) lateRefreshPublished = true;
		}
		if (publish) publishLateRefresh(generation);
	}

	private void initialLoadCompleted(long generation, Throwable failure) {
		boolean publish;
		synchronized (this) {
			if ((generation != loadGeneration) || (failure != null)) return;
			initialChildrenReady = true;
			publish = (pendingLateResult != null) && !lateRefreshPublished;
			if (publish) lateRefreshPublished = true;
		}
		if (publish) publishLateRefresh(generation);
	}

	private void publishLateRefresh(long generation) {
		Runnable publish = () -> {
			synchronized (StremioStreamPickerItem.this) {
				if ((generation != loadGeneration) || (pendingLateResult == null)) return;
			}
			invalidateChildrenCache();
			for (Item.ChangeListener listener : listeners) listener.mediaItemChanged(this);
		};
		App app = App.get();
		if (app == null) publish.run();
		else app.run(publish);
	}

	@Override
	public boolean addChangeListener(Item.ChangeListener listener) {
		listeners.addIfAbsent(listener);
		return true;
	}

	@Override
	public boolean removeChangeListener(Item.ChangeListener listener) {
		return listeners.remove(listener);
	}

	private FutureSupplier<List<Item>> append(StreamAggregationResult result,
			int index, List<Item> items) {
		List<PlaybackDescriptor> descriptors = result.descriptors();
		if (index >= descriptors.size()) {
			if (items.isEmpty()) {
				items.add(result.hasPendingProviders() ?
						new StremioLoadingStreamsItem(this, getRoot(), gateway(), request) :
						new StremioNoStreamsItem(this, getRoot(), gateway(), request));
			}
			return Completed.completed(List.copyOf(items));
		}
		PlaybackDescriptor descriptor = descriptors.get(index);
		if (!descriptor.identity().equals(request.identity())) {
			return append(result, index + 1, items);
		}
		if (isPlayable(descriptor)) {
			items.add(new StremioDirectPlayableItem(
					this, gateway(), descriptor, request, 0));
			return append(result, index + 1, items);
		}
		items.add(new StremioUnavailableStreamItem(this, getRoot(), gateway(), descriptor));
		return append(result, index + 1, items);
	}

	private static boolean isPlayable(PlaybackDescriptor descriptor) {
		return StremioStreamEligibilityPolicy.classify(descriptor) !=
				StremioStreamEligibilityPolicy.Kind.UNSUPPORTED;
	}
}
