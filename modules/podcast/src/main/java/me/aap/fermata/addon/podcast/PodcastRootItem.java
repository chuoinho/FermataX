package me.aap.fermata.addon.podcast;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.provider.PodcastSearchCoordinator;
import me.aap.fermata.addon.podcast.data.PodcastRepository;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.data.PodcastImportResult;
import java.io.InputStream;
import java.io.OutputStream;
import me.aap.fermata.addon.podcast.model.PodcastSource;
import me.aap.fermata.addon.podcast.refresh.PodcastRefreshCoordinator;
import me.aap.fermata.addon.podcast.download.PodcastDownloadCoordinator;
import me.aap.fermata.addon.podcast.download.PodcastDownloadFiles;
import java.io.File;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class PodcastRootItem extends ExtRoot implements PodcastItem {
	public static final String ID = "Podcast";
	static final String SCHEME = "podcast";
	private static final String ACTION_PREFIX = SCHEME + ":action:";
	private static final String FEED_PREFIX = SCHEME + ":feed:";
	private static final String EPISODE_PREFIX = SCHEME + ":episode:";
	private static final String SECTION_PREFIX = SCHEME + ":section:";
	private final EnumMap<PodcastAction, PodcastActionItem> actions =
			new EnumMap<>(PodcastAction.class);
	private final EnumMap<PodcastSection, PodcastSectionItem> sections =
			new EnumMap<>(PodcastSection.class);
	private final PodcastSearchCoordinator search;
	private final PodcastRepository repository;
	private final PodcastRefreshCoordinator refresh;
	private final PodcastDownloadCoordinator downloads;

	PodcastRootItem(DefaultMediaLib lib) {
		this(lib, new PodcastSearchCoordinator(), null, null, null);
	}

	PodcastRootItem(DefaultMediaLib lib, PodcastSearchCoordinator search) {
		this(lib, search, null, null, null);
	}

	PodcastRootItem(DefaultMediaLib lib, PodcastSearchCoordinator search,
			@Nullable PodcastRepository repository, @Nullable PodcastRefreshCoordinator refresh,
			@Nullable PodcastDownloadCoordinator downloads) {
		super(ID, lib, AddonCapability.PODCAST);
		this.search = search;
		this.repository = repository;
		this.refresh = refresh;
		this.downloads = downloads;
		for (PodcastAction action : PodcastAction.values()) {
			actions.put(action, new PodcastActionItem(this, action));
		}
		for (PodcastSection section : PodcastSection.values()) {
			sections.put(section, new PodcastSectionItem(this, section));
		}
	}

	@Nullable
	FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		if (!SCHEME.equals(scheme)) return null;

		for (PodcastActionItem item : actions.values()) {
			if (item.getId().equals(id)) return completed(item);
		}
		for (PodcastSectionItem item : sections.values()) {
			if (item.getId().equals(id)) return completed(item);
		}
		if (id.startsWith(FEED_PREFIX) && (repository != null)) {
			String feedKey = id.substring(FEED_PREFIX.length());
			return repository.getSubscription(feedKey).map(subscription ->
					(subscription == null) ? null : new PodcastSubscriptionItem(this, subscription));
		}
		if (id.startsWith(EPISODE_PREFIX) && (repository != null)) {
			String[] keys = id.substring(EPISODE_PREFIX.length()).split(":", 2);
			if (keys.length != 2) return completedNull();
			return repository.getSubscription(keys[0]).then(subscription -> {
				if (subscription == null) return completedNull();
				return repository.getEpisode(keys[0], keys[1]).map(episode ->
						(episode == null) ? null : createEpisode(
								new PodcastSubscriptionItem(this, subscription), episode));
			});
		}
		return completedNull();
	}

	PodcastActionItem getActionItem(PodcastAction action) {
		return actions.get(action);
	}

	PodcastSearchFolder createSearchFolder(PodcastSearchRequest request) {
		return new PodcastSearchFolder(this, request, search);
	}

	FutureSupplier<PodcastSubscription> subscribe(PodcastSearchResult result) {
		return subscribe(result.getFeedUrl());
	}

	FutureSupplier<PodcastSubscription> subscribe(String url) {
		if (repository == null) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Podcast repository is unavailable"));
		}
		return repository.subscribe(url);
	}

	FutureSupplier<PodcastSubscription> subscribe(PodcastSource source) {
		if (repository == null) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Podcast repository is unavailable"));
		}
		return repository.subscribe(source);
	}

	FutureSupplier<PodcastImportResult> importOpml(InputStream input) {
		if (repository == null) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Podcast repository is unavailable"));
		}
		return repository.importOpml(input);
	}

	FutureSupplier<Void> exportOpml(OutputStream output, boolean includePrivate) {
		if (repository == null) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Podcast repository is unavailable"));
		}
		return repository.exportOpml(output, includePrivate);
	}

	FutureSupplier<PodcastSubscription> edit(PodcastSubscription previous, String url) {
		PodcastSource source = PodcastSource.create(url, null, null);
		if (source == null) return me.aap.utils.async.Completed.failed(
				new IllegalArgumentException("Invalid Podcast URL"));
		return edit(previous, source);
	}

	FutureSupplier<PodcastSubscription> edit(PodcastSubscription previous, PodcastSource source) {
		return subscribe(source).then(updated -> {
			if (updated.getFeedKey().equals(previous.getFeedKey())) return completed(updated);
			return repository.deleteSubscription(previous.getFeedKey()).map(ignored -> updated);
		});
	}

	FutureSupplier<Boolean> delete(PodcastSubscription subscription) {
		if (repository == null) return completed(false);
		FutureSupplier<Void> files = (downloads == null) ?
				me.aap.utils.async.Completed.completedVoid() :
				downloads.deleteSubscription(subscription.getFeedKey());
		return files.then(ignored -> repository.deleteSubscription(subscription.getFeedKey()));
	}

	static String feedId(String feedKey) {
		return FEED_PREFIX + feedKey;
	}

	static String episodeId(String feedKey, String episodeKey) {
		return EPISODE_PREFIX + feedKey + ':' + episodeKey;
	}

	static String sectionId(PodcastSection section) {
		return SECTION_PREFIX + section.id;
	}

	FutureSupplier<List<Item>> listEpisodes(PodcastSubscriptionItem parent) {
		if (repository == null) return completed(List.of());
		return repository.listEpisodes(parent.getSubscription().getFeedKey(), 100, 0).map(records -> {
			List<Item> result = new ArrayList<>(records.size());
			for (PodcastEpisodeRecord record : records) result.add(createEpisode(parent, record));
			return result;
		});
	}

	FutureSupplier<List<Item>> openSubscription(PodcastSubscriptionItem parent) {
		if (refresh == null) return listEpisodes(parent);
		return refresh.auto(parent.getSubscription()).then(
				ignored -> listEpisodes(parent), error -> listEpisodes(parent));
	}

	FutureSupplier<PodcastSubscription> refresh(PodcastSubscription subscription) {
		if (refresh == null) return completed(subscription);
		return refresh.manual(subscription.getFeedKey());
	}

	FutureSupplier<Void> setPlayed(PodcastEpisodeRecord episode, boolean played) {
		if (repository == null) return me.aap.utils.async.Completed.completedVoid();
		return repository.setPlayed(episode.getFeedKey(), episode.getEpisodeKey(), played);
	}

	FutureSupplier<File> download(PodcastEpisodeRecord episode) {
		if (downloads == null) return me.aap.utils.async.Completed.failed(
				new IllegalStateException("Podcast downloads are unavailable"));
		return downloads.download(episode);
	}

	FutureSupplier<Void> deleteDownload(PodcastEpisodeRecord episode) {
		if (downloads == null) return me.aap.utils.async.Completed.completedVoid();
		return downloads.delete(episode);
	}

	FutureSupplier<String> resolveArtwork(PodcastSubscription subscription) {
		if (repository == null) return completed(subscription.getArtworkUrl());
		return repository.resolveArtwork(subscription);
	}

	FutureSupplier<List<Item>> listSection(PodcastSectionItem parent) {
		if (repository == null) return completed(List.of());
		return switch (parent.getSection()) {
			case SUBSCRIPTIONS -> repository.listSubscriptions().map(subscriptions -> {
				List<Item> result = new ArrayList<>(subscriptions.size());
				for (PodcastSubscription subscription : subscriptions) {
					result.add(new PodcastSubscriptionItem(this, subscription));
				}
				return result;
			});
			case CONTINUE -> repository.listContinue(50).map(records ->
					createSectionEpisodes(parent, records));
			case NEW_EPISODES -> repository.listNewEpisodes(50).map(records ->
					createSectionEpisodes(parent, records));
		};
	}

	private List<Item> createSectionEpisodes(PodcastSectionItem parent,
			List<PodcastEpisodeRecord> records) throws Exception {
		List<Item> result = new ArrayList<>(records.size());
		for (PodcastEpisodeRecord record : records) result.add(createEpisode(parent, record));
		return result;
	}

	private PodcastEpisodeItem createEpisode(me.aap.fermata.media.lib.MediaLib.BrowsableItem parent,
			PodcastEpisodeRecord record) throws Exception {
		PodcastPlaybackSource playback = repository.resolveNetworkPlayback(record);
		return new PodcastEpisodeItem(parent, record, playback,
				() -> repository.resolveArtwork(record),
				repository::updateProgress,
				PodcastDownloadFiles.complete(getLib().getContext(), record));
	}

	static String actionId(PodcastAction action) {
		return ACTION_PREFIX + action.itemName;
	}

	boolean isChildItemId(String id) {
		return ID.equals(id) || id.startsWith(SCHEME + ':');
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(
				me.aap.fermata.R.string.addon_name_podcast));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
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
	protected FutureSupplier<List<Item>> listChildren() {
		if (repository == null) return completed(new ArrayList<>(actions.values()));
		return repository.listSubscriptions().map(subscriptions -> {
			List<Item> items = new ArrayList<>(sections.size() + actions.size());
			if (!subscriptions.isEmpty()) items.addAll(sections.values());
			items.addAll(actions.values());
			return items;
		});
	}
}
