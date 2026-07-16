package me.aap.fermata.addon.podcast.data;

import static me.aap.utils.async.Completed.failed;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import me.aap.fermata.addon.podcast.download.PodcastDownloadState;
import me.aap.fermata.addon.podcast.download.PodcastDownloadStore;
import me.aap.fermata.addon.podcast.download.PodcastDownloadInfo;

import me.aap.fermata.addon.podcast.feed.PodcastFeedLoader;
import me.aap.fermata.addon.podcast.feed.PodcastFeedProvider;
import me.aap.fermata.addon.podcast.feed.PodcastLoadedFeed;
import me.aap.fermata.addon.podcast.feed.PodcastOpmlCodec;
import me.aap.fermata.addon.podcast.model.PodcastEpisode;
import me.aap.fermata.addon.podcast.model.PodcastFeed;
import me.aap.fermata.addon.podcast.model.PodcastSource;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.addon.podcast.model.PodcastOpmlEntry;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.fermata.addon.podcast.security.PodcastCredential;
import me.aap.fermata.addon.podcast.security.PodcastCredentialStore;
import me.aap.fermata.addon.podcast.security.PodcastUrlRedactor;
import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.app.App;
import static me.aap.utils.async.Completed.completed;
import me.aap.utils.db.SQLite;

public final class PodcastRepository implements Closeable, PodcastDownloadStore {
	private static final long MINUTE_MS = 60_000L;
	private static final long MAX_RETRY_MS = 6 * 60 * MINUTE_MS;
	private static final long[] RETRY_STEPS_MS = {
			MINUTE_MS, 5 * MINUTE_MS, 15 * MINUTE_MS, 60 * MINUTE_MS
	};
	private final SQLite database;
	private final PodcastFeedProvider feeds;
	private final PodcastCredentialStore credentials;
	private final PodcastArtworkCache artworks;
	private final FutureSupplier<Void> initialized;

	public PodcastRepository(Context context) {
		this(SQLite.get(new File(context.getFilesDir(), "podcast/podcast.db")),
				new PodcastFeedLoader(), new PodcastCredentialStore(context),
				new PodcastArtworkCache(new File(context.getCacheDir(), "podcast/artwork"),
						new PodcastHttpClient()));
	}

	PodcastRepository(SQLite database, PodcastFeedProvider feeds,
			PodcastCredentialStore credentials) {
		this(database, feeds, credentials, null);
	}

	PodcastRepository(SQLite database, PodcastFeedProvider feeds,
			PodcastCredentialStore credentials, @Nullable PodcastArtworkCache artworks) {
		this.database = database;
		this.feeds = feeds;
		this.credentials = credentials;
		this.artworks = artworks;
		initialized = database.execute(PodcastDatabase::initialize);
	}

	public FutureSupplier<PodcastSubscription> subscribe(String url) {
		PodcastSource source = PodcastSource.create(url, null, null);
		if (source == null) {
			return failed(new PodcastException(PodcastErrorCode.INVALID_CONTENT,
					"Enter a valid HTTP or HTTPS podcast feed URL"));
		}
		return subscribe(source);
	}

	public FutureSupplier<PodcastSubscription> subscribe(PodcastSource source) {
		if (source.isPrivate() && !credentials.isAvailable()) {
			return failed(new PodcastException(PodcastErrorCode.SECURE_STORAGE_UNAVAILABLE,
					"Secure storage is unavailable for this private podcast"));
		}
		return initialized.thenIgnoreResult(() -> feeds.load(source.toRequest(null, null)))
				.then(loaded -> persist(source, loaded, source.getFeedKey(), null));
	}

	public FutureSupplier<PodcastSubscription> refresh(String feedKey) {
		return getSubscription(feedKey).then(subscription -> {
			if (subscription == null) {
				return failed(new PodcastException(PodcastErrorCode.NOT_FOUND,
						"Podcast subscription was not found"));
			}
			try {
				PodcastSource source = sourceFor(subscription);
				return feeds.load(source.toRequest(subscription.getEtag(),
						subscription.getLastModified())).then(loaded -> {
					if (loaded.isNotModified()) {
						long now = System.currentTimeMillis();
						return database.query(db -> {
							PodcastDatabase.refreshNotModified(db, feedKey, now, loaded.getEtag(),
									loaded.getLastModified());
							return PodcastDatabase.getSubscription(db, feedKey);
						});
					}
					return persist(source, loaded, feedKey, subscription.getCredentialRef());
				}, error -> recordRefreshFailure(subscription, error));
			} catch (Throwable ex) {
				return recordRefreshFailure(subscription, ex);
			}
		});
	}

