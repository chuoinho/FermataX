package me.aap.fermata.addon.podcast;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.FermataApplication;
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
import me.aap.fermata.addon.AutomotiveShutdownParticipant;
import me.aap.fermata.backup.BackupContributor;
import me.aap.fermata.backup.BackupIO;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;

@Keep
@SuppressWarnings("unused")
public final class PodcastAddon implements MediaLibAddon, FermataMediaServiceAddon, VoiceSearchAddon,
		MediaSessionCallback.Listener, AutomotiveShutdownParticipant, BackupContributor {
	private static final String BACKUP_ID = "podcast.sources";
	private static final int BACKUP_VERSION = 1;
	private static final int MAX_BACKUP_SOURCES = 10_000;
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
	public void onAutomotiveShutdown() {
		stop();
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
		if ((previous != null) && !current.hasSameItem(previous) &&
				previous.canPersistProgress()) {
			writeProgress(previous, true);
		}
		int state = current.getState().getState();
		if (!current.canPersistProgress()) return;
		boolean force = (state != PlaybackStateCompat.STATE_PLAYING) &&
				(state != PlaybackStateCompat.STATE_BUFFERING);
		writeProgress(current, force);
	}

	private synchronized void writeProgress(PlaybackSnapshot snapshot, boolean force) {
		if ((snapshot == null) || !snapshot.canPersistProgress()) return;
		me.aap.fermata.media.lib.MediaLib.PlayableItem item = snapshot.getItem();
		if (item == null) return;
		item = PlayableItemResolver.unwrap(item);
		if (!(item instanceof PodcastEpisodeItem podcast)) return;
		if (repository == null) return;

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
		persistEpisodeProgress(podcast, position, played);
	}

	static FutureSupplier<Void> persistEpisodeProgress(PodcastEpisodeItem episode,
			long position, boolean played) {
		return episode.savePlaybackProgress(position, played);
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
			String id) {
		return getRootItem(lib).getItem(scheme, id);
	}

	@Override
	public String getBackupId() {
		return BACKUP_ID;
	}

	@Override
	public int getBackupVersion() {
		return BACKUP_VERSION;
	}

	@Override
	public byte[] exportBackup() throws Exception {
		RepositoryLease lease = backupRepository();
		try {
			List<PodcastSubscription> subscriptions = lease.repository.listSubscriptions().get();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.writeInt(subscriptions.size());
				for (PodcastSubscription source : subscriptions) writeSubscription(output, source);
			}
			return bytes.toByteArray();
		} finally {
			lease.close();
		}
	}

	@Override
	public void validateRestore(int version, byte[] data) throws Exception {
		readSubscriptions(version, data);
	}

	@Override
	public void restoreBackup(int version, byte[] data) throws Exception {
		List<PodcastSubscription> subscriptions = readSubscriptions(version, data);
		RepositoryLease lease = backupRepository();
		try {
			lease.repository.replaceSubscriptions(subscriptions).get();
		} finally {
			lease.close();
		}
	}

	@Override
	public void verifyRestore(int version, byte[] data) throws Exception {
		List<PodcastSubscription> expected = readSubscriptions(version, data);
		RepositoryLease lease = backupRepository();
		try {
			List<PodcastSubscription> actual = lease.repository.listSubscriptions().get();
			if (expected.size() != actual.size()) throw incomplete();
			java.util.Map<String, PodcastSubscription> byId = new java.util.HashMap<>();
			for (PodcastSubscription source : actual) byId.put(source.getFeedKey(), source);
			for (PodcastSubscription source : expected) {
				if (!sameSubscription(source, byId.get(source.getFeedKey()))) throw incomplete();
			}
		} finally {
			lease.close();
		}
	}

	private synchronized RepositoryLease backupRepository() {
		if (repository != null) return new RepositoryLease(repository, false);
		return new RepositoryLease(new PodcastRepository(FermataApplication.get()), true);
	}

	static void writeSubscription(DataOutputStream output, PodcastSubscription source)
			throws Exception {
		BackupIO.writeString(output, source.getFeedKey());
		BackupIO.writeString(output, source.getCanonicalUrl());
		BackupIO.writeNullableString(output, source.getCredentialRef());
		BackupIO.writeString(output, source.getTitle());
		BackupIO.writeString(output, source.getAuthor());
		BackupIO.writeString(output, source.getDescription());
		BackupIO.writeString(output, source.getArtworkUrl());
		BackupIO.writeNullableString(output, source.getArtworkCredentialRef());
		BackupIO.writeString(output, source.getWebsiteUrl());
		BackupIO.writeString(output, source.getLanguage());
		output.writeBoolean(source.isExplicit());
		output.writeLong(source.getSubscribedMs());
	}

	static List<PodcastSubscription> readSubscriptions(int version, byte[] data)
			throws Exception {
		if (version != BACKUP_VERSION) throw new IllegalArgumentException(
				"Unsupported Podcast backup version");
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
			int count = BackupIO.readCount(input, MAX_BACKUP_SOURCES, "podcast sources");
			List<PodcastSubscription> result = new ArrayList<>(count);
			Set<String> ids = new HashSet<>();
			for (int i = 0; i < count; i++) {
				String feedKey = BackupIO.readString(input);
				if (feedKey.isBlank() || !ids.add(feedKey)) throw new IllegalArgumentException(
						"Invalid Podcast source ID");
				String canonicalUrl = BackupIO.readString(input);
				if (canonicalUrl.isBlank()) throw new IllegalArgumentException(
						"Missing Podcast source URL");
				result.add(new PodcastSubscription(feedKey, canonicalUrl,
						BackupIO.readNullableString(input), BackupIO.readString(input),
						BackupIO.readString(input), BackupIO.readString(input),
						BackupIO.readString(input), BackupIO.readNullableString(input),
						BackupIO.readString(input), BackupIO.readString(input), input.readBoolean(),
						null, null, 0, 0, input.readLong()));
			}
			if (input.read() != -1) throw new IllegalArgumentException("Trailing Podcast backup data");
			return List.copyOf(result);
		}
	}

	private static boolean sameSubscription(PodcastSubscription first,
			@Nullable PodcastSubscription second) {
		return (second != null) && first.getFeedKey().equals(second.getFeedKey()) &&
				first.getCanonicalUrl().equals(second.getCanonicalUrl()) &&
				java.util.Objects.equals(first.getCredentialRef(), second.getCredentialRef()) &&
				first.getTitle().equals(second.getTitle()) &&
				first.getAuthor().equals(second.getAuthor()) &&
				first.getDescription().equals(second.getDescription()) &&
				first.getArtworkUrl().equals(second.getArtworkUrl()) &&
				java.util.Objects.equals(first.getArtworkCredentialRef(),
						second.getArtworkCredentialRef()) &&
				first.getWebsiteUrl().equals(second.getWebsiteUrl()) &&
				first.getLanguage().equals(second.getLanguage()) &&
				(first.isExplicit() == second.isExplicit()) &&
				(first.getSubscribedMs() == second.getSubscribedMs());
	}

	private static IllegalStateException incomplete() {
		return new IllegalStateException("Podcast sources did not restore completely");
	}

	private record RepositoryLease(PodcastRepository repository, boolean owned)
			implements AutoCloseable {
		@Override
		public void close() {
			if (owned) repository.close();
		}
	}
}
