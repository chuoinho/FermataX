package me.aap.fermata.addon.audiobook;

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
import me.aap.fermata.addon.FermataMediaServiceAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.addon.audiobook.data.AudiobookRepository;
import me.aap.fermata.addon.audiobook.download.AudiobookDownloadManager;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.remote.AudiobookshelfClient;
import me.aap.fermata.addon.audiobook.remote.OpdsCatalogClient;
import me.aap.fermata.addon.audiobook.security.AudiobookCredentialStore;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.addon.AutomotiveShutdownParticipant;
import me.aap.fermata.backup.BackupContributor;
import me.aap.fermata.backup.BackupIO;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;
import android.support.v4.media.session.PlaybackStateCompat;

@Keep
@SuppressWarnings("unused")
public final class AudiobookAddon implements MediaLibAddon, FermataMediaServiceAddon, VoiceSearchAddon,
		MediaSessionCallback.Listener, AutomotiveShutdownParticipant, BackupContributor {
	private static final String BACKUP_ID = "audiobook.sources";
	private static final int BACKUP_VERSION = 1;
	private static final int MAX_BACKUP_SOURCES = 10_000;
	private static final long PROGRESS_WRITE_INTERVAL_MS = 15_000;
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(
			AudiobookAddon.class.getName());
	private AudiobookRootItem root;
	private AudiobookRepository repository;
	private AudiobookDownloadManager downloads;
	private AudiobookshelfClient audiobookshelf;
	private OpdsCatalogClient opds;
	private MediaSessionCallback service;
	private long lastProgressWriteMs;
	private String lastProgressItemId;

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.audiobook_fragment;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "audiobook";
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new AudiobookFragment();
	}

	@Override
	public boolean isSupportedItem(Item item) {
		return item instanceof AudiobookItem;
	}

	@Override
	public synchronized AudiobookRootItem getRootItem(DefaultMediaLib lib) {
		if ((repository == null) && (lib != null)) {
			repository = new AudiobookRepository(lib.getContext(), lib.getVfsManager());
			AudiobookCredentialStore credentialStore = new AudiobookCredentialStore(lib.getContext());
			audiobookshelf = new AudiobookshelfClient(credentialStore);
			opds = new OpdsCatalogClient(credentialStore);
			downloads = new AudiobookDownloadManager(lib.getContext(), repository,
					this::downloadHeaders);
		}
		if ((root == null) || (root.getLib() != lib)) {
			root = new AudiobookRootItem(lib, repository, downloads, audiobookshelf, opds);
		}
		return root;
	}

	private FutureSupplier<java.util.Map<String, String>> downloadHeaders(AudiobookBook book) {
		String sourceId = book.getSourceId();
		if (sourceId == null) return me.aap.utils.async.Completed.completed(java.util.Map.of());
		return repository.getSource(sourceId).map(source -> {
			if (source == null) return java.util.Map.<String, String>of();
			return switch (source.getType()) {
				case AUDIOBOOKSHELF -> audiobookshelf.requestHeaders(source);
				case OPDS -> opds.requestHeaders(source);
				default -> java.util.Map.of();
			};
		});
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
			String id) {
		return getRootItem(lib).getItem(scheme, id);
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
		if (downloads != null) downloads.close();
		downloads = null;
		audiobookshelf = null;
		opds = null;
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
		if (!(item instanceof AudiobookChapterItem chapter)) return;
		if (repository == null) return;

		long now = System.currentTimeMillis();
		String id = chapter.getId();
		if (!force && id.equals(lastProgressItemId) &&
				((now - lastProgressWriteMs) < PROGRESS_WRITE_INTERVAL_MS)) return;
		lastProgressItemId = id;
		lastProgressWriteMs = now;

		long position = Math.max(snapshot.getState().getPosition(), 0);
		long duration = chapter.getChapter().getDurationMs();
		boolean completed = (duration > 0) &&
				(position >= Math.max(duration - 30_000, (long) (duration * 0.95)));
		chapter.savePlaybackProgress(completed ? 0 : position, completed);
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
			List<AudiobookSource> sources = lease.repository.listSources().get();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.writeInt(sources.size());
				for (AudiobookSource source : sources) writeSource(output, source);
			}
			return bytes.toByteArray();
		} finally {
			lease.close();
		}
	}

	@Override
	public void validateRestore(int version, byte[] data) throws Exception {
		readSources(version, data);
	}

	@Override
	public void restoreBackup(int version, byte[] data) throws Exception {
		List<AudiobookSource> sources = readSources(version, data);
		RepositoryLease lease = backupRepository();
		try {
			lease.repository.replaceSources(sources).get();
		} finally {
			lease.close();
		}
	}

	@Override
	public void verifyRestore(int version, byte[] data) throws Exception {
		List<AudiobookSource> expected = readSources(version, data);
		RepositoryLease lease = backupRepository();
		try {
			List<AudiobookSource> actual = lease.repository.listSources().get();
			if (expected.size() != actual.size()) throw incomplete();
			java.util.Map<String, AudiobookSource> byId = new java.util.HashMap<>();
			for (AudiobookSource source : actual) byId.put(source.getId(), source);
			for (AudiobookSource source : expected) {
				if (!sameSource(source, byId.get(source.getId()))) throw incomplete();
			}
		} finally {
			lease.close();
		}
	}

	private synchronized RepositoryLease backupRepository() {
		if (repository != null) return new RepositoryLease(repository, false);
		FermataApplication app = FermataApplication.get();
		return new RepositoryLease(new AudiobookRepository(app, app.getVfsManager()), true);
	}

	static void writeSource(DataOutputStream output, AudiobookSource source)
			throws Exception {
		BackupIO.writeString(output, source.getId());
		BackupIO.writeString(output, source.getType().name());
		BackupIO.writeString(output, source.getName());
		BackupIO.writeString(output, source.getEndpoint());
		BackupIO.writeNullableString(output, source.getCredentialRef());
		output.writeLong(source.getCreatedMs());
		output.writeLong(source.getUpdatedMs());
	}

	static List<AudiobookSource> readSources(int version, byte[] data) throws Exception {
		if (version != BACKUP_VERSION) throw new IllegalArgumentException(
				"Unsupported Audiobook backup version");
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
			int count = BackupIO.readCount(input, MAX_BACKUP_SOURCES, "audiobook sources");
			List<AudiobookSource> result = new ArrayList<>(count);
			Set<String> ids = new HashSet<>();
			for (int i = 0; i < count; i++) {
				String id = BackupIO.readString(input);
				if (id.isBlank() || !ids.add(id)) throw new IllegalArgumentException(
						"Invalid Audiobook source ID");
				AudiobookSourceType type = AudiobookSourceType.valueOf(BackupIO.readString(input));
				String name = BackupIO.readString(input);
				String endpoint = BackupIO.readString(input);
				if (endpoint.isBlank()) throw new IllegalArgumentException(
						"Missing Audiobook source endpoint");
				result.add(new AudiobookSource(id, type, name, endpoint,
						BackupIO.readNullableString(input),
						input.readLong(), input.readLong()));
			}
			if (input.read() != -1) throw new IllegalArgumentException(
					"Trailing Audiobook backup data");
			return List.copyOf(result);
		}
	}

	private static boolean sameSource(AudiobookSource first, @Nullable AudiobookSource second) {
		return (second != null) && first.getId().equals(second.getId()) &&
				(first.getType() == second.getType()) && first.getName().equals(second.getName()) &&
				first.getEndpoint().equals(second.getEndpoint()) &&
				java.util.Objects.equals(first.getCredentialRef(), second.getCredentialRef()) &&
				(first.getCreatedMs() == second.getCreatedMs()) &&
				(first.getUpdatedMs() == second.getUpdatedMs());
	}

	private static IllegalStateException incomplete() {
		return new IllegalStateException("Audiobook sources did not restore completely");
	}

	private record RepositoryLease(AudiobookRepository repository, boolean owned)
			implements AutoCloseable {
		@Override
		public void close() {
			if (owned) repository.close();
		}
	}
}
