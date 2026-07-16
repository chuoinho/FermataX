package me.aap.fermata.addon.podcast;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.addon.FermataMediaServiceAddon;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.addon.podcast.provider.PodcastSearchCoordinator;
import me.aap.fermata.addon.podcast.data.PodcastRepository;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.lib.PlayableItemResolver;
import android.support.v4.media.session.PlaybackStateCompat;
import me.aap.fermata.addon.podcast.refresh.PodcastRefreshCoordinator;
import me.aap.fermata.addon.podcast.download.PodcastDownloadCoordinator;

@Keep
@SuppressWarnings("unused")
public final class PodcastAddon implements MediaLibAddon, FermataMediaServiceAddon, VoiceSearchAddon,
		MediaSessionCallback.Listener {
	private static final long PROGRESS_WRITE_INTERVAL_MS = 15_000;
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(PodcastAddon.class.getName());
	private PodcastRootItem root;
	private PodcastSearchCoordinator search;
	private PodcastRepository repository;
	private PodcastRefreshCoordinator refresh;
	private PodcastDownloadCoordinator downloads;
	private MediaSessionCallback service;
	private long lastProgressWriteMs;
	private String lastProgressItemId;

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.podcast_fragment;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "podcast";
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new PodcastFragment();
	}

	@Override
	public boolean isSupportedItem(Item item) {
		return item instanceof PodcastItem;
	}

	@Override
	public synchronized PodcastRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) {
			if (search == null) search = new PodcastSearchCoordinator();
			if ((repository == null) && (lib != null)) {
				repository = new PodcastRepository(lib.getContext());
				refresh = new PodcastRefreshCoordinator(repository);
				downloads = new PodcastDownloadCoordinator(lib.getContext(), repository);
			}
			root = new PodcastRootItem(lib, search, repository, refresh, downloads);
		}
		return root;
	}

	@Override
	public synchronized void stop() {
		MediaSessionCallback callback = service;
		if (callback != null) {
			writeProgress(callback.getPlaybackSnapshot(), true);
			callback.removeBroadcastListener(this);
		}
		service = null;
		root = null;
		if (search != null) search.close();
		search = null;
		if (refresh != null) refresh.close();
		refresh = null;
		if (downloads != null) downloads.close();
		downloads = null;
		if (repository != null) repository.close();
		repository = null;
		lastProgressItemId = null;
		lastProgressWriteMs = 0;
	}

	@Override
	public synchronized void onServiceCreate(MediaSessionCallback callback) {
		if (service == callback) return;
		if (service != null) service.removeBroadcastListener(this);
		service = callback;
		callback.addBroadcastListener(this);
	}

	@Override
	public synchronized void onServiceDestroy(MediaSessionCallback callback) {
		if (service != callback) return;
		writeProgress(callback.getPlaybackSnapshot(), true);
		callback.removeBroadcastListener(this);
		service = null;
	}

	@Override
	public void onPlaybackSnapshotChanged(MediaSessionCallback callback,
			@Nullable PlaybackSnapshot previous, @NonNull PlaybackSnapshot current) {
		if ((previous != null) && !current.hasSameItem(previous)) writeProgress(previous, true);
		int state = current.getState().getState();
		boolean force = (state != PlaybackStateCompat.STATE_PLAYING) &&
				(state != PlaybackStateCompat.STATE_BUFFERING);
		writeProgress(current, force);
	}

	private synchronized void writeProgress(PlaybackSnapshot snapshot, boolean force) {
		if (snapshot == null) return;
		me.aap.fermata.media.lib.MediaLib.PlayableItem item = snapshot.getItem();
		if (item == null) return;
		item = PlayableItemResolver.unwrap(item);
		if (!(item instanceof PodcastEpisodeItem podcast)) return;
		PodcastRepository target = repository;
		if (target == null) return;

		long now = System.currentTimeMillis();
		String id = podcast.getId();
		if (!force && id.equals(lastProgressItemId) &&
				((now - lastProgressWriteMs) < PROGRESS_WRITE_INTERVAL_MS)) return;
		lastProgressItemId = id;
		lastProgressWriteMs = now;

		long position = Math.max(snapshot.getState().getPosition(), 0);
		me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord episode = podcast.getEpisode();
		long duration = episode.getDurationMs();
		boolean played = (duration > 0) &&
				(position >= Math.max(duration - 30_000, (long) (duration * 0.95)));
		target.updateProgress(episode.getFeedKey(), episode.getEpisodeKey(), position, played, now);
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
			String id) {
		return getRootItem(lib).getItem(scheme, id);
	}
}
