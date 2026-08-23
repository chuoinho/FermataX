package me.aap.fermata.vfs.m3u;

import static me.aap.fermata.util.Utils.createUserSourceDownloader;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.net.http.HttpFileDownloader.Status;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class M3uFileSystem implements VirtualFileSystem {
	public static final String SCHEME_M3U = "m3u";
	private static final M3uFileSystem fs = new M3uFileSystem();

	public static M3uFileSystem getInstance() {
		return fs;
	}

	@NonNull
	@Override
	public Provider getProvider() {
		return Provider.getInstance();
	}

	@NonNull
	@Override
	public FutureSupplier<? extends M3uFile> getResource(Rid rid) {
		return load(createM3uFile(rid));
	}

	public String getScheme() {
		return SCHEME_M3U;
	}

	public Rid toRid(String id) {
		return Rid.create(getScheme(), null, id, -1, null);
	}

	public String toId(Rid rid) {
		return rid.getHost();
	}

	protected synchronized M3uFile newFile() {
		String name = getScheme();
		SharedPreferences prefs = App.get().getSharedPreferences(name, Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = prefs.edit();
		int id = prefs.getInt("ID_COUNTER", 0) + 1;
		Rid rid = Rid.create(name, null, String.valueOf(id), -1, null);
		edit.putInt("ID_COUNTER", id);
		edit.apply();
		return createM3uFile(rid);
	}

	protected M3uFile createM3uFile(Rid rid) {
		return new M3uFile(rid);
	}

	protected FutureSupplier<M3uFile> load(M3uFile file) {
		Promise<M3uFile> p = new Promise<>();
		String url = file.getUrl();

		if (url == null) {
			Log.d("Not an m3u file: ", file);
			return completedNull();
		}

		if (url.startsWith("/") || url.startsWith("content://")) {
			p.complete(file);
			return p;
		}

		fetch(file, null).onCompletion((result, err) -> {
			if (err == null) {
				p.complete(file);
			} else {
				p.completeExceptionally(err);
			}
		});

		return p;
	}

	public FutureSupplier<M3uFetchResult> refresh(M3uFile file, M3uRefreshMode mode) {
		String url = file.getUrl();
		if ((url == null) || url.startsWith("/") || url.startsWith("content://"))
			return completedNull();
		return refresh(file, mode, Collections.singletonList(url));
	}

	public FutureSupplier<M3uFetchResult> refresh(M3uFile file, M3uRefreshMode mode,
			List<String> candidates) {
		if ((candidates == null) || candidates.isEmpty())
			return failed(new IllegalArgumentException("No M3U fetch candidates"));
		return refresh(file, mode, candidates, 0);
	}

	private FutureSupplier<M3uFetchResult> refresh(M3uFile file, M3uRefreshMode mode,
			List<String> candidates, int index) {
		boolean hasNext = (index + 1) < candidates.size();
		return fetch(file, mode, candidates.get(index)).then(value ->
				(hasNext && (value.state() == M3uFetchResult.State.STALE_FALLBACK)) ?
						refresh(file, mode, candidates, index + 1) : completed(value),
				error -> (hasNext && !isCancellation(error)) ?
						refresh(file, mode, candidates, index + 1) : failed(error));
	}

	private FutureSupplier<M3uFetchResult> fetch(M3uFile file, M3uRefreshMode mode) {
		return fetch(file, mode, file.getUrl());
	}

	private FutureSupplier<M3uFetchResult> fetch(M3uFile file, M3uRefreshMode mode, String url) {
		File cacheFile = file.getLocalFile();
		boolean existed = cacheFile.isFile();
		boolean fresh = existed && isFresh(file);
		Context ctx = App.get();
		HttpFileDownloader downloader = createUserSourceDownloader(ctx, url);
		downloader.setReturnExistingOnFail(true);
		downloader.setForceRevalidate((mode == M3uRefreshMode.MANUAL) ||
				(mode == M3uRefreshMode.EDIT));
		downloader.setContentValidator(new M3uContentValidator());
		return downloader.download(url, cacheFile, file.getPrefs()).map(status ->
				toFetchResult(status, mode, fresh));
	}

	private static boolean isFresh(M3uFile file) {
		long timestamp = file.getPrefs().getLongPref(HttpFileDownloader.TIMESTAMP);
		int maxAge = file.getPrefs().getIntPref(HttpFileDownloader.MAX_AGE);
		return (timestamp > 0L) && (maxAge > 0) &&
				((timestamp + maxAge * 1000L) > System.currentTimeMillis());
	}

	private static M3uFetchResult toFetchResult(Status status, M3uRefreshMode mode,
			boolean wasFresh) {
		Throwable failure = status.getFailure();
		if (failure != null)
			return new M3uFetchResult(M3uFetchResult.State.STALE_FALLBACK, status, failure);
		if (status.bytesDownloaded() > 0L)
			return new M3uFetchResult(M3uFetchResult.State.UPDATED, status, null);
		M3uFetchResult.State state = ((mode == null) ||
				((mode == M3uRefreshMode.AUTO) && wasFresh)) ?
				M3uFetchResult.State.FRESH_CACHE : M3uFetchResult.State.NOT_MODIFIED;
		return new M3uFetchResult(state, status, null);
	}

	public static final class Provider implements VirtualFileSystem.Provider {
		private static final Provider instance = new Provider();

		public static Provider getInstance() {
			return instance;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return Collections.singleton(SCHEME_M3U);
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(fs);
		}
	}
}
