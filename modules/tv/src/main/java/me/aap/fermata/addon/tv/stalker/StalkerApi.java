package me.aap.fermata.addon.tv.stalker;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.diagnostics.DiagnosticSourceOperation;
import me.aap.utils.async.FutureSupplier;

public final class StalkerApi {
	private final StalkerHttpClient http;
	private final StalkerHealthChecker healthChecker;
	private FutureSupplier<List<StalkerCategory>> categories;
	private FutureSupplier<List<StalkerChannel>> channels;
	private FutureSupplier<List<StalkerCategory>> vodCategories;
	private FutureSupplier<List<StalkerCategory>> seriesCategories;
	private final Map<String, FutureSupplier<List<StalkerVod>>> vod = new HashMap<>();
	private final Map<String, FutureSupplier<List<StalkerSeries>>> series = new HashMap<>();
	private final Map<String, FutureSupplier<List<StalkerSeason>>> seasons = new HashMap<>();
	private final Map<String, FutureSupplier<List<StalkerEpgProgram>>> epg = new HashMap<>();

	public StalkerApi(StalkerAccount account, Context context) {
		http = new StalkerHttpClient(account, context);
		healthChecker = new StalkerHealthChecker(account, http, context);
	}

	public FutureSupplier<Void> validate() {
		return observe(http.validate(), "stalker_validate");
	}

	public FutureSupplier<StalkerHealth> healthCheck() {
		return observe(healthChecker.check(), "stalker_health");
	}

	public synchronized FutureSupplier<List<StalkerCategory>> getCategories() {
		if (categories == null) {
			categories = observe(http.getCategories(), "stalker_categories")
					.onFailure(error -> clearFailedCategories());
		}
		return categories;
	}

	public synchronized FutureSupplier<List<StalkerChannel>> getChannels() {
		if (channels == null) {
			channels = observe(http.getChannels(), "stalker_channels")
					.onFailure(error -> clearFailedChannels());
		}
		return channels;
	}

	public FutureSupplier<StalkerPlaybackLink> createLink(String command) {
		return observe(http.createLink(command), "stalker_create_link");
	}

	public synchronized FutureSupplier<List<StalkerCategory>> getVodCategories() {
		if (vodCategories == null) {
			vodCategories = observe(http.getVodCategories(), "stalker_vod_categories")
					.onFailure(error -> clearFailedVodCategories());
		}
		return vodCategories;
	}

	public synchronized FutureSupplier<List<StalkerCategory>> getSeriesCategories() {
		if (seriesCategories == null) {
			seriesCategories = observe(http.getSeriesCategories(), "stalker_series_categories")
					.onFailure(error -> clearFailedSeriesCategories());
		}
		return seriesCategories;
	}

	public synchronized FutureSupplier<List<StalkerVod>> getVod(String categoryId) {
		FutureSupplier<List<StalkerVod>> future = vod.get(categoryId);
		if (future == null) {
			future = observe(http.getVod(categoryId), "stalker_vod")
					.onFailure(error -> clearFailed(vod, categoryId));
			vod.put(categoryId, future);
		}
		return future;
	}

	public synchronized FutureSupplier<List<StalkerSeries>> getSeries(String categoryId) {
		FutureSupplier<List<StalkerSeries>> future = series.get(categoryId);
		if (future == null) {
			future = observe(http.getSeries(categoryId), "stalker_series")
					.onFailure(error -> clearFailed(series, categoryId));
			series.put(categoryId, future);
		}
		return future;
	}

	public synchronized FutureSupplier<List<StalkerSeason>> getSeasons(String seriesId) {
		FutureSupplier<List<StalkerSeason>> future = seasons.get(seriesId);
		if (future == null) {
			future = observe(http.getSeasons(seriesId), "stalker_seasons")
					.onFailure(error -> clearFailed(seasons, seriesId));
			seasons.put(seriesId, future);
		}
		return future;
	}

	public synchronized FutureSupplier<List<StalkerEpgProgram>> getEpg(String channelId) {
		FutureSupplier<List<StalkerEpgProgram>> future = epg.get(channelId);
		if (future == null) {
			future = observe(http.getEpg(channelId), "stalker_epg")
					.onFailure(error -> clearFailed(epg, channelId));
			epg.put(channelId, future);
		}
		return future;
	}

	public FutureSupplier<StalkerPlaybackLink> createVodLink(String command, String series) {
		return observe(http.createVodLink(command, series), "stalker_vod_link");
	}

	public FutureSupplier<StalkerPlaybackLink> createArchiveLink(String programId) {
		return observe(http.createArchiveLink(programId), "stalker_archive_link");
	}

	public void warmUp() {
		getCategories();
	}

	public synchronized void clearCache() {
		categories = null;
		channels = null;
		vodCategories = null;
		seriesCategories = null;
		vod.clear();
		series.clear();
		seasons.clear();
		epg.clear();
		http.resetSession();
	}

	private synchronized void clearFailedCategories() {
		categories = null;
	}

	private synchronized void clearFailedChannels() {
		channels = null;
	}

	private synchronized void clearFailedVodCategories() {
		vodCategories = null;
	}

	private synchronized void clearFailedSeriesCategories() {
		seriesCategories = null;
	}

	private synchronized <T> void clearFailed(Map<String, FutureSupplier<List<T>>> cache,
			String key) {
		cache.remove(key);
	}

	private static <T> FutureSupplier<T> observe(FutureSupplier<T> future, String operation) {
		return DiagnosticSourceOperation.observe(future, "tv_source", operation);
	}
}
