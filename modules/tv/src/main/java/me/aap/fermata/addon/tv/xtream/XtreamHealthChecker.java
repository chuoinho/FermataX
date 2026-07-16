package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import java.io.IOException;
import java.net.HttpURLConnection;

import me.aap.utils.async.FutureSupplier;

final class XtreamHealthChecker {
	private final XtreamAccount account;
	private final XtreamRepository repository;
	private final XtreamHttpClient http;

	XtreamHealthChecker(XtreamAccount account, XtreamRepository repository,
										 XtreamHttpClient http) {
		this.account = account;
		this.repository = repository;
		this.http = http;
	}

	FutureSupplier<XtreamHealth> check() {
		XtreamHealth health = new XtreamHealth(account);
		return repository.validate().then(status -> {
			health.setStatus(status);
			IOException failure = health.accountFailure();
			return (failure != null) ? failed(failure) : checkCategories(health);
		});
	}

	private FutureSupplier<XtreamHealth> checkCategories(XtreamHealth health) {
		return repository.getLiveCategories().then(live -> repository.getVodCategories().then(vod ->
				repository.getSeriesCategories().then(series -> {
					health.setCategoryCounts(live.size(), vod.size(), series.size());
					if (health.getTotalCategories() == 0) return failed(health.noCategoriesFailure());
					return live.isEmpty() ? completed(health) : probeFirstLiveStream(health);
				})));
	}

	private FutureSupplier<XtreamHealth> probeFirstLiveStream(XtreamHealth health) {
		return repository.getFirstLiveStream().then(channel -> {
			if (channel == null) return failed(health.noLiveStreamsFailure());
			health.setTestedStream(channel);
			return http.probe(account.buildLiveStreamUrl(channel.getStreamId()), (status, reason) -> {
				health.setStreamStatusCode(status);
				if ((status == HttpURLConnection.HTTP_OK) ||
						(status == HttpURLConnection.HTTP_PARTIAL)) return health;
				throw health.streamFailure(status, reason);
			});
		});
	}
}
