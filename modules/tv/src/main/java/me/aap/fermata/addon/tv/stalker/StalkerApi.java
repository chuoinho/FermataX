package me.aap.fermata.addon.tv.stalker;

import android.content.Context;

import java.util.List;

import me.aap.fermata.diagnostics.DiagnosticSourceOperation;
import me.aap.utils.async.FutureSupplier;

public final class StalkerApi {
	private final StalkerHttpClient http;
	private final StalkerHealthChecker healthChecker;
	private FutureSupplier<List<StalkerCategory>> categories;
	private FutureSupplier<List<StalkerChannel>> channels;

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

	public void warmUp() {
		getCategories();
	}

	public synchronized void clearCache() {
		categories = null;
		channels = null;
		http.resetSession();
	}

	private synchronized void clearFailedCategories() {
		categories = null;
	}

	private synchronized void clearFailedChannels() {
		channels = null;
	}

	private static <T> FutureSupplier<T> observe(FutureSupplier<T> future, String operation) {
		return DiagnosticSourceOperation.observe(future, "tv_source", operation);
	}
}
