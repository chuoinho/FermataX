package me.aap.fermata.addon.stremio.torrent;

import me.aap.utils.net.NetUtils;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;

import me.aap.fermata.addon.stremio.integration.StremioSourceLease;
import me.aap.fermata.addon.stremio.lifecycle.StremioDeadlineScheduler;
import me.aap.fermata.addon.stremio.net.AddressResolver;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.NetworkPolicy;
import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.engine.PlaybackFailureException.Reason;
import me.aap.fermata.media.net.RemotePlaybackProgress;
import me.aap.utils.log.Log;

/** Runtime-owned torrent resolver backed by FrostWire jlibtorrent (MIT). */
public final class StremioTorrentEngine implements AutoCloseable {
	/* Keep P2P preparation bounded for Android Auto. A black player is not a useful
	 * loading state, especially while the car is moving. */
	private static final int METADATA_TIMEOUT_SECONDS = 20;
	private static final int INITIAL_BOUNDARY_PIECES = 4;
	private static final String[] FALLBACK_TRACKERS = {
		"udp://tracker.opentrackr.org:1337/announce",
		"udp://open.stealth.si:80/announce",
		"udp://tracker.torrent.eu.org:451/announce",
		"udp://exodus.desync.com:6969/announce"
	};
	private static final String[] VIDEO_EXTENSIONS = {
			".mkv", ".mp4", ".m4v", ".webm", ".avi", ".mov", ".ts", ".m2ts", ".mpg", ".mpeg"
	};

	private final File cacheDirectory;
	private final Executor executor;
	private final AddressResolver addressResolver;
	private final TorrentHttpServer server = new TorrentHttpServer();
	private final TorrentSessionOwner sessionOwner = new TorrentSessionOwner();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final TorrentCacheMaintenance cacheMaintenance;
	private final TorrentPreparationCoordinator preparations;

	public StremioTorrentEngine(File cacheDirectory, Executor executor) {
		this(cacheDirectory, executor, host ->
				java.util.Arrays.asList(java.net.InetAddress.getAllByName(host)),
				StremioDeadlineScheduler.get());
	}

	public StremioTorrentEngine(File cacheDirectory, Executor executor,
			AddressResolver addressResolver) {
		this(cacheDirectory, executor, addressResolver, StremioDeadlineScheduler.get());
	}

	public StremioTorrentEngine(File cacheDirectory, Executor executor,
			AddressResolver addressResolver, ScheduledExecutorService scheduler) {
		this(cacheDirectory, executor, addressResolver, scheduler, TorrentCachePolicy.DEFAULT,
				System::currentTimeMillis);
	}

	StremioTorrentEngine(File cacheDirectory, Executor executor,
			AddressResolver addressResolver, TorrentCachePolicy cachePolicy, LongSupplier clock) {
		this(cacheDirectory, executor, addressResolver, StremioDeadlineScheduler.get(),
				cachePolicy, clock);
	}

	StremioTorrentEngine(File cacheDirectory, Executor executor,
			AddressResolver addressResolver, ScheduledExecutorService scheduler,
			TorrentCachePolicy cachePolicy, LongSupplier clock) {
		this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.addressResolver = Objects.requireNonNull(addressResolver, "addressResolver");
		Objects.requireNonNull(cachePolicy, "cachePolicy");
		Objects.requireNonNull(clock, "clock");
		cacheMaintenance = new TorrentCacheMaintenance(cacheDirectory, executor, cachePolicy,
				clock, closed::get);
		preparations = new TorrentPreparationCoordinator(executor, scheduler, closed::get,
				this::releasePrepared, this::observe, cacheMaintenance::schedule);
	}

	public CompletableFuture<PreparedTorrent> prepare(InfoHashStreamTarget target) {
		return prepare(target, StremioSourceLease.unbound("stremio-torrent",
				NetworkConsent.STRICT));
	}

	/** Starts DHT/native networking before the user selects one of the visible P2P choices. */
	public CompletableFuture<Void> warmUp() {
		if (closed.get()) return StremioFutures.failedFuture(
				new IllegalStateException("Stremio torrent runtime is closed"));
		return CompletableFuture.runAsync(() -> {
			cacheMaintenance.run();
			sessionOwner.session();
		}, executor);
	}

	public CompletableFuture<PreparedTorrent> prepare(InfoHashStreamTarget target,
			StremioSourceLease sourceLease) {
		return prepare(target, sourceLease, null);
	}

