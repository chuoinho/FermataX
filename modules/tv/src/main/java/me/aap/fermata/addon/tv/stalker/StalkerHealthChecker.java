package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.content.Context;

import java.io.IOException;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.utils.async.FutureSupplier;

final class StalkerHealthChecker {
	private static final int MAX_STREAM_PROBES = 3;
	private final StalkerAccount account;
	private final StalkerHttpClient http;
	private final Context context;

	StalkerHealthChecker(StalkerAccount account, StalkerHttpClient http, Context context) {
		this.account = account;
		this.http = http;
		this.context = context;
	}

	FutureSupplier<StalkerHealth> check() {
		StalkerHealth health = new StalkerHealth();
		long discoveryStarted = System.nanoTime();
		if (account.getEndpointCandidates().isEmpty()) {
			health.record(StalkerHealth.Stage.DISCOVER_ENDPOINT, false,
					duration(discoveryStarted), 0);
			return failed(new IOException(text(R.string.stalker_error_incomplete_account,
					"Enter a valid portal URL and MAC address.")));
		}
		health.record(StalkerHealth.Stage.DISCOVER_ENDPOINT, true,
				duration(discoveryStarted), 0);

		return authenticate(health).then(ignore ->
				stage(health, StalkerHealth.Stage.CATEGORIES, http.getCategories()).then(categories -> {
					if (categories.isEmpty()) return failed(new IOException(text(
							R.string.stalker_error_no_categories,
							"The Stalker portal returned no channel categories.")));
					return stage(health, StalkerHealth.Stage.CHANNELS, http.getChannels()).then(channels -> {
						if (channels.isEmpty()) return failed(new IOException(text(
								R.string.stalker_error_no_channels,
								"The Stalker portal returned no playable channels.")));
						health.setCatalogCounts(categories.size(), channels.size());
						return probeNext(health, channels, 0, 0);
					});
				}));
	}

	private FutureSupplier<Void> authenticate(StalkerHealth health) {
		long started = System.nanoTime();
		return http.authenticate().onCompletion((done, failure) -> {
			boolean success = failure == null;
			long elapsed = duration(started);
			int status = http.getLastStatusCode();
			health.record(StalkerHealth.Stage.HANDSHAKE, success, elapsed, status);
			health.record(StalkerHealth.Stage.PROFILE, success, elapsed, status);
		});
	}

	private FutureSupplier<StalkerHealth> probeNext(StalkerHealth health,
			List<StalkerChannel> channels, int index, int attempts) {
		if ((index >= channels.size()) || (attempts >= MAX_STREAM_PROBES)) {
			health.completeDegraded(warning());
			return completed(health);
		}

		StalkerChannel channel = channels.get(index);
		if (isNullOrBlank(channel.command())) {
			return probeNext(health, channels, index + 1, attempts);
		}

		health.incrementStreamAttempts();
		return stage(health, StalkerHealth.Stage.CREATE_LINK,
				http.createLink(channel.command())).then(link ->
				stage(health, StalkerHealth.Stage.STREAM_PROBE, http.probe(link)).then(probe -> {
					health.completePass(channel, probe.statusCode());
					return completed(health);
				}, failure -> {
					health.recordStreamFailureStatus(http.getLastStatusCode());
					return probeNext(health, channels, index + 1, attempts + 1);
				}), failure -> {
			health.recordStreamFailureStatus(http.getLastStatusCode());
			return probeNext(health, channels, index + 1, attempts + 1);
		});
	}

	private <T> FutureSupplier<T> stage(StalkerHealth health, StalkerHealth.Stage stage,
			FutureSupplier<T> future) {
		long started = System.nanoTime();
		return future.onCompletion((result, failure) -> health.record(stage, failure == null,
				duration(started), http.getLastStatusCode()));
	}

	private String warning() {
		return text(R.string.stalker_error_no_working_stream,
				"The portal and catalog work, but no sampled stream could be opened.");
	}

	private String text(int resource, String fallback) {
		return (context == null) ? fallback : context.getString(resource);
	}

	private static long duration(long started) {
		return (System.nanoTime() - started) / 1_000_000L;
	}
}
