package me.aap.fermata.addon.tv.xtream;

import android.content.Context;

import java.util.List;
import java.util.Map;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.fermata.diagnostics.DiagnosticSourceOperation;

/** Backward-compatible Xtream facade used by TV media-library items and source validation. */
public class XtreamApi {
	private final XtreamRepository repository;
	private final XtreamHealthChecker healthChecker;

	public XtreamApi(XtreamAccount account) {
		this(account, null);
	}

	public XtreamApi(XtreamAccount account, Context context) {
		XtreamErrorMapper errors = new XtreamErrorMapper(account, context);
		XtreamHttpClient http = new XtreamHttpClient(account, errors);
		repository = new XtreamRepository(http, new XtreamJsonStreamParser());
		healthChecker = new XtreamHealthChecker(account, repository, http);
	}

	public FutureSupplier<XtreamStatus> validate() {
		return observe(repository.validate(), "validate");
	}

	public FutureSupplier<XtreamHealth> healthCheck() {
		return observe(healthChecker.check(), "health");
	}

	public FutureSupplier<List<XtreamCategory>> getLiveCategories() {
		return observe(repository.getLiveCategories(), "live_categories");
	}

	public FutureSupplier<List<XtreamCategory>> getVodCategories() {
		return observe(repository.getVodCategories(), "vod_categories");
	}

	public FutureSupplier<List<XtreamCategory>> getSeriesCategories() {
		return observe(repository.getSeriesCategories(), "series_categories");
	}

	public FutureSupplier<List<XtreamChannel>> getLiveStreams(String categoryId) {
		return observe(repository.getLiveStreams(categoryId), "live_streams");
	}

	public FutureSupplier<List<XtreamMovie>> getVodStreams(String categoryId) {
		return observe(repository.getVodStreams(categoryId), "vod_streams");
	}

	public FutureSupplier<List<XtreamSeries>> getSeries(String categoryId) {
		return observe(repository.getSeries(categoryId), "series");
	}

	public FutureSupplier<List<XtreamSeason>> getSeriesSeasons(int seriesId) {
		return observe(repository.getSeriesSeasons(seriesId), "seasons");
	}

	public FutureSupplier<List<XtreamEpgProgram>> getEpg(int streamId) {
		return observe(repository.getEpg(streamId), "epg");
	}

	private static <T> FutureSupplier<T> observe(FutureSupplier<T> future, String requestType) {
		return DiagnosticSourceOperation.observe(future, "tv_source", requestType);
	}

	public void warmUp() {
		repository.warmUp();
	}

	public void clearCache() {
		repository.clearCache();
	}
}