	public FutureSupplier<List<PodcastSubscription>> listSubscriptions() {
		return initialized.thenIgnoreResult(() -> database.query(
				PodcastDatabase::listSubscriptions));
	}

	public FutureSupplier<PodcastSubscription> getSubscription(String feedKey) {
		return initialized.thenIgnoreResult(() -> database.query(
				db -> PodcastDatabase.getSubscription(db, feedKey)));
	}

	public FutureSupplier<Boolean> deleteSubscription(String feedKey) {
		return initialized.thenIgnoreResult(() -> database.query(db -> {
			Set<String> refs = PodcastDatabase.credentialRefs(db, feedKey);
			boolean deleted = PodcastDatabase.deleteSubscription(db, feedKey) != 0;
			return new DeleteResult(deleted, refs);
		})).map(result -> {
			if (result.deleted) for (String ref : result.credentialRefs) credentials.remove(ref);
			return result.deleted;
		});
	}

	public FutureSupplier<List<PodcastEpisodeRecord>> listEpisodes(String feedKey, int limit,
			int offset) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				PodcastDatabase.listEpisodes(db, feedKey, limit, offset)));
	}

	public FutureSupplier<PodcastEpisodeRecord> getEpisode(String feedKey, String episodeKey) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				PodcastDatabase.getEpisode(db, feedKey, episodeKey)));
	}

	public FutureSupplier<List<PodcastEpisodeRecord>> listContinue(int limit) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				PodcastDatabase.listContinue(db, limit)));
	}

	public FutureSupplier<List<PodcastEpisodeRecord>> listNewEpisodes(int limit) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				PodcastDatabase.listNewEpisodes(db, limit)));
	}

	public PodcastPlaybackSource resolveNetworkPlayback(PodcastEpisodeRecord episode)
			throws Exception {
		return resolve(episode.getMediaUrl(), episode.getMediaCredentialRef());
	}

	@Override
	public FutureSupplier<PodcastDownloadInfo> getDownloadInfo(String feedKey, String episodeKey) {
		return initialized.thenIgnoreResult(() -> database.query(db ->
				PodcastDatabase.getDownloadInfo(db, feedKey, episodeKey)));
	}

	public FutureSupplier<Void> updateProgress(String feedKey, String episodeKey, long positionMs,
			boolean played, long lastPlayedMs) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				PodcastDatabase.updateProgress(db, feedKey, episodeKey, positionMs, played,
						lastPlayedMs)));
	}

	public FutureSupplier<Void> setPlayed(String feedKey, String episodeKey, boolean played) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				PodcastDatabase.setPlayed(db, feedKey, episodeKey, played)));
	}

	public FutureSupplier<Void> updateDownload(String feedKey, String episodeKey, int state,
			@Nullable String localPath, @Nullable String tempPath, long downloaded, long total,
			@Nullable String etag, @Nullable String lastModified, @Nullable String errorCode) {
		return initialized.thenIgnoreResult(() -> database.execute(db -> PodcastDatabase.updateDownload(
				db, feedKey, episodeKey, state, localPath, tempPath, downloaded, total, etag,
				lastModified, errorCode)));
	}

	public FutureSupplier<Void> deleteDownloadState(String feedKey, String episodeKey) {
		return initialized.thenIgnoreResult(() -> database.execute(db ->
				PodcastDatabase.deleteDownload(db, feedKey, episodeKey)));
	}

	public FutureSupplier<String> resolveArtwork(PodcastEpisodeRecord episode) {
		return resolveArtwork(episode.getArtworkUrl(), episode.getArtworkCredentialRef());
	}

	public FutureSupplier<String> resolveArtwork(PodcastSubscription subscription) {
		return resolveArtwork(subscription.getArtworkUrl(),
				subscription.getArtworkCredentialRef());
	}

	private FutureSupplier<String> resolveArtwork(String url, @Nullable String reference) {
		if ((url == null) || url.isEmpty()) return completed("");
		try {
			PodcastPlaybackSource source = resolve(url, reference);
			return (artworks == null) ? completed(source.getUrl()) : artworks.resolve(source);
		} catch (Throwable error) {
			return failed(error);
		}
	}

	private PodcastPlaybackSource resolve(String storedUrl, @Nullable String reference)
			throws Exception {
		if (reference == null) return new PodcastPlaybackSource(storedUrl, null);
		PodcastCredential credential = credentials.load(reference);
		if (credential == null) {
			throw new PodcastException(PodcastErrorCode.AUTH_REQUIRED,
					"Podcast credentials are missing");
		}
		String url = (reference.startsWith("feed:")) ? storedUrl : credential.getFeedUrl();
		return new PodcastPlaybackSource(url, credential.getAuthorization());
	}

	public FutureSupplier<PodcastImportResult> importOpml(InputStream input) {
		FutureSupplier<List<PodcastOpmlEntry>> parsed = App.get().execute(() -> {
			try (InputStream source = input) {
				return new PodcastOpmlCodec().parse(source);
			}
		});
		return parsed.then(entries -> importEntry(entries, 0,
				new PodcastImportResult(entries.size(), 0, 0)));
	}

	public FutureSupplier<Void> exportOpml(OutputStream output, boolean includePrivate) {
		return listSubscriptions().then(subscriptions -> App.get().execute(() -> {
			List<PodcastOpmlEntry> entries = new ArrayList<>(subscriptions.size());
			for (PodcastSubscription subscription : subscriptions) {
				String url = subscription.getCanonicalUrl();
				if (subscription.getCredentialRef() != null) {
					if (!includePrivate) continue;
					PodcastCredential credential = credentials.load(subscription.getCredentialRef());
					if (credential == null) continue;
					url = credential.getFeedUrl();
				}
				entries.add(new PodcastOpmlEntry(subscription.getTitle(), url));
			}
			try (OutputStream destination = output) {
				new PodcastOpmlCodec().write(entries, destination, includePrivate);
			}
			return null;
		}));
	}

	private FutureSupplier<PodcastImportResult> importEntry(List<PodcastOpmlEntry> entries,
			int index, PodcastImportResult result) {
		if (index == entries.size()) return completed(result);
		return subscribe(entries.get(index).feedUrl()).then(
				ignored -> importEntry(entries, index + 1, new PodcastImportResult(result.total(),
						result.imported() + 1, result.failed())),
				error -> importEntry(entries, index + 1, new PodcastImportResult(result.total(),
						result.imported(), result.failed() + 1)));
	}

	private FutureSupplier<PodcastSubscription> persist(PodcastSource source,
			PodcastLoadedFeed loaded, String feedKey, @Nullable String existingCredentialRef) {
		if (loaded.isNotModified() || (loaded.getFeed() == null)) {
			return failed(new PodcastException(PodcastErrorCode.INVALID_CONTENT,
					"Podcast feed did not return any metadata"));
		}
		try {
			PreparedFeed prepared = prepare(source, loaded, feedKey, existingCredentialRef);
			return database.query(db -> {
				PodcastDatabase.upsert(db, prepared.subscription, prepared.episodes,
						prepared.now);
				return PodcastDatabase.getSubscription(db, feedKey);
			});
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	private PreparedFeed prepare(PodcastSource source, PodcastLoadedFeed loaded, String feedKey,
			@Nullable String existingCredentialRef) throws Exception {
		PodcastFeed feed = loaded.getFeed();
		long now = System.currentTimeMillis();
		String feedCredentialRef = existingCredentialRef;
		if ((feedCredentialRef == null) && source.isPrivate()) feedCredentialRef = "feed:" + feedKey;
		if (source.isPrivate()) credentials.save(feedCredentialRef, source.toCredential());

		String finalUrl = loaded.getFinalUrl();
		if ((feedCredentialRef == null) && PodcastUrlRedactor.containsSecrets(finalUrl)) {
			feedCredentialRef = "feed:" + feedKey;
			PodcastCredential credential = source.credentialForUrl(finalUrl);
			if (credential == null) throw invalidUrl();
			credentials.save(feedCredentialRef, credential);
		}

		StoredUrl artwork = storeUrl(feed.getArtworkUrl(), source, feedCredentialRef,
				"artwork:" + feedKey);
		String site = value(PodcastUrlRedactor.forStorage(feed.getWebsiteUrl()));
		String canonical = value(PodcastUrlRedactor.forStorage(finalUrl));
		PodcastSubscription subscription = new PodcastSubscription(feedKey, canonical,
				feedCredentialRef, feed.getTitle(), feed.getAuthor(), feed.getDescription(),
				artwork.url, artwork.credentialRef, site, feed.getLanguage(), feed.isExplicit(),
				loaded.getEtag(), loaded.getLastModified(), now, now, now);

		List<PodcastStoredEpisode> episodes = new ArrayList<>(feed.getEpisodes().size());
		for (PodcastEpisode episode : feed.getEpisodes()) {
			StoredUrl media = storeUrl(episode.getMediaUrl(), source, feedCredentialRef,
					"media:" + feedKey + ':' + episode.getKey());
			StoredUrl episodeArtwork = storeUrl(episode.getArtworkUrl(), source, feedCredentialRef,
					"artwork:" + feedKey + ':' + episode.getKey());
			if (media.url.isEmpty()) continue;
			episodes.add(new PodcastStoredEpisode(episode, media.url, media.credentialRef,
					episodeArtwork.url, episodeArtwork.credentialRef));
		}
		return new PreparedFeed(subscription, episodes, now);
	}

	private PodcastSource sourceFor(PodcastSubscription subscription) throws Exception {
		PodcastCredential credential = credentials.load(subscription.getCredentialRef());
		PodcastSource source = (credential == null) ?
				PodcastSource.create(subscription.getCanonicalUrl(), null, null) :
				PodcastSource.create(credential.getFeedUrl(), credential.getUsername(),
						credential.getPassword());
		if (source == null) throw invalidUrl();
		return source;
	}

	private FutureSupplier<PodcastSubscription> recordRefreshFailure(
			PodcastSubscription subscription,
			Throwable error) {
		String code = (error instanceof PodcastException podcast) ?
				podcast.getCode().name() : PodcastErrorCode.HTTP.name();
		long now = System.currentTimeMillis();
		long retryAfter = (error instanceof PodcastException podcast) ?
				podcast.getRetryAfterMs() : 0;
		long delay = retryDelayMs(subscription.getFailureCount() + 1, retryAfter,
				now ^ subscription.getFeedKey().hashCode());
		return database.execute(db -> PodcastDatabase.refreshFailed(db,
				subscription.getFeedKey(), now, now + delay, code))
				.then(ignored -> failed(error));
	}

	static long retryDelayMs(int failureCount, long retryAfterMs, long entropy) {
		int count = Math.max(failureCount, 1);
		long base;
		if (count <= RETRY_STEPS_MS.length) {
			base = RETRY_STEPS_MS[count - 1];
		} else {
			int shift = Math.min(count - RETRY_STEPS_MS.length, 3);
			base = Math.min(RETRY_STEPS_MS[RETRY_STEPS_MS.length - 1] << shift,
					MAX_RETRY_MS);
		}
		long spread = base / 10;
		long width = (spread * 2) + 1;
		long jittered = base - spread + Math.floorMod(entropy, width);
		return Math.min(Math.max(jittered, Math.max(retryAfterMs, 0)), MAX_RETRY_MS);
	}

	private StoredUrl storeUrl(String url, PodcastSource source,
			@Nullable String feedCredentialRef, String reference) throws Exception {
		if ((url == null) || url.isEmpty()) return new StoredUrl("", null);
		String stored = PodcastUrlRedactor.forStorage(url);
		if (stored == null) throw invalidUrl();
		if (PodcastUrlRedactor.containsSecrets(url)) {
			PodcastCredential credential = source.credentialForUrl(url);
			if (credential == null) throw invalidUrl();
			credentials.save(reference, credential);
			return new StoredUrl(stored, reference);
		}
		return new StoredUrl(stored, source.hasBasicAuth() ? feedCredentialRef : null);
	}

	private static PodcastException invalidUrl() {
		return new PodcastException(PodcastErrorCode.INVALID_CONTENT,
				"Podcast feed contains an invalid media URL");
	}

	private static String value(@Nullable String value) {
		return (value == null) ? "" : value;
	}

	@Override
	public void close() {
		database.close();
	}

	private record StoredUrl(String url, String credentialRef) {}
	private record PreparedFeed(PodcastSubscription subscription,
			List<PodcastStoredEpisode> episodes, long now) {}
	private record DeleteResult(boolean deleted, Set<String> credentialRefs) {}
}
