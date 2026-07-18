package me.aap.fermata.addon.audiobook;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;
import android.support.v4.media.session.PlaybackStateCompat;

@Keep
@SuppressWarnings("unused")
public final class AudiobookAddon implements MediaLibAddon, FermataMediaServiceAddon, VoiceSearchAddon,
		MediaSessionCallback.Listener {
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
		if (snapshot == null) return;
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
}