	public CompletableFuture<PreparedTorrent> prepare(InfoHashStreamTarget target,
			StremioSourceLease sourceLease, Consumer<RemotePlaybackProgress> progress) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(sourceLease, "sourceLease");
		String key = target.infoHash().toLowerCase(Locale.ROOT) + ':' + target.fileIndex();
		return preparations.prepare(key, progress,
				(cancellation, observedProgress) -> prepareBlocking(target, sourceLease,
						observedProgress, cancellation));
	}

	private PreparedTorrent prepareBlocking(InfoHashStreamTarget target,
			StremioSourceLease sourceLease, Consumer<RemotePlaybackProgress> progress,
			TorrentPreparationCoordinator.Cancellation cancellation) throws Exception {
		requireActive(sourceLease, cancellation);
		ensureDirectory(cacheDirectory);
		SessionManager manager = sessionOwner.session();
		List<String> trackers = allowedTrackers(target, sourceLease.consent());
		String magnet = magnet(target.infoHash(), trackers);
		Log.i("P2P phase=RESOLVING_METADATA trackers=" + trackers.size() +
				" timeout=" + METADATA_TIMEOUT_SECONDS + "s");
		byte[] metadata = manager.fetchMagnet(magnet, METADATA_TIMEOUT_SECONDS, cacheDirectory);
		requireActive(sourceLease, cancellation);
		if (metadata == null || metadata.length == 0) {
			logUnavailable(manager, trackers.size(), null, "metadata-timeout");
			throw new PlaybackFailureException(Reason.P2P_METADATA_UNAVAILABLE);
		}
		TorrentInfo info = new TorrentInfo(metadata);
		if (!info.isValid() || info.numFiles() == 0) {
			throw new PlaybackFailureException(Reason.P2P_METADATA_UNAVAILABLE);
		}
		// fetchMagnet removes its temporary torrent. Its returned info dictionary does not
		// retain magnet trackers, so attach the validated trackers to the real download.
		for (String tracker : trackers) info.addTracker(tracker);
		int fileIndex;
		try {
			fileIndex = selectFile(info, target.fileIndex());
		} catch (IllegalArgumentException unavailable) {
			throw new PlaybackFailureException(Reason.P2P_FILE_UNAVAILABLE, unavailable);
		}
		File storageDirectory = new File(cacheDirectory,
				digest(info.infoHashV1().toHex().toLowerCase(Locale.ROOT)));
		boolean existedBefore = storageDirectory.exists();
		TorrentHandle handle = null;
		try {
			cacheMaintenance.beginPreparation(storageDirectory);
			ensureDirectory(storageDirectory);
			File resumeFile = new File(storageDirectory, ".fastresume");
			File file = containedFile(storageDirectory, info.files().filePath(fileIndex));
			boolean requiresRecheck = file.isFile() && !resumeFile.isFile();
			Priority[] priorities = new Priority[info.numFiles()];
			for (int i = 0; i < priorities.length; i++) {
				priorities[i] = (i == fileIndex) ? Priority.NORMAL : Priority.IGNORE;
			}
			manager.download(info, storageDirectory, resumeFile, priorities, null,
					TorrentFlags.AUTO_MANAGED);
			handle = awaitHandle(manager, info, sessionOwner.alerts(), cancellation);
			requireActive(sourceLease, cancellation);
			sessionOwner.registerResume(handle, resumeFile);
			if (requiresRecheck) handle.forceRecheck();
			requireActive(sourceLease, cancellation);
			handle.queuePositionTop();
			handle.resume();
			handle.filePriority(fileIndex, Priority.NORMAL);
			handle.forceReannounce();
			handle.forceDHTAnnounce();
			long size = info.files().fileSize(fileIndex);
			if (size <= 0) throw new PlaybackFailureException(Reason.P2P_FILE_UNAVAILABLE);
			primeBoundaries(handle, info, fileIndex);
			new TorrentReadinessGate().await(handle, info, fileIndex, sessionOwner.alerts(),
					progress, cancellation);
			requireActive(sourceLease, cancellation);
			String streamKey = digest(target.infoHash() + ':' + fileIndex);
			URI location = URI.create(server.register(
					streamKey, file, size, handle, info, fileIndex, sessionOwner.alerts(), progress));
			try {
				new TorrentLoopbackProbe().verify(location, size);
			} catch (IOException unavailable) {
				server.cancel(streamKey);
				throw new PlaybackFailureException(Reason.P2P_ENGINE_UNAVAILABLE, unavailable);
			}
			TorrentStatus status = handle.status();
			Log.i("P2P bridge ready: trackers=" + trackers.size() + " peers=" +
					status.numPeers() + " seeds=" + status.numSeeds() + " rate=" +
					status.downloadRate() + "B/s dhtNodes=" + manager.dhtNodes());
			TorrentProgressMapper.publish(progress, TorrentProgressMapper.initial(status));
			cacheMaintenance.prepared(storageDirectory);
			return new PreparedTorrent(target.infoHash().toLowerCase(Locale.ROOT) + ':' + fileIndex,
					location, file.getName(), size, file, handle, streamKey);
		} catch (Exception failure) {
			if (handle != null) {
				sessionOwner.unregisterResume(handle);
				if (handle.isValid()) manager.remove(handle);
			}
			cacheMaintenance.failed(storageDirectory, existedBefore);
			throw failure;
		} finally {
			cacheMaintenance.finishPreparation(storageDirectory);
		}
	}

	private static void primeBoundaries(TorrentHandle handle, TorrentInfo info, int fileIndex) {
		int pieceLength = info.pieceLength();
		if (pieceLength <= 0) return;
		long offset = info.files().fileOffset(fileIndex);
		long size = info.files().fileSize(fileIndex);
		int first = Math.max(0, (int) (offset / pieceLength));
		int last = Math.min(info.numPieces() - 1,
				(int) ((offset + Math.max(size - 1, 0)) / pieceLength));
		handle.clearPieceDeadlines();
		int deadline = 0;
		for (int piece = first; piece <= Math.min(last, first + INITIAL_BOUNDARY_PIECES - 1); piece++) {
			handle.piecePriority(piece, Priority.SEVEN);
			handle.setPieceDeadline(piece, deadline);
			deadline += 50;
		}
		for (int piece = Math.max(first, last - INITIAL_BOUNDARY_PIECES + 1); piece <= last; piece++) {
			if (piece < first + INITIAL_BOUNDARY_PIECES) continue;
			handle.piecePriority(piece, Priority.SEVEN);
			handle.setPieceDeadline(piece, deadline);
			deadline += 50;
		}
	}

	private static void logUnavailable(SessionManager manager, int trackerCount,
			TorrentStatus status, String phase) {
		int peers = (status == null) ? 0 : status.numPeers();
		int seeds = (status == null) ? 0 : status.numSeeds();
		int rate = (status == null) ? 0 : status.downloadRate();
		Log.w("P2P startup unavailable: phase=" + phase + " trackers=" + trackerCount +
				" peers=" + peers + " seeds=" + seeds + " rate=" + rate +
				"B/s dht=" + manager.isDhtRunning() + " dhtNodes=" + manager.dhtNodes());
	}

	private void observe(CompletableFuture<PreparedTorrent> future,
			Consumer<RemotePlaybackProgress> progress) {
		if (progress == null) return;
		future.thenAccept(prepared -> server.observe(prepared.streamKey(), progress));
	}

	/** Stops the native torrent and removes its loopback stream when playback changes. */
	public void release(PreparedTorrent prepared) {
		preparations.release(prepared);
	}

	private void releasePrepared(PreparedTorrent prepared) {
		server.cancel(prepared.streamKey());
		sessionOwner.remove(prepared.handle());
		sessionOwner.unregisterResume(prepared.key());
		cacheMaintenance.released(prepared.cacheFile());
	}

	private void requireActive(StremioSourceLease sourceLease,
			TorrentPreparationCoordinator.Cancellation cancellation) {
		cancellation.throwIfCancelled();
		if (closed.get()) throw new IllegalStateException("Stremio torrent runtime is closed");
		if (!sourceLease.isCurrent()) throw new IllegalStateException(
				"Stremio torrent provider changed");
	}

	private static File containedFile(File directory, String relativePath) throws IOException {
		File root = directory.getCanonicalFile();
		File file = new File(root, relativePath).getCanonicalFile();
		if (!file.getPath().startsWith(root.getPath() + File.separator)) {
			throw new IOException("Torrent file escaped the cache directory");
		}
		return file;
	}

	static void writeAtomically(File target, byte[] data) throws IOException {
		TorrentSessionOwner.writeAtomically(target, data);
	}

	private static void ensureDirectory(File directory) throws IOException {
		if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
			throw new IOException("Unable to create Stremio torrent cache");
		}
	}

	private static TorrentHandle awaitHandle(SessionManager manager, TorrentInfo info,
			TorrentAlertRouter alerts, TorrentPreparationCoordinator.Cancellation cancellation)
			throws InterruptedException, IOException {
		long deadline = System.currentTimeMillis() + 10_000L;
		try (TorrentWaiter waiter = new TorrentWaiter(alerts, info.infoHashV1().toHex())) {
			while (true) {
				cancellation.throwIfCancelled();
				TorrentHandle handle = manager.find(info);
				if ((handle != null) && handle.isValid()) return handle;
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0L) break;
				waiter.awaitSignal(Math.min(remaining, 1_000L));
			}
		}
		throw new IOException("Torrent session did not create a playback handle");
	}

	private static int selectFile(TorrentInfo info, Integer requested) {
		if (requested != null) {
			validateRequestedFileIndex(requested, info.numFiles());
			return requested;
		}
		int selected = 0;
		long selectedSize = -1;
		for (int i = 0; i < info.numFiles(); i++) {
			String name = info.files().fileName(i).toLowerCase(Locale.ROOT);
			if (!isVideo(name)) continue;
			long size = info.files().fileSize(i);
			if (size > selectedSize) {
				selected = i;
				selectedSize = size;
			}
		}
		if (selectedSize >= 0) return selected;
		for (int i = 0; i < info.numFiles(); i++) {
			long size = info.files().fileSize(i);
			if (size > selectedSize) {
				selected = i;
				selectedSize = size;
			}
		}
		return selected;
	}

	static void validateRequestedFileIndex(int requested, int fileCount) {
		if (fileCount <= 0 || requested < 0 || requested >= fileCount) {
			throw new IllegalArgumentException("Requested torrent file index is unavailable");
		}
	}

	private static boolean isVideo(String name) {
		for (String extension : VIDEO_EXTENSIONS) if (name.endsWith(extension)) return true;
		return false;
	}

	List<String> allowedTrackers(InfoHashStreamTarget target, NetworkConsent consent) {
		LinkedHashSet<String> trackers = new LinkedHashSet<>();
		for (String source : target.sources()) {
			String tracker = source.startsWith("tracker:") ? source.substring(8) : source;
			if (isAllowedTracker(tracker, consent)) trackers.add(tracker);
		}
		for (String tracker : FALLBACK_TRACKERS) {
			if (isAllowedTracker(tracker, consent)) trackers.add(tracker);
		}
		return List.copyOf(trackers);
	}

	private static String magnet(String infoHash, List<String> trackers) {
		StringBuilder magnet = new StringBuilder("magnet:?xt=urn:btih:")
				.append(infoHash.toLowerCase(Locale.ROOT));
		for (String tracker : trackers) {
			magnet.append("&tr=").append(NetUtils.urlEncode(tracker));
		}
		return magnet.toString();
	}

	boolean isAllowedTracker(String tracker, NetworkConsent consent) {
		try {
			URI uri = URI.create(tracker);
			String scheme = uri.getScheme();
			if ((scheme == null) || (uri.getHost() == null) ||
					(uri.getRawUserInfo() != null) || (uri.getRawFragment() != null)) return false;
			if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
				NetworkPolicy.validate(uri, consent, addressResolver);
				return true;
			}
			if (!scheme.equalsIgnoreCase("udp")) return false;
			int port = uri.getPort();
			if ((port <= 0) || (port > 65_535)) return false;
			// Selecting a P2P stream is the explicit transport action. Public UDP trackers
			// are useful for cold metadata discovery, but LAN and special-use addresses
			// remain behind the source's existing network consent.
			URI validation = new URI("https", null, uri.getHost(), port,
					(uri.getRawPath() == null) ? "/" : uri.getRawPath(), uri.getRawQuery(), null);
			NetworkPolicy.validate(validation,
					new NetworkConsent(true, consent.allowLan()), addressResolver);
			return true;
		} catch (Exception invalid) {
			return false;
		}
	}

	private static String digest(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder digest = new StringBuilder(32);
			for (int i = 0; i < 16; i++) digest.append(String.format(Locale.ROOT, "%02x", bytes[i]));
			return digest.toString();
		} catch (NoSuchAlgorithmException error) {
			throw new AssertionError(error);
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		server.close();
		preparations.close();
		sessionOwner.close();
		cacheMaintenance.close();
	}

	public record PreparedTorrent(String key, URI location, String filename, long size, File cacheFile,
			TorrentHandle handle, String streamKey) {
		public PreparedTorrent {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(location, "location");
			Objects.requireNonNull(filename, "filename");
			Objects.requireNonNull(cacheFile, "cacheFile");
			Objects.requireNonNull(handle, "handle");
			Objects.requireNonNull(streamKey, "streamKey");
			if (size <= 0) throw new IllegalArgumentException("size must be positive");
		}
	}
}
