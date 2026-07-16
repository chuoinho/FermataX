package me.aap.fermata.addon.tv.xtream;

import java.util.List;

import me.aap.utils.async.FutureSupplier;

/** Backward-compatible Xtream facade used by TV media-library items and source validation. */
public class XtreamApi {
	private final XtreamRepository repository;
	private final XtreamHealthChecker healthChecker;

	public XtreamApi(XtreamAccount account) {
		XtreamErrorMapper errors = new XtreamErrorMapper(account);
		XtreamHttpClient http = new XtreamHttpClient(account, errors);
		repository = new XtreamRepository(http, new XtreamJsonStreamParser());
		healthChecker = new XtreamHealthChecker(account, repository, http);
	}

	public FutureSupplier<XtreamStatus> validate() {
		return repository.validate();
	}

	public FutureSupplier<XtreamHealth> healthCheck() {
		return healthChecker.check();
	}

	public FutureSupplier<List<XtreamCategory>> getLiveCategories() {
		return repository.getLiveCategories();
	}

	public FutureSupplier<List<XtreamCategory>> getVodCategories() {
		return repository.getVodCategories();
	}

	public FutureSupplier<List<XtreamCategory>> getSeriesCategories() {
		return repository.getSeriesCategories();
	}

	public FutureSupplier<List<XtreamChannel>> getLiveStreams(String categoryId) {
		return repository.getLiveStreams(categoryId);
	}

	public FutureSupplier<List<XtreamMovie>> getVodStreams(String categoryId) {
		return repository.getVodStreams(categoryId);
	}

	public FutureSupplier<List<XtreamSeries>> getSeries(String categoryId) {
		return repository.getSeries(categoryId);
	}

	public FutureSupplier<List<XtreamSeason>> getSeriesSeasons(int seriesId) {
		return repository.getSeriesSeasons(seriesId);
	}

	public FutureSupplier<List<XtreamEpgProgram>> getEpg(int streamId) {
		return repository.getEpg(streamId);
	}

	public void warmUp() {
		repository.warmUp();
	}

	public void clearCache() {
		repository.clearCache();
	}
}
